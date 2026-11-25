package com.example.clandestinely;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/**
 * App Check for RELEASE builds only (src/release): Play Integrity.
 * The debug version of this class lives in src/debug.
 */
final class AppCheckSetup {

    private AppCheckSetup() {
    }

    static void install() {
        FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
    }
}