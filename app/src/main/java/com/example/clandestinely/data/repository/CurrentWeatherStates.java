package com.example.clandestinely.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.model.HourlyWeather;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.model.TipCacheKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The weather states active right now (e.g. ["rain", "thunderstorm"]), shared across the app.
 *
 * - M1 (weather classifier) calls update(...) whenever it has new weather.
 * - Screens like the Tips screen and the Home tip card watch get().
 * - Until M1 reports real weather, the states are ["normal"] and hasLiveWeather() is false.
 * - Week 7: it also keeps the latest weather numbers and hourly forecast, for the Gemini tip prompt.
 */
public final class CurrentWeatherStates {

    private static final MutableLiveData<List<String>> STATES =
            new MutableLiveData<>(Collections.singletonList(Schema.STATE_NORMAL));

    private static volatile boolean liveWeather = false;
    private static volatile boolean previewing = false;
    @Nullable private static volatile CurrentWeather latest;
    private static volatile List<HourlyWeather> hourly = Collections.emptyList();

    private CurrentWeatherStates() {
    }

    /** The current weather states. Watch this to update a screen when the weather changes. */
    public static LiveData<List<String>> get() {
        return STATES;
    }

    /**
     * For M1: report the states worked out from the latest weather data (weather_rules.md).
     * Safe to call from any thread. Unknown state names throw IllegalArgumentException.
     */
    public static void update(@NonNull List<String> states) {
        List<String> clean = TipCacheKey.normalisedStates(states);
        liveWeather = true;
        previewing = false;
        STATES.postValue(clean);
    }

    /** Same as update(states), and also keeps the weather numbers for the Gemini prompt. */
    public static void update(@NonNull List<String> states, @Nullable CurrentWeather weather) {
        latest = weather;
        update(states);
    }

    /** For M1: the hourly forecast rows (the Gemini prompt uses the next few hours). */
    public static void updateHourly(@Nullable List<HourlyWeather> hours) {
        hourly = hours == null
                ? Collections.<HourlyWeather>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(hours));
    }

    /** For testing and demos only: pretend the weather is these states. */
    public static void preview(@NonNull List<String> states) {
        previewing = true;
        STATES.postValue(TipCacheKey.normalisedStates(states));
    }

    /** True once M1 has reported real weather at least once. */
    public static boolean hasLiveWeather() {
        return liveWeather;
    }

    /** True while the debug "Preview weather" chips decide the states (the numbers don't match them). */
    public static boolean isPreview() {
        return previewing;
    }

    /** The latest weather numbers, or null if none yet. */
    @Nullable
    public static CurrentWeather latestWeather() {
        return latest;
    }

    /** The hourly forecast rows, or an empty list. */
    @NonNull
    public static List<HourlyWeather> hourly() {
        return hourly;
    }
}