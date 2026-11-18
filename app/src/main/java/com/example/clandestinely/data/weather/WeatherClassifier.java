package com.example.clandestinely.data.weather;

import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.repository.WeatherRepository;
import com.example.clandestinely.tips.RuleTipLibrary;

import java.util.ArrayList;
import java.util.List;

public class WeatherClassifier {

    public static final int RAIN_PROBABILITY = 70;    // %
    public static final int THUNDERSTORM_CODE = 95;   // WMO weather_code
    public static final double HEAT_APPARENT = 38.0;  // °C
    public static final int AQI_SENSITIVE = 101;
    public static final int AQI_UNHEALTHY = 151;

    private WeatherClassifier() {}

    /** All active states, e.g. [rain, heat]. Never empty: falls back to normal. */
    public static List<String> classify(CurrentWeather w) {
        List<String> states = new ArrayList<>();
        if (w.weather_code >= THUNDERSTORM_CODE) states.add(Schema.STATE_THUNDERSTORM);
        if (w.precipitation_probability >= RAIN_PROBABILITY) states.add(Schema.STATE_RAIN);
        if (w.apparent_temperature >= HEAT_APPARENT) states.add(Schema.STATE_HEAT);
        if (w.usAqi != WeatherRepository.AQI_UNAVAILABLE) {
            if (w.usAqi >= AQI_UNHEALTHY) states.add(Schema.STATE_HAZE_UNHEALTHY);
            else if (w.usAqi >= AQI_SENSITIVE) states.add(Schema.STATE_HAZE_SENSITIVE);
        }
        if (states.isEmpty()) states.add(Schema.STATE_NORMAL);
        return states;
    }

    /** The one state to show in the summary: the most dangerous. */
    public static String mostSevere(List<String> states) {
        for (String s : RuleTipLibrary.SEVERITY_ORDER) {
            if (states.contains(s)) return s;
        }
        return Schema.STATE_NORMAL;
    }
}
