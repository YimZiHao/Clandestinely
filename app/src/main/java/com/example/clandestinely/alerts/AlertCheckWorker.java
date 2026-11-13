package com.example.clandestinely.alerts;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.clandestinely.MainActivity;
import com.example.clandestinely.R;
import com.example.clandestinely.data.model.AirQualityResponse;
import com.example.clandestinely.data.model.AlertPrefs;
import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.model.ForecastResponse;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.model.UserProfile;
import com.example.clandestinely.data.remote.ApiClient;
import com.example.clandestinely.data.repository.UserRepository;
import com.example.clandestinely.data.repository.WeatherRepository;
import com.example.clandestinely.data.weather.WeatherClassifier;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * M2 (FR2.1–2.3): runs in the background about every 15 minutes. Fetches the forecast,
 * classifies it with WeatherClassifier (existing thresholds only) and posts one notification
 * for the most severe state the user wants to hear about.
 *
 * - Rain/thunderstorm: any hour from now up to rainLeadMinutes ahead (hourly data).
 * - Heat/haze: current conditions.
 * - Quiet hours: no notification (it comes after quiet hours if the state is still active).
 * - Repeats: the same state isn't notified twice in a row; it resets when the weather is normal.
 */
public class AlertCheckWorker extends Worker {

    private static final String TAG = "AlertCheckWorker";
    public static final String UNIQUE_NAME = "alert_check";
    public static final String CHANNEL_ID = "weather_alerts";
    private static final int NOTIFICATION_ID = 2001;
    private static final long REPEAT_MINUTES = 15;            // WorkManager's minimum

    private static final String PREFS = "alert_check";
    private static final String KEY_LAST_STATE = "last_state";

