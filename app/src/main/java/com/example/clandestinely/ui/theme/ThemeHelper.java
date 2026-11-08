package com.example.clandestinely.ui.theme;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {
    private static final String PREFS = "settings";
    private static final String KEY_LIGHT = "light_mode";

    private ThemeHelper() {}

    // Dark by default; light only if the user switched to it
    public static boolean isLight(Context context) {
        return prefs(context).getBoolean(KEY_LIGHT, false);
    }

    // Applies the saved choice (called when the app starts)
    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(isLight(context)
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES);
    }

    // Saves the choice and switches the theme right away
    public static void setLight(Context context, boolean light) {
        prefs(context).edit().putBoolean(KEY_LIGHT, light).apply();
        apply(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
