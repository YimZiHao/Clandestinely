package com.example.clandestinely.data.repository;

import androidx.annotation.NonNull;

import com.example.clandestinely.data.model.AlertPrefs;
import com.example.clandestinely.data.model.Health;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.model.UserProfile;
import com.example.clandestinely.data.model.UserSettings;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * users/{uid} (M6 Profile & Settings, FR6.1–6.3).
 *
 * Every method returns a Task. Use it like this:
 * <pre>
 * userRepository.getProfile(uid)
 *     .addOnSuccessListener(profile -> { ... })
 *     .addOnFailureListener(e -> { ... });
 * </pre>
 *
 * Settings and alert preferences live on the phone first (SharedPreferences), so they work
 * offline. Call {@link #saveSettings} whenever they change, to back them up for signed-in users.
 */
public class UserRepository {

    /** Same limits as validUser() in firebase/firestore.rules. */
    static final int MAX_NAME_LENGTH = 60;
    static final int MAX_EMAIL_LENGTH = 120;

    private final FirebaseFirestore db;

    public UserRepository() {
        this(FirestoreDb.get());
    }

    public UserRepository(FirebaseFirestore db) {
        this.db = db;
    }

    private DocumentReference userDoc(String uid) {
        return db.collection(Schema.USERS).document(uid);
    }

    /**
     * Call right after sign-in (Google or guest). Creates the profile the first time,
     * and returns the saved profile.
     */
    public Task<UserProfile> ensureProfile(@NonNull FirebaseUser user) {
        DocumentReference ref = userDoc(user.getUid());
        return ref.get().continueWithTask(getTask -> {
            if (!getTask.isSuccessful()) throw getTask.getException();
            DocumentSnapshot existing = getTask.getResult();
            if (existing.exists()) {
                return Tasks.forResult(toProfile(existing, user.getUid()));
            }
            UserProfile profile = new UserProfile();
            String name = user.getDisplayName();
            if (name == null || name.trim().isEmpty()) {
                name = user.isAnonymous() ? "Guest" : "UM user";
            }
            // Firestore rules allow displayName up to 60 characters and email up to 120.
            profile.setDisplayName(limit(name.trim(), MAX_NAME_LENGTH));
            profile.setEmail(user.isAnonymous() ? null : limit(user.getEmail(), MAX_EMAIL_LENGTH));
            profile.setGuest(user.isAnonymous());
            // createdAt / updatedAt stay null so Firestore fills in the server time.
            return ref.set(profile)
                    .continueWithTask(setTask -> {
                        if (!setTask.isSuccessful()) throw setTask.getException();
                        return ref.get();
                    })
                    .continueWith(readTask -> {
                        if (!readTask.isSuccessful()) throw readTask.getException();
                        return toProfile(readTask.getResult(), user.getUid());
                    });
        });
    }

    /** Returns the profile, or null if this user has none yet. */
    public Task<UserProfile> getProfile(@NonNull String uid) {
        return userDoc(uid).get().continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            DocumentSnapshot snapshot = task.getResult();
            return snapshot.exists() ? toProfile(snapshot, uid) : null;
        });
    }

    /** Backs up settings and alert preferences. Guests' settings stay on the phone only. */
    public Task<Void> saveSettings(@NonNull UserProfile profile, @NonNull UserSettings settings,
                                   @NonNull AlertPrefs alertPrefs) {
        if (profile.isGuest()) return Tasks.forResult(null);
        Map<String, Object> changes = new HashMap<>();
        changes.put("settings", settings);
        changes.put("alertPrefs", alertPrefs);
        return update(profile.getUid(), changes);
    }

    /** FR6.2: health sensitivities. */
    public Task<Void> updateHealth(@NonNull String uid, @NonNull Health health) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("health", health);
        return update(uid, changes);
    }

    /** FR6.2: usual places, as node IDs from campus_graph.json (e.g. "KK8", "FSKTM"). */
    public Task<Void> updateUsualLocations(@NonNull String uid, @NonNull List<String> nodeIds) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("usualLocations", nodeIds);
        return update(uid, changes);
    }

    /** M2: save this phone's push notification token. */
    public Task<Void> addFcmToken(@NonNull String uid, @NonNull String token) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("fcmTokens", FieldValue.arrayUnion(token));
        return update(uid, changes);
    }

    public Task<Void> removeFcmToken(@NonNull String uid, @NonNull String token) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("fcmTokens", FieldValue.arrayRemove(token));
        return update(uid, changes);
    }

    private Task<Void> update(String uid, Map<String, Object> changes) {
        changes.put("updatedAt", FieldValue.serverTimestamp());
        return userDoc(uid).update(changes);
    }

    /** Cuts {@code value} to at most {@code max} characters; null stays null. */
    static String limit(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static UserProfile toProfile(DocumentSnapshot snapshot, String uid) {
        UserProfile profile = snapshot.toObject(UserProfile.class);
        if (profile == null) profile = new UserProfile();
        profile.setUid(uid);
        return profile;
    }
}