    public AlertCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Call after sign-in. KEEP = if it's already scheduled, leave it alone. */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(AlertCheckWorker.class, REPEAT_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "Not signed in; skipping.");
            return Result.success();
        }
        AlertPrefs prefs = loadPrefs(user.getUid());
        int lead = prefs.getRainLeadMinutes();

        // 1. Fetch (synchronous is fine: doWork() already runs in the background).
        ForecastResponse forecast;
        int usAqi = WeatherRepository.AQI_UNAVAILABLE;
        try {
            int hoursNeeded = (59 + lead) / 60 + 1;           // current hour + enough to cover `lead`
            Response<ForecastResponse> response = ApiClient.getWeatherApi().getForecast(
                    WeatherRepository.LAT, WeatherRepository.LON,
                    "apparent_temperature,weather_code",
                    "precipitation_probability,weather_code",
                    null,                                     // no daily data needed
                    WeatherRepository.TIMEZONE, hoursNeeded, 0).execute();
            forecast = response.body();
            if (!response.isSuccessful() || forecast == null
                    || forecast.current == null || forecast.hourly == null) {
                Log.e(TAG, "Bad forecast response: HTTP " + response.code());
                return Result.retry();
            }
            usAqi = fetchAqi();
        } catch (IOException e) {
            Log.e(TAG, "No network; will retry.", e);
            return Result.retry();
        }

        // 2. Which states are active, limited to the alert types the user switched on.
        List<String> states = new ArrayList<>();
        CurrentWeather now = new CurrentWeather(0, forecast.current.apparent_temperature, 0, 0,
                usAqi, forecast.current.weather_code, 0);
        for (String s : WeatherClassifier.classify(now)) {
            if (wanted(s, prefs)) states.add(s);
        }
        int minute = nowInCampusTime().get(Calendar.MINUTE);
        ForecastResponse.Hourly h = forecast.hourly;
        for (int i = 0; h.time != null && i < h.time.size(); i++) {
            if (i * 60 >= minute + lead) break;               // this hour starts after the window
            Integer rainChance = h.precipitation_probability != null ? h.precipitation_probability.get(i) : null;
            Integer code = h.weather_code != null ? h.weather_code.get(i) : null;
            if (rainChance == null || code == null) continue;
            CurrentWeather hour = new CurrentWeather(0, 0, 0, rainChance,
                    WeatherRepository.AQI_UNAVAILABLE, code, 0);
            for (String s : WeatherClassifier.classify(hour)) {
                if (wanted(s, prefs) && !states.contains(s)) states.add(s);
            }
        }
        String state = states.isEmpty() ? Schema.STATE_NORMAL : WeatherClassifier.mostSevere(states);

        // 3. Decide whether to notify.
        SharedPreferences store = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String last = store.getString(KEY_LAST_STATE, Schema.STATE_NORMAL);
        if (state.equals(Schema.STATE_NORMAL)) {
            store.edit().putString(KEY_LAST_STATE, Schema.STATE_NORMAL).apply();
            return Result.success();
        }
        if (state.equals(last)) {
            Log.d(TAG, "Already notified about " + state);
            return Result.success();
        }
        if (inQuietHours(prefs)) {
            Log.d(TAG, "Quiet hours; not notifying about " + state);
            return Result.success();                          // not saved, so it notifies later
        }
        if (notify(state, lead)) {
            store.edit().putString(KEY_LAST_STATE, state).apply();
        }
        return Result.success();
    }

    /** Current AQI, or AQI_UNAVAILABLE if the air quality call fails (haze is then skipped). */
    private int fetchAqi() {
        try {
            Response<AirQualityResponse> response = ApiClient.getHazeApi().getAirQuality(
                    WeatherRepository.LAT, WeatherRepository.LON, "us_aqi", null,
                    WeatherRepository.TIMEZONE, 1).execute();
            AirQualityResponse body = response.body();
            if (response.isSuccessful() && body != null && body.current != null) {
                return body.current.us_aqi;
            }
            Log.e(TAG, "Bad AQI response: HTTP " + response.code());
        } catch (IOException e) {
            Log.e(TAG, "AQI request failed; haze not checked.", e);
        }
        return WeatherRepository.AQI_UNAVAILABLE;
    }

    /** The user's alert preferences from Firestore, or the defaults if they can't be loaded. */
    private static AlertPrefs loadPrefs(String uid) {
        try {
            UserProfile profile = Tasks.await(new UserRepository().getProfile(uid), 10, TimeUnit.SECONDS);
            if (profile != null && profile.getAlertPrefs() != null) return profile.getAlertPrefs();
        } catch (Exception e) {
            Log.w(TAG, "Couldn't load alert preferences; using defaults.", e);
        }
        return new AlertPrefs();                              // all alerts on, 60 min, no quiet hours
    }

    /** Rain toggle also covers thunderstorms (see AlertPrefs). */
    private static boolean wanted(String state, AlertPrefs prefs) {
        switch (state) {
            case Schema.STATE_RAIN:
            case Schema.STATE_THUNDERSTORM:   return prefs.getRain();
            case Schema.STATE_HEAT:           return prefs.getHeat();
            case Schema.STATE_HAZE_SENSITIVE:
            case Schema.STATE_HAZE_UNHEALTHY: return prefs.getHaze();
            default:                          return false;
        }
    }

    /** True inside quietStart–quietEnd ("HH:mm"), including ranges past midnight like 23:00–07:00. */
    private static boolean inQuietHours(AlertPrefs prefs) {
        int start = toMinutes(prefs.getQuietStart());
        int end = toMinutes(prefs.getQuietEnd());
        if (start < 0 || end < 0 || start == end) return false;
        Calendar now = nowInCampusTime();
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return start < end
                ? current >= start && current < end           // e.g. 13:00–15:00
                : current >= start || current < end;          // e.g. 23:00–07:00
    }

    /** "23:30" → 1410, or -1 if missing or not "HH:mm". */
    private static int toMinutes(String hhmm) {
        if (hhmm == null) return -1;
        String[] parts = hhmm.split(":");
        if (parts.length != 2) return -1;
        try {
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Calendar nowInCampusTime() {
        return Calendar.getInstance(TimeZone.getTimeZone(WeatherRepository.TIMEZONE));
    }

    /** Posts the notification. False if notifications are off or not allowed. */
    private boolean notify(String state, int lead) {
        Context context = getApplicationContext();
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) {
            Log.d(TAG, "Notifications are off; not posting " + state);
            return false;
        }
        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_forecast)
                .setContentTitle(context.getString(R.string.alert_title))
                .setContentText(textFor(context, state, lead))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Notified: " + state);
            return true;
        } catch (SecurityException e) {                       // POST_NOTIFICATIONS denied (Android 13+)
            Log.w(TAG, "Notification permission missing.", e);
            return false;
        }
    }

    /** Rain/thunderstorm say when; heat and haze reuse the Home summary wording. */
    private static String textFor(Context context, String state, int lead) {
        switch (state) {
            case Schema.STATE_THUNDERSTORM:   return context.getString(R.string.alert_thunderstorm, lead);
            case Schema.STATE_RAIN:           return context.getString(R.string.alert_rain, lead);
            default:                          return context.getString(summaryFor(state));
        }
    }

    @StringRes
    private static int summaryFor(String state) {
        switch (state) {
            case Schema.STATE_HEAT:           return R.string.summary_heat;
            case Schema.STATE_HAZE_SENSITIVE: return R.string.summary_haze_sensitive;
            case Schema.STATE_HAZE_UNHEALTHY: return R.string.summary_haze_unhealthy;
            default:                          return R.string.summary_normal;
        }
    }
}
