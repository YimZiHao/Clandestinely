package com.example.clandestinely.ui.tips;

import android.content.Context;

import com.example.clandestinely.R;
import com.example.clandestinely.data.model.Schema;

import java.util.List;

/** Turns state and sensitivity names (e.g. "haze_sensitive") into text for the screen. */
public final class WeatherStateLabels {

    private WeatherStateLabels() {
    }

    public static String state(Context context, String state) {
        switch (state) {
            case Schema.STATE_THUNDERSTORM:
                return context.getString(R.string.state_thunderstorm);
            case Schema.STATE_HAZE_UNHEALTHY:
                return context.getString(R.string.state_haze_unhealthy);
            case Schema.STATE_RAIN:
                return context.getString(R.string.state_rain);
            case Schema.STATE_HEAT:
                return context.getString(R.string.state_heat);
            case Schema.STATE_HAZE_SENSITIVE:
                return context.getString(R.string.state_haze_sensitive);
            case Schema.STATE_UV:
                return context.getString(R.string.state_uv);
            case Schema.STATE_NORMAL:
                return context.getString(R.string.state_normal);
            default:
                return state;
        }
    }

    public static String sensitivity(Context context, String sensitivity) {
        switch (sensitivity) {
            case Schema.SENSITIVITY_ASTHMA:
                return context.getString(R.string.sensitivity_asthma);
            case Schema.SENSITIVITY_HEAT:
                return context.getString(R.string.sensitivity_heat);
            case Schema.SENSITIVITY_UV:
                return context.getString(R.string.sensitivity_uv);
            case Schema.SENSITIVITY_MOBILITY:
                return context.getString(R.string.sensitivity_mobility);
            default:
                return sensitivity;
        }
    }

    /** e.g. "Thunderstorm · Rain" */
    public static String join(Context context, List<String> states) {
        StringBuilder sb = new StringBuilder();
        for (String s : states) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(state(context, s));
        }
        return sb.toString();
    }
}