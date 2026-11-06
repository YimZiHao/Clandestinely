package com.example.clandestinely;
import android.app.Application;

import com.example.clandestinely.ui.theme.ThemeHelper;

public class CuacaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.apply(this);
    }
}