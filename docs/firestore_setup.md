# Firestore Setup — CuacaUM

How to turn `docs/firestore_schema.md` into the live database. Takes about 20 minutes.

**What's in this package**

| Path | What it is |
|---|---|
| `firebase/firestore.rules` | Firestore security rules (who can read and write what) |
| `firebase/storage.rules` | Storage rules for hazard photos |
| `firebase/firestore.indexes.json` | Index config (none needed; every query uses one field) |
| `firebase/firebase.json` | Tells the Firebase CLI where the files are |
| `firebase/seed.js` | Creates all collections with sample documents |
| `firebase/schema.js` | Schema field list, expiry rules and tip keys (Node version) |
| `firebase/test/` | Tests for the logic and the security rules |

---

## What you need

- **Node.js 20 or newer** (LTS from nodejs.org)
- **Owner or Editor access** to the team's Firebase project. If Zi Hao created it, ask him to add you: ⚙️ Project settings → Users and permissions → Add member → Editor.
- Optional, only for the rules tests: **Java 11 or newer**

---

## 1. Firebase console (one-time)

Skip anything that's already done.

1. **Firestore:** done. Our database is named **`default`** (Enterprise edition). That's different from the usual `(default)` with brackets, so `firebase.json`, the seed script and the app code (`FirestoreDb.instance`) all ask for `default` by name. Don't create another database from the terminal: that needs billing.
2. **Sign-in methods:** Security → Authentication → Get started → Sign-in method → enable **Google** and **Anonymous** (Anonymous is what "Continue as guest" uses).

## 2. Install the tools

Put the `firebase/` folder in the root of the repo, then in a terminal:

```bash
cd firebase
npm install
npx firebase login
npx firebase use --add      # pick the CuacaUM project, call it "default"
```

## 3. Deploy the rules

```bash
npm run deploy
```

This uploads `firestore.rules` and `firestore.indexes.json`. Check it worked: console → Firestore Database → **Rules** tab shows our rules.

## 4. Create the collections with sample data

1. Console → ⚙️ Project settings → **Service accounts** → **Generate new private key**.
2. Save the file as `firebase/service-account.json`. **Never commit or share this file.** It's already in `.gitignore`.
3. Run:

   macOS / Linux:
   ```bash
   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json npm run seed
   ```
   Windows (PowerShell):
   ```powershell
   $env:GOOGLE_APPLICATION_CREDENTIALS=".\service-account.json"; npm run seed
   ```

You should see `Wrote 8 sample documents`. Console → Firestore → **Data** now shows:

- `users`: `sample_user`, `sample_guest`
- `hazard_reports`: `sample_flood` (with a `confirmations` subcollection), `sample_fallen_tree`
- `tips_cache`: `rain_afternoon_none`, `haze_sensitive_afternoon_asthma`, `heat_afternoon_none`

The sample reports expire about 2–3 hours after you run the seed, like real ones. Run it again whenever you need fresh ones. To remove all sample data (e.g. before the demo): `npm run seed:clean` with the same key setting.

Want to see the sample documents without touching Firebase? `npm run seed:dry`.

## 5. Auto-delete expired documents (optional, needs Blaze)

**Skipped for now:** TTL policies need billing enabled (the console returns "billing disabled" on the Spark plan). The app already hides expired reports and tips, so nothing breaks. Do this only if the team moves to Blaze.

The app already hides expired reports and tips. To also delete them from the database, add TTL policies on `expiresAt`:

Google Cloud console → Firestore → select the **default** database → **Time-to-live (TTL)** → Create policy:
- Collection group `hazard_reports`, timestamp field `expiresAt`
- Collection group `tips_cache`, timestamp field `expiresAt`

Firestore deletes expired documents within about a day.

## 6. Hazard photos (needs a team decision)

Since 3 Feb 2026, **Cloud Storage for Firebase needs the Blaze plan**: a billing account with a card linked. Usage within the free quota (5 GB stored) still costs nothing.

- **If the team upgrades:** console → Upgrade → Blaze, then set a **budget alert** (e.g. RM 5) in Google Cloud Billing. Then Build → Storage → Get started (same location), and run `npm run deploy:storage`.
- **If not:** everything else works. `HazardRepository.submitReport()` just needs `photo = null` until Storage is set up.

## 7. Connect the app

The app is written in **Java**. The Java database code and the sign-in screen are in a separate package: follow **`docs/auth_setup.md`**.

## 8. Run the tests

```bash
npm test             # expiry rules, tip keys, sample data vs schema (no Firebase needed)
npm run test:rules   # security rules on the local emulator (needs Java 11+)
```

`test:rules` downloads the Firestore emulator the first time.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `Could not load the default credentials` | `GOOGLE_APPLICATION_CREDENTIALS` isn't set in this terminal, or the path is wrong (step 4) |
| `PERMISSION_DENIED` when deploying or seeding | Your Google account isn't Owner/Editor on the project |
| `PERMISSION_DENIED` in the app | The write breaks a rule. Check field names and values against `firestore_schema.md` |
| Report creation fails on one phone only | That phone's clock is more than 5 minutes fast. Turn on automatic date and time |
| `This API method requires billing to be enabled` when deploying | `firebase.json` is missing `"database": "default"`, so the CLI tries to create a new database |
| `firebase: command not found` | Use `npx firebase ...`, or run the `npm run` scripts |
