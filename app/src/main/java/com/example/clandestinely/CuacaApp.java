package com.example.clandestinely;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.example.clandestinely.alerts.AlertCheckWorker;
import com.example.clandestinely.ui.theme.ThemeHelper;

public class CuacaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.apply(this);
        createAlertChannel();
    }

    /** M2: Android 8+ only shows notifications that belong to a channel. Safe to call every launch. */
    private void createAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                AlertCheckWorker.CHANNEL_ID,
                getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.alert_channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}