package com.example.clandestinely.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.model.TipCacheKey;

import java.util.Collections;
import java.util.List;

/**
 * The weather states active right now (e.g. ["rain", "thunderstorm"]), shared across the app.
 *
 * - M1 (weather classifier) calls update(...) whenever it has new weather.
 * - Screens like the Tips screen and the Home tip card watch get().
 * - Until M1 reports real weather, the states are ["normal"] and hasLiveWeather() is false.
 */
public final class CurrentWeatherStates {

    private static final MutableLiveData<List<String>> STATES =
            new MutableLiveData<>(Collections.singletonList(Schema.STATE_NORMAL));

    private static volatile boolean liveWeather = false;

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
        STATES.postValue(clean);
    }

    /** For testing and demos only: pretend the weather is these states. */
    public static void preview(@NonNull List<String> states) {
        STATES.postValue(TipCacheKey.normalisedStates(states));
    }

    /** True once M1 has reported real weather at least once. */
    public static boolean hasLiveWeather() {
        return liveWeather;
    }
}