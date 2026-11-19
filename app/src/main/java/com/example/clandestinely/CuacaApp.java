package com.example.clandestinely;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import com.example.clandestinely.alerts.AlertCheckWorker;
import com.example.clandestinely.ui.theme.ThemeHelper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class CuacaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.apply(this);
        createAlertChannel();
        setUpAppCheck();
    }

    /**
     * M3: Firebase AI Logic (Gemini) only answers apps that pass App Check.
     * Debug builds use a debug token (register it in the Firebase console, see the PR);
     * release builds use Play Integrity.
     */
    private void setUpAppCheck() {
        FirebaseApp.initializeApp(this);
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(debuggable
                ? DebugAppCheckProviderFactory.getInstance()
                : PlayIntegrityAppCheckProviderFactory.getInstance());
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