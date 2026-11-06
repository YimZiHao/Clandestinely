# Firestore Schema — CuacaUM

| | |
|---|---|
| **Owner** | Ammar |
| **Week** | 4 |
| **Version** | 4 (2 Nov 2026): rules, sample data and app code added |
| **Related modules** | M2 Alerts, M3 Smart Tips, M4 Routing, M5 Hazard Reporting, M6 Profile & Settings |
| **Aligned with** | `weather_rules.md` (weather states) and the app layer diagram |
| **Time zone** | All times are Malaysia time (UTC+8) |

---

## 1. What goes in Firestore (and what doesn't)

Firestore stores only data that is **shared between users** or that must **follow a user across devices**. Everything else stays on the phone, so the app keeps working offline (NFR2).

| Data | Stored in | Reason |
|---|---|---|
| User profile | Firestore `users` | Follows the user across devices (M6) |
| Settings & alert preferences | On device (local storage), copied to `users` when signed in | Works offline; the Firestore copy restores them on a new phone (M6, M2) |
| Hazard reports | Firestore `hazard_reports` | Shared: every user sees every report (M5) |
| Hazard photos | Firebase Storage `hazard_photos/` | Firestore isn't meant for images |
| AI-generated tips | Firestore `tips_cache` | Shared cache cuts Gemini calls (M3) |
| Weather data & current weather states | On device (local cache) | Comes from Open-Meteo, same for everyone (M1). Cached with a "last updated" time (NFR2) |
| Campus graph | `data/campus_graph.json` in the app | Fixed data, needed offline (M4) |
| Rule-based tip library | Bundled in the app (JSON) | Must work offline or when Gemini fails (FR3.3) |
| Emergency contacts & guidance | Bundled in the app | Must be available offline (FR7.3) |

---

## 2. Collection overview

```
users/{uid}
hazard_reports/{reportId}
    └── confirmations/{uid}
tips_cache/{cacheKey}
```

Firebase Storage:

```
hazard_photos/{reportId}.jpg
```

---

## 3. `users/{uid}`

One document per user. The document ID is the Firebase Auth `uid`.
Guests sign in with **Firebase Anonymous Auth**, so they also get a `uid` (FR6.1).

| Field | Type | Required | Example | Notes |
|---|---|---|---|---|
| `displayName` | string | yes | `"Ammar"` | `"Guest"` for anonymous users |
| `email` | string \| null | no | `"ammar@siswa.um.edu.my"` | `null` for guests |
| `isGuest` | boolean | yes | `false` | |
| `createdAt` | timestamp | yes | server timestamp | |
| `updatedAt` | timestamp | yes | server timestamp | Last time settings were synced from the phone |
| `usualLocations` | array\<string\> | no | `["KK8", "FSKTM"]` | Node IDs from `campus_graph.json` (FR6.2) |
| `health` | map | yes | see below | Used to personalise tips and routes (FR3.2, FR6.2) |
| `health.asthma` | boolean | yes | `true` | Stronger haze advice |
| `health.heatSensitive` | boolean | yes | `false` | Earlier and stronger heat advice |
| `health.uvSensitive` | boolean | yes | `false` | Sun-protection advice for skin conditions |
| `health.mobilityLimited` | boolean | yes | `false` | Wheelchair or limited mobility; extra care with flooded or slippery paths |
| `settings` | map | yes | see below | (FR6.3) |
| `settings.language` | string | yes | `"en"` | `"en"` or `"ms"` |
| `settings.textSize` | string | yes | `"normal"` | `"normal"` or `"large"` |
| `alertPrefs` | map | yes | see below | (FR2.1–2.3) |
| `alertPrefs.rain` | boolean | yes | `true` | Also covers thunderstorm |
| `alertPrefs.heat` | boolean | yes | `true` | |
| `alertPrefs.haze` | boolean | yes | `true` | |
| `alertPrefs.rainLeadMinutes` | number | yes | `60` | How early to warn about rain |
| `alertPrefs.quietStart` | string | no | `"23:00"` | 24-hour `HH:mm` |
| `alertPrefs.quietEnd` | string | no | `"07:00"` | 24-hour `HH:mm` |
| `fcmTokens` | array\<string\> | no | `["dX3k..."]` | Push notification tokens, one per device (M2) |

