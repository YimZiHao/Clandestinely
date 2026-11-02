# Firebase Auth + Database Code (Java) — CuacaUM

Week 5 task: **Firebase Auth (Google + guest)**, plus the Java code the app uses to read and write the Firestore database.

## What's in this package

| File | What it does |
|---|---|
| `auth/AuthRepository.java` | Sign in with Google, continue as guest, sign out |
| `ui/signin/SignInActivity.java` | The sign-in screen (first screen of the app) |
| `res/layout/activity_sign_in.xml` | Layout of the sign-in screen |
| `res/values/strings_auth.xml` | Text on the sign-in screen |
| `data/model/*.java` | Data classes matching the Firestore schema, plus `HazardExpiry` and `TipCacheKey` |
| `data/repository/*.java` | `FirestoreDb`, `UserRepository`, `HazardRepository`, `TipCacheRepository` |
| `test/.../HazardExpiryTest.java`, `TipCacheKeyTest.java` | Unit tests (13 tests) |

---

## Steps 1–3: Copy the files in

The files already use our package, `com.example.clandestinely`.

In Android Studio's **Project** view, right-click `app/src/main/java/com/example/clandestinely` → **Open In → Explorer**, then:

1. Copy the zip's **`auth`** and **`data`** folders (from `app/src/main/java/com/example/clandestinely/`) into that folder, next to `MainActivity.kt`.
2. Copy the zip's **`ui/signin`** folder into the existing **`ui`** folder.
3. Copy `activity_sign_in.xml` into `app/src/main/res/layout/` (create the `layout` folder if it doesn't exist).
4. Copy `strings_auth.xml` into `app/src/main/res/values/`.
5. Copy the two test files into `app/src/test/java/com/example/clandestinely/data/model/` (create the folders if needed).

The project is currently Kotlin + Jetpack Compose. These Java files work alongside the Kotlin ones, so nothing else needs converting first.

## Step 4: Register the app in Firebase and get `google-services.json`

1. Firebase console → **Settings → Project settings → General**. Scroll to **Your apps**.
2. If there's no Android app: click **Add app → Android**, type your package name, click **Register app**.
3. Click **Download google-services.json** and put the file in your project's **`app/`** folder (next to `build.gradle.kts`).

Download it **again** even if someone added the app earlier: the file only contains the Google sign-in details if it was downloaded **after** Google sign-in was turned on.

⚠️ `google-services.json` is fine to keep in the project, but **don't** put `service-account.json` anywhere near the app.

## Step 5: Add your SHA-1 fingerprint (needed for Google sign-in)

Google sign-in only works on laptops whose fingerprint is registered. **Every teammate who runs the app from their own laptop must do this once.**

1. In Android Studio, open the **Terminal** tab at the bottom.
2. Run:
   ```
   .\gradlew signingReport
   ```
   (On Mac: `./gradlew signingReport`.)
3. Find the block that says **Variant: debug** and copy the line **SHA1: AA:BB:CC:…**
4. Firebase console → Project settings → Your apps → your Android app → **Add fingerprint** → paste → **Save**.

## Step 6: Add the libraries

**`build.gradle.kts` (Project level, the one at the top of the repo)**, inside `plugins { }`:

```kotlin
id("com.google.gms.google-services") version "4.4.2" apply false
```

**`app/build.gradle.kts` (Module level)**, inside `plugins { }`:

```kotlin
id("com.google.gms.google-services")
```

and inside `dependencies { }`:

```kotlin
// Firebase (use the latest BoM version from firebase.google.com/docs/android/setup if this one is old)
implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
implementation("com.google.firebase:firebase-auth")
implementation("com.google.firebase:firebase-firestore")
implementation("com.google.firebase:firebase-storage")

// Google sign-in through Android's Credential Manager
implementation("androidx.credentials:credentials:1.3.0")
implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
```

Click **Sync Now** in the yellow bar at the top.

## Step 7: Make the sign-in screen the first screen

Open `app/src/main/AndroidManifest.xml`.

1. Find the `<activity android:name=".MainActivity" ...>` block. **Cut** its `<intent-filter>…</intent-filter>` part (the lines with `MAIN` and `LAUNCHER`).
2. Add this inside `<application>`, and paste the intent filter into it:

```xml
<activity
    android:name=".ui.signin.SignInActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

3. Change MainActivity's `android:exported="true"` to `"false"`.

## Step 8: Run it

Use a phone, or an emulator **with Google Play** (the emulator image with the Play Store icon), signed into a Google account.

1. Click ▶ **Run**. The sign-in screen appears.
2. Tap **Sign in with Google**, pick an account. You should land on the home screen.
3. Check the Firebase console:
   - **Security → Authentication → Users** shows your account.
   - **Firestore → Data → users** has a new document with your name and `isGuest: false`.
4. Test guest mode: uninstall the app (or clear its data), run it again, tap **Continue as guest**. A second user appears with `isGuest: true`.
5. Close and reopen the app: it should skip the sign-in screen.

## Step 9: Run the unit tests

Right-click `app/src/test/java` → **Run 'Tests in…'**. All 13 should pass.

---

## How teammates use the code

| Who | Use |
|---|---|
| Daniel (Settings) | Sign out button: `new AuthRepository().signOut(this);` then open `SignInActivity` and `finish()` |
| Intan, Daniel (Profile) | `new UserRepository().updateHealth(uid, health)`, `updateUsualLocations(...)`, `saveSettings(...)` |
| Zi Hao (Alerts) | `new UserRepository().addFcmToken(uid, token)` |
| Intan (Hazard reports) | `new HazardRepository().submitReport(...)`, `listenToActiveReports(...)`, `confirmReport(...)` |
| Daya (Routing) | `HazardReport.blockedEdgeIds(reports, System.currentTimeMillis())` |
| Ammar (Tips) | `TipCacheKey.build(...)`, `new TipCacheRepository().getFreshTip(key)`, `saveTip(...)` |

The current user's ID anywhere in the app: `FirebaseAuth.getInstance().getCurrentUser().getUid()`.

Every repository method returns a `Task`:

```java
new UserRepository().getProfile(uid)
        .addOnSuccessListener(profile -> { /* use it */ })
        .addOnFailureListener(e -> { /* show an error */ });
```

Always get Firestore through `FirestoreDb.get()`, never `FirebaseFirestore.getInstance()` with no argument: our database is named `default`, and the no-argument version looks for `(default)`.

**Hazard photos:** `submitReport(..., photo)` needs Firebase Storage, which needs the Blaze plan. Pass `null` for the photo until the team decides.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `Cannot resolve symbol 'default_web_client_id'` | `google-services.json` is missing, in the wrong folder, or was downloaded before Google sign-in was turned on. Download it again (step 4) and Sync |
| `Cannot resolve symbol 'MainActivity'` | Your home screen has a different name. Change `MainActivity` in `SignInActivity.java` |
| Google sign-in: "Google sign-in failed" right away | Your SHA-1 isn't added (step 5), or `google-services.json` is old |
| "No Google account found on this phone" | Add a Google account in the phone's Settings → Accounts, or use an emulator image with Google Play |
| Signed in, but no document in `users` | Check Firestore rules are deployed (`npm run deploy` in the `firebase` folder) and that the code uses `FirestoreDb.get()` |
| `PERMISSION_DENIED` in Logcat | The write breaks a Firestore rule. Check field names against `docs/firestore_schema.md` |
