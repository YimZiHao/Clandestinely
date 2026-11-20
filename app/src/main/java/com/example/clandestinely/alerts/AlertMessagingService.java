package com.example.clandestinely.alerts;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.clandestinely.data.model.AlertPrefs;
import com.example.clandestinely.data.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * M2: receives weather alerts pushed through Firebase Cloud Messaging.
 * Messages are data messages like {"state": "rain", "lead": "30"} so the user's
 * alert types and quiet hours are checked here, the same as AlertCheckWorker.
 */
public class AlertMessagingService extends FirebaseMessagingService {

    private static final String TAG = "AlertMessaging";
    private static final String KEY_STATE = "state";
    private static final String KEY_LEAD = "lead";

    /** FCM gave this phone a new token. Save it so the server can reach us. */
    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "New FCM token: " + token);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "Not signed in; token will be saved at sign-in.");
            return;                       // saved at sign-in instead
        }
        new UserRepository().addFcmToken(user.getUid(), token).addOnFailureListener(e -> Log.w(TAG, "Couldn't save FCM token.", e));
    }

    /** Runs on a background thread, so blocking calls are OK (keep it under ~10 s). */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        String state = data.get(KEY_STATE);
        if (state == null) {
            Log.w(TAG, "Push without a state; ignored.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "Not signed in; ignoring push for " + state);
            return;
        }

        AlertPrefs prefs = AlertCheckWorker.loadPrefs(user.getUid());
        if (!AlertCheckWorker.wanted(state, prefs)) {
            Log.d(TAG, "User turned off alerts for " + state);
            return;
        }
        if (AlertCheckWorker.inQuietHours(prefs)) {
            Log.d(TAG, "Quiet hours; not notifying about " + state);
            return;
        }

        int lead = parseLead(data.get(KEY_LEAD), prefs.getRainLeadMinutes());
        AlertNotifier.show(this, state, lead);
    }

    /** The "lead" value from the push, or the user's own setting if missing or not a number. */
    private static int parseLead(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}