> `settings` and `alertPrefs` are read from local storage first (works offline). The app writes them to Firestore whenever they change, so they come back if the user signs in on a new phone. Guests' settings stay local only.

**Example document**

```json
{
  "displayName": "Ammar",
  "email": "ammar@siswa.um.edu.my",
  "isGuest": false,
  "createdAt": "2026-10-29T12:00:00+08:00",
  "updatedAt": "2026-10-29T12:00:00+08:00",
  "usualLocations": ["KK8", "FSKTM"],
  "health": {
    "asthma": true,
    "heatSensitive": false,
    "uvSensitive": false,
    "mobilityLimited": false
  },
  "settings": { "language": "en", "textSize": "normal" },
  "alertPrefs": {
    "rain": true, "heat": true, "haze": true,
    "rainLeadMinutes": 60,
    "quietStart": "23:00", "quietEnd": "07:00"
  },
  "fcmTokens": ["dX3k...example"]
}
```

---

## 4. `hazard_reports/{reportId}`

One document per reported hazard. The document ID is auto-generated.

| Field | Type | Required | Example | Notes |
|---|---|---|---|---|
| `category` | string | yes | `"flood"` | `"flood"`, `"fallen_tree"`, `"blocked_drain"`, `"other"` (FR5.1) |
| `description` | string | no | `"Water ankle-deep near the bus stop"` | Max 200 characters |
| `photoPath` | string \| null | no | `"hazard_photos/abc123.jpg"` | Path in Firebase Storage |
| `location` | geopoint | yes | `3.1209, 101.6538` | GPS pin (FR5.1) |
| `nearestEdgeId` | string \| null | no | `"KK8-BUSSTOP3"` | Set automatically by the app (see §4.2). `null` if no path is close enough |
| `reportedBy` | string | yes | uid | |
| `createdAt` | timestamp | yes | server timestamp | |
| `expiresAt` | timestamp | yes | see §4.1 | Report disappears after this (FR5.3) |
| `confirmCount` | number | yes | `0` | Number of other users who confirmed it |

**Example document**

```json
{
  "category": "flood",
  "description": "Water ankle-deep near the bus stop",
  "photoPath": "hazard_photos/abc123.jpg",
  "location": { "latitude": 3.1209, "longitude": 101.6538 },
  "nearestEdgeId": "KK8-BUSSTOP3",
  "reportedBy": "uid_ammar",
  "createdAt": "2026-10-29T15:10:00+08:00",
  "expiresAt": "2026-10-29T19:10:00+08:00",
  "confirmCount": 2
}
```

### Subcollection `hazard_reports/{reportId}/confirmations/{uid}`

One document per user who confirms the report. Using the user's `uid` as the document ID means **each person can confirm a report only once**.

| Field | Type | Example |
|---|---|---|
| `confirmedAt` | timestamp | server timestamp |

### 4.1 Expiry rules (FR5.3) — agreed with Intan

| Rule | Value |
|---|---|
| Default lifetime of a new report | **2 hours** |
| Each confirmation extends `expiresAt` by | **1 hour** |
| Maximum lifetime | **10 hours** after `createdAt` |
| Daily reset | **All reports end at midnight** (00:00 Malaysia time) |

The app calculates `expiresAt` as the **earliest** of:

1. `createdAt + 2 h + (confirmCount × 1 h)`
2. `createdAt + 10 h`
3. The next midnight (00:00 UTC+8) after `createdAt`

Example: a report made at 21:00 can never last past 00:00, even with confirmations.

- The app shows only reports where `expiresAt > now`, so expired reports disappear from the map straight away. No background job is needed to hide them.
- To actually delete old documents, turn on a Firestore **TTL policy** on `expiresAt` (needs the Blaze plan, so not set up yet). Firestore removes expired documents automatically, usually within a day. Photos in Storage need a separate clean-up (a Storage lifecycle rule, or a scheduled Cloud Function at midnight).

### 4.2 How `nearestEdgeId` is set (FR4.3)

The user only drops a GPS pin. The app then:

1. Finds the closest edge (path segment) in `campus_graph.json` to the pin.
2. If that edge is within **30 m** of the pin, saves its ID in `nearestEdgeId`. Daya's routing then adds a penalty to that edge.
3. If no edge is within 30 m, saves `null`. The report still shows on the map, but doesn't change any route.

