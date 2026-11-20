package com.example.clandestinely.alerts;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.clandestinely.MainActivity;
import com.example.clandestinely.R;
import com.example.clandestinely.data.model.Schema;

/** Shows a weather alert. Used by AlertCheckWorker (on-device check) and AlertMessagingService (FCM). */
public class AlertNotifier {

    private static final String TAG = "AlertNotifier";
    public static final String CHANNEL_ID = "weather_alerts";
    private static final int NOTIFICATION_ID = 2001;   // same ID, so a push replaces a worker alert

    private AlertNotifier() {}

    /** Returns false if notifications are off or not allowed. */
    public static boolean show(Context context, String state, int lead) {

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
            case Schema.STATE_THUNDERSTORM: return context.getString(R.string.alert_thunderstorm, lead);
            case Schema.STATE_RAIN:         return context.getString(R.string.alert_rain, lead);
            default:                        return context.getString(summaryFor(state));
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

