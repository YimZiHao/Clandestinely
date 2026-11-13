package com.example.clandestinely.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.clandestinely.data.model.CurrentWeather;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class WeatherCache {

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private static final String KEY_CURRENT = "current_weather";

    public WeatherCache(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences("weather_cache",Context.MODE_PRIVATE);
    }

    public void save(CurrentWeather currentWeather) {
        String json = gson.toJson(currentWeather);  // objects --> json
        prefs.edit()
                .putString(KEY_CURRENT, json)
                .apply();
    }

    public CurrentWeather load() {
        String json = prefs.getString(KEY_CURRENT, null);
        if (json == null) {
            return null;
        }
        try {
            return gson.fromJson(json, CurrentWeather.class);
        } catch (JsonSyntaxException e) {
            Log.w("WeatherCache", "Cached weather unreadable", e);
            return null;
        }
    }
}