The 30 m distance is a starting value; Daya can tune it once the graph is built.

---

## 5. `tips_cache/{cacheKey}`

Stores Gemini-generated tips so the same tip isn't requested again and again.
The cache is **shared by all users** and contains **no personal data**: the key is built only from the weather states, time of day and sensitivities.

### 5.1 Weather state values

These match `weather_rules.md`. **Several states can be active at once**, and the banner shows all of them (agreed by the team).

| State | Rule (from weather_rules.md) | Open-Meteo field |
|---|---|---|
| `normal` | Default, only when no other state is active | – |
| `rain` | `precipitation_probability` ≥ 70% | Forecast API |
| `thunderstorm` | `weather_code` ≥ 95 | Forecast API |
| `heat` | `apparent_temperature` ≥ 38 °C | Forecast API |
| `haze_sensitive` | `us_aqi` 101–150 (unhealthy for sensitive groups) | Air Quality API |
| `haze_unhealthy` | `us_aqi` ≥ 151 (unhealthy for everyone) | Air Quality API |
| `uv` | `uv_index` ≥ 8 ("very high" on the WHO UV index scale) | Forecast API |

`uv` is new in this schema so UV-sensitive users get sun-protection tips; `weather_rules.md` needs the same row added.

### 5.2 Key format

```
{states}_{timeSlot}_{sensitivities}
```

- **states:** all active states, sorted A–Z, joined with `+`
- **timeSlot:** `morning` 06–12, `afternoon` 12–18, `evening` 18–22, `night` 22–06
- **sensitivities:** the user's active health flags, sorted A–Z, joined with `+`, or `none`

| Situation | Key |
|---|---|
| Rain only, afternoon, no sensitivities | `rain_afternoon_none` |
| Rain and sensitive-level haze, afternoon, asthma | `haze_sensitive+rain_afternoon_asthma` |
| Heat, morning, UV-sensitive and limited mobility | `heat_morning_mobility+uv` |

Sensitivity names in keys: `asthma`, `heat`, `uv`, `mobility`.

### 5.3 Fields

| Field | Type | Required | Example | Notes |
|---|---|---|---|---|
| `states` | array\<string\> | yes | `["haze_sensitive", "rain"]` | Sorted A–Z |
| `timeSlot` | string | yes | `"afternoon"` | |
| `sensitivities` | array\<string\> | yes | `["asthma"]` | Empty array means none |
| `text` | string | yes | `"Rain from 3pm and the air is hazy. Take the covered walkway and wear a mask if you have asthma."` | |
| `source` | string | yes | `"ai"` | `"ai"` or `"rule"` (rule = fallback, FR3.3) |
| `model` | string \| null | no | `"gemini (Firebase AI Logic)"` | `null` for rule tips. Used in the Week 9 tip evaluation |
| `createdAt` | timestamp | yes | server timestamp | |
| `expiresAt` | timestamp | yes | `createdAt + 1 h` | After this, generate a fresh tip |

### 5.4 How the app uses it

1. Build the key from the current weather states, time slot and the user's sensitivities.
2. Read `tips_cache/{key}`. If it exists and `expiresAt > now`, show it.
3. Otherwise, ask Gemini through **Firebase AI Logic**, show the result and save it to the cache. Firebase AI Logic is called through the Firebase SDK, so there is no Gemini API key inside the app.
4. If Gemini fails or the phone is offline, build a tip from the bundled rule library instead: one rule tip per active state, plus any matching sensitivity tips (FR3.3, NFR2).

---

## 6. Which requirement uses which data

| Requirement | Collection / field |
|---|---|
| FR1.3 Weather state banner | Computed on device (not Firestore); can show several states |
| FR2.1–2.3 Alerts & quiet hours | `users.alertPrefs`, `users.fcmTokens` |
| FR3.1 Generate tip | `tips_cache` |
| FR3.2 Personalised tip | `users.health` → `tips_cache.sensitivities` |
| FR3.3 Fallback | Bundled rule library (not Firestore) |
| FR4.3 Avoid hazards | `hazard_reports.nearestEdgeId` |
| FR5.1 Report hazard | `hazard_reports` + Storage `hazard_photos/` |
| FR5.2 View hazards on map | `hazard_reports` where `expiresAt > now` |
| FR5.3 Expiry & confirmation | `expiresAt`, `confirmCount`, `confirmations/` |
| FR6.1 Sign in / guest | Firebase Auth (Google + Anonymous) → `users.isGuest` |
| FR6.2 Locations & sensitivities | `users.usualLocations`, `users.health` |
| FR6.3 Language & text size | `users.settings` |

