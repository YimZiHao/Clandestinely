package com.example.clandestinely;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/**
 * App Check for DEBUG builds only (src/debug). Prints a debug token to Logcat
 * ("DebugAppCheckProvider"); register it in Firebase → App Check → Manage debug tokens.
 * The release version of this class (src/release) uses Play Integrity, so the debug
 * provider never ships in the release APK.
 */
final class AppCheckSetup {

    private AppCheckSetup() {
    }

    static void install() {
        FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
    }
}