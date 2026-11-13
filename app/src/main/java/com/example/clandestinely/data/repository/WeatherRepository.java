package com.example.clandestinely.data.repository;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.clandestinely.data.local.WeatherCache;
import com.example.clandestinely.data.model.AirQualityResponse;
import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.model.DailyWeather;
import com.example.clandestinely.data.model.ForecastResponse;
import com.example.clandestinely.data.model.HourlyWeather;
import com.example.clandestinely.data.remote.OpenMeteoAirQualityApi;
import com.example.clandestinely.data.remote.OpenMeteoApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeatherRepository {

    private final OpenMeteoApi weatherApi;
    private final OpenMeteoAirQualityApi hazeApi;
    // Public so the M2 AlertCheckWorker checks the same place.
    public static final double LAT = 3.1209;
    public static final double LON = 101.6538;
    public static final String TIMEZONE = "Asia/Kuala_Lumpur";
    private static final String CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code";
    private static final String AQI_FIELDS = "us_aqi";
    private static final String AQI_HOURLY_FIELDS = "us_aqi";
    public static final int AQI_UNAVAILABLE = -1;
    private static final String HOURLY_FIELDS =
            "precipitation_probability,temperature_2m,apparent_temperature,weather_code,"
            + "relative_humidity_2m";
    // Current hour + the next 24 hours (Open-Meteo counts the current hour as the first).
    private static final int FORECAST_HOURS = 25;
    // Forecast row: how many hours before now to show (sent as past_hours). 0 = start at now.
    public static final int COLUMN_SPAN = 0;
    private final WeatherCache cache;
    private final MutableLiveData<CurrentWeather> weather = new MutableLiveData<>();
    private final MutableLiveData<List<HourlyWeather>> hourly = new MutableLiveData<>();
    private static final String DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min,"
            + "apparent_temperature_max,precipitation_probability_max,"
            + "temperature_2m_mean,apparent_temperature_mean,relative_humidity_2m_mean";
    private static final int FORECAST_DAYS = 7;
    private final MutableLiveData<List<DailyWeather>> daily = new MutableLiveData<>();

    public WeatherRepository(OpenMeteoApi weatherApi, OpenMeteoAirQualityApi hazeApi, WeatherCache cache) {
        this.weatherApi = weatherApi;
        this.hazeApi = hazeApi;
        this.cache = cache;
    }

    public LiveData<CurrentWeather> getWeather() {
        return weather;
    }

    /** The forecast row: the current hour first, then the next 24 hours. */
    public LiveData<List<HourlyWeather>> getHourly() {
        return hourly;
    }

    /** The daily forecast row: today and the next 6 days. */
    public LiveData<List<DailyWeather>> getDaily() {
        return daily;
    }

    /***
     * Calls both OpenMeteoApi and OpenMeteoAirQualityApi
     */
    public void refresh() {
        CurrentWeather currentWeather = cache.load();
        if (currentWeather != null) {
            weather.setValue(currentWeather); //NFR1 Instant Display
        }
        // call Open Meteo API
        weatherApi.getForecast(LAT, LON, CURRENT_FIELDS, HOURLY_FIELDS, DAILY_FIELDS, TIMEZONE, FORECAST_HOURS, COLUMN_SPAN)
                .enqueue(new Callback<ForecastResponse>() {
                    @Override
                    public void onResponse(Call<ForecastResponse> call,
                                           Response<ForecastResponse> response) {
                        // runs when the server replies
                        ForecastResponse forecast = response.body();

                        // 400 server error
                        if (!response.isSuccessful() || forecast == null
                                || forecast.current == null || forecast.hourly == null) {
                            Log.e("WeatherRepository", "Bad response: HTTP " + response.code());
                            return; // keep showing cached weather
                        }

                        // 200 OK
                        double temperature = forecast.current.temperature_2m;
                        double apparentTemperature = forecast.current.apparent_temperature;
                        int humidity = forecast.current.relative_humidity_2m;
                        List<String> time = forecast.hourly.time;
                        List<Integer> precipitationProbability = forecast.hourly.precipitation_probability;
                        String currentHour = forecast.current.time.substring(0, 13);
                        int weatherCode = forecast.current.weather_code;

                        int rainChance = 0;                       // fallback if no match
                        int currentIndex = -1;
                        for (int i = 0; i < time.size(); i++) {
                            if (time.get(i).substring(0, 13).equals(currentHour)) {
                                rainChance = precipitationProbability.get(i);
                                currentIndex = i;
                                break;                            // found it, stop looping
                            }
                        }

                        // Show the rows now; AQI is filled in when the air quality reply arrives.
                        String today = forecast.current.time.substring(0, 10);
                        publishHourly(forecast.hourly, currentIndex, null);
                        publishDaily(forecast.daily, today, null);

                        // call Open Meteo Air Quality API
                        int finalRainChance = rainChance;
                        int finalCurrentIndex = currentIndex;
                        hazeApi.getAirQuality(LAT, LON, AQI_FIELDS, AQI_HOURLY_FIELDS, TIMEZONE, FORECAST_DAYS)
                                .enqueue(new Callback<AirQualityResponse>() {
                                    @Override
                                    public void onResponse(Call<AirQualityResponse> call, Response<AirQualityResponse> response) {

                                        // runs when the server replies
                                         AirQualityResponse airQualityResponse = response.body();

                                        // 400 server error
                                        if (!response.isSuccessful() || airQualityResponse == null
                                                || airQualityResponse.current == null) {
                                            Log.e("WeatherRepository", "Bad AQI response: HTTP " + response.code());
                                            // weather is still good, so show it without fresh AQI
                                            publish(temperature, apparentTemperature, humidity, finalRainChance, fallbackAqi(), weatherCode);
                                            return;
                                        }

                                        // 200 OK
                                        int usAqi = airQualityResponse.current.us_aqi;
                                        publish(temperature, apparentTemperature, humidity, finalRainChance, usAqi, weatherCode);

                                        // Re-publish the rows with each hour's / day's AQI.
                                        Map<String, Integer> aqiByHour = toAqiMap(airQualityResponse.hourly);
                                        if (aqiByHour != null) {
                                            publishHourly(forecast.hourly, finalCurrentIndex, aqiByHour);
                                            publishDaily(forecast.daily, today, aqiByHour);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<AirQualityResponse> call, Throwable t) {
                                        // runs when there's no network / request failed
                                        Log.e("WeatherRepository", "Open Meteo Air Quality API request failed. Using fallback AQI.", t);
                                        publish(temperature, apparentTemperature, humidity, finalRainChance, fallbackAqi(), weatherCode);
                                    }
                                });


                    }

                    @Override
                    public void onFailure(Call<ForecastResponse> call, Throwable t) {
                        // runs when there's no network / request failed
                        Log.e("WeatherRepository", "Open Meteo API Forecast request failed. Keeping cached weather.", t);
                    }
                });
    }

    /***
     * Builds the CurrentWeather, saves it to the cache and pushes it to the UI.
     *
     * @param temperature
     * @param apparentTemperature
     * @param humidity
     * @param rainChance
     * @param usAqi
     */
    private void publish(double temperature, double apparentTemperature, int humidity,
                         int rainChance, int usAqi, int weatherCode) {
        CurrentWeather current = new CurrentWeather(temperature, apparentTemperature, humidity,
                rainChance, usAqi, weatherCode, System.currentTimeMillis());
        cache.save(current);
        weather.setValue(current);
    }

    /**
     * Cuts the hours from the current one onwards out of the response and pushes them to the UI.
     * aqiByHour: hourly AQI keyed by time, or null if not fetched yet (AQI then unavailable).
     */
    private void publishHourly(ForecastResponse.Hourly h, int currentIndex,
                               @Nullable Map<String, Integer> aqiByHour) {
        if (currentIndex < 0 || h.temperature_2m == null || h.apparent_temperature == null
                || h.weather_code == null || h.relative_humidity_2m == null) {
            Log.e("WeatherRepository", "Hourly data missing; forecast row not updated.");
            return;
        }
        int from = Math.max(0, currentIndex - COLUMN_SPAN);
        int to = h.time.size() - 1;                     // every hour up to the end of the forecast

        List<HourlyWeather> hours = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            hours.add(new HourlyWeather(
                    h.time.get(i),
                    h.temperature_2m.get(i),
                    h.apparent_temperature.get(i),
                    h.precipitation_probability.get(i),
                    h.weather_code.get(i),
                    h.relative_humidity_2m.get(i),
                    aqiAt(aqiByHour, h.time.get(i)),
                    i == currentIndex));
        }
        hourly.setValue(hours);
    }

    /** Turns the daily part of the response into at most FORECAST_DAYS days and pushes them to the UI. */
    private void publishDaily(ForecastResponse.Daily d, String today,
                              @Nullable Map<String, Integer> aqiByHour) {
        if (d == null || d.time == null || d.weather_code == null || d.temperature_2m_max == null
                || d.temperature_2m_min == null || d.apparent_temperature_max == null
                || d.precipitation_probability_max == null
                || d.temperature_2m_mean == null || d.apparent_temperature_mean == null
                || d.relative_humidity_2m_mean == null) {
            Log.e("WeatherRepository", "Daily data missing; daily forecast not updated.");
            return;
        }
        List<DailyWeather> days = new ArrayList<>();
        for (int i = 0; i < Math.min(FORECAST_DAYS, d.time.size()); i++) {
            days.add(new DailyWeather(
                    d.time.get(i),
                    d.temperature_2m_max.get(i),
                    d.temperature_2m_min.get(i),
                    d.apparent_temperature_max.get(i),
                    d.precipitation_probability_max.get(i),
                    d.weather_code.get(i),
                    d.temperature_2m_mean.get(i),
                    d.apparent_temperature_mean.get(i),
                    d.relative_humidity_2m_mean.get(i),
                    worstAqiOfDay(aqiByHour, d.time.get(i)),
                    d.time.get(i).equals(today)));
        }
        daily.setValue(days);
    }

    /** Hourly AQI as time → value, skipping hours with no forecast. Null if the reply has none. */
    @Nullable
    private static Map<String, Integer> toAqiMap(@Nullable AirQualityResponse.Hourly h) {
        if (h == null || h.time == null || h.us_aqi == null) return null;
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < Math.min(h.time.size(), h.us_aqi.size()); i++) {
            if (h.us_aqi.get(i) != null) map.put(h.time.get(i), h.us_aqi.get(i));
        }
        return map;
    }

    /** AQI for one hour ("2026-11-12T15:00"), or AQI_UNAVAILABLE. */
    private static int aqiAt(@Nullable Map<String, Integer> aqiByHour, String time) {
        if (aqiByHour == null) return AQI_UNAVAILABLE;
        Integer aqi = aqiByHour.get(time);
        return aqi != null ? aqi : AQI_UNAVAILABLE;
    }

    /** Daily AQI = the day's worst (highest) hour. AQI_UNAVAILABLE if no hour has a value. */
    private static int worstAqiOfDay(@Nullable Map<String, Integer> aqiByHour, String date) {
        if (aqiByHour == null) return AQI_UNAVAILABLE;
        int worst = AQI_UNAVAILABLE;
        for (Map.Entry<String, Integer> e : aqiByHour.entrySet()) {
            if (e.getKey().startsWith(date + "T")) worst = Math.max(worst, e.getValue());
        }
        return worst;
    }

    // AQI to use when the air quality call fails: last cached value, or -1 = unavailable.
    private int fallbackAqi() {
        CurrentWeather cached = cache.load();
        return cached != null ? cached.usAqi : AQI_UNAVAILABLE;
    }
}