---

## 7. Main queries

| Screen | Query | Index needed |
|---|---|---|
| Hazard map | `hazard_reports` where `expiresAt > now`, ordered by `expiresAt` | No (single field) |
| Routing | Same as above, then use `nearestEdgeId` of each result | No |
| Profile / settings | `users/{uid}` | No |
| Tips | `tips_cache/{key}` | No |

Every query uses a single field, so no composite indexes are needed.

---

## 8. Security rules

The live rules are in `firebase/firestore.rules` and `firebase/storage.rules`, deployed with `npm run deploy` (see `docs/firestore_setup.md`). In plain terms:

| Collection | Who can read | Who can write |
|---|---|---|
| `users/{uid}` | Only that user | Only that user; unknown fields and invalid settings are rejected |
| `hazard_reports` | Anyone signed in (guests too) | Create: your own report, valid category, 0 confirmations, `createdAt` = server time, lasts at most 2 h. Delete: only the reporter |
| Confirming a report | – | Anyone signed in **except the reporter**, once per person, only while the report is active. Only `confirmCount` (+1) and `expiresAt` (+1 h at most, never past 10 h) can change, together with the user's `confirmations` doc |
| `tips_cache` | Anyone signed in | Signed-in users **except guests**, if the tip is well formed (valid time slot and source, text up to 300 characters, cached for at most 1 h) and saved under the ID that matches its states, time slot and sensitivities. Tips can't be deleted from the app |
| `hazard_photos/` (Storage) | Anyone signed in | Upload only: JPEG name, image type, under 5 MB. Photos can't be overwritten or deleted from the app |
| Anything else | No one | No one |

The midnight cut-off is applied by the app when it calculates `expiresAt`; the rules only enforce the 2 h / +1 h / 10 h limits. Rules allow 5 minutes of leeway for a phone clock that is slightly fast.

**Known limitation:** signed-in (non-guest) users write to `tips_cache` directly, so a modified app could still save a misleading tip. A production app would move tip generation to a Cloud Function. This is acceptable for the prototype and can be listed under Limitations in the report.

**Storage needs the Blaze plan.** Since 3 Feb 2026, Cloud Storage for Firebase requires a project on the Blaze (pay-as-you-go) plan with a billing account linked. Usage within the free quota still costs nothing. Until the team decides, Firestore works without it, but hazard photo upload (FR5.1) won't.

---

## 9. Decisions

### Agreed

| # | Decision | Agreed value | Agreed by |
|---|---|---|---|
| 1 | Rule-based tip library location | Bundled in the app | Team |
| 2 | Report lifetime | 2 h default, +1 h per confirmation, max 10 h, all end at midnight | Intan |
| 3 | How `nearestEdgeId` is set | App snaps the pin to the closest edge within 30 m, otherwise `null` | Team (Daya to tune the distance) |
| 4 | Multiple weather states at once | Show all active states; tip key includes all of them | Team |
| 5 | Health sensitivities | Asthma, heat-sensitive, UV-sensitive, limited mobility | Intan |
| 6 | Settings backup | Local first, copied to Firestore for signed-in users | Zi Hao |
| 7 | AI tip cache lifetime | 1 h | Ammar |
| 8 | Time-slot boundaries | 06 / 12 / 18 / 22 | Ammar |
| 9 | Max report lifetime | 10 h (top of Intan's 8–10 h range) | Ammar |
| 10 | UV state | Added: `uv_index` ≥ 8. Zi Hao to add the same row to `weather_rules.md` | Ammar |
| 11 | Limited mobility routing | Routing avoids stairs and steep paths for users with `health.mobilityLimited = true`. Daya to add a `step_free` (boolean) field to each edge in `campus_graph.json` | Ammar |
