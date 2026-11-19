package com.example.clandestinely.tips.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.model.HourlyWeather;

import java.util.Collections;
import java.util.List;

/**
 * Everything the AI tip step needs for one request (M3, Week 7).
 *
 * Holds no personal data: only weather numbers, the time, the user's sensitivity flags
 * (e.g. "asthma") and the app language. No name, email or user ID ever goes to Gemini.
 */
public final class TipRequest {

    /** Active weather states, already normalised (e.g. ["heat", "rain"]). */
    public final List<String> states;
    /** Sensitivity names from the profile (e.g. ["asthma"]). Empty for guests. */
    public final List<String> sensitivities;
    /** "morning", "afternoon", "evening" or "night". */
    public final String timeSlot;
    /** Malaysia time, "HH:mm". */
    public final String localTime;
    /** "en" or "ms". */
    public final String language;
    /** Latest weather from M1, or null if there is none yet (or in preview mode). */
    @Nullable public final CurrentWeather now;
    /** The next few hours of the forecast. May be empty. */
    public final List<HourlyWeather> nextHours;
    /** Guests can't write tips_cache, so they skip Gemini and use the rule library. */
    public final boolean guest;
    /** Debug "Preview weather" chips are on: never read or write the shared cache. */
    public final boolean preview;

    public TipRequest(@NonNull List<String> states, @NonNull List<String> sensitivities,
                      @NonNull String timeSlot, @NonNull String localTime, @NonNull String language,
                      @Nullable CurrentWeather now, @Nullable List<HourlyWeather> nextHours,
                      boolean guest, boolean preview) {
        this.states = Collections.unmodifiableList(states);
        this.sensitivities = Collections.unmodifiableList(sensitivities);
        this.timeSlot = timeSlot;
        this.localTime = localTime;
        this.language = "ms".equals(language) ? "ms" : "en";
        this.now = now;
        this.nextHours = nextHours == null
                ? Collections.<HourlyWeather>emptyList()
                : Collections.unmodifiableList(nextHours);
        this.guest = guest;
        this.preview = preview;
    }
}