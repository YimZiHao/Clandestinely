# CuacaUM (working title) — Campus Weather & Safe-Route App

**Group:** Clandestinely · **Course:** WIA2007 Mobile Application Development, Group Project (20%) · **Semester:** 2026/2027 S1, Universiti Malaya

**Repo:** https://github.com/YimZiHao/Clandestinely (default branch: `main`)

### Getting started (team members)
1. Clone the repo: `git clone https://github.com/YimZiHao/Clandestinely.git`
2. Open the folder in Android Studio, let Gradle sync, then press **Run**.
3. Create a branch for your work: `git checkout -b feature/<module>-<short-desc>`. Never commit straight to `main`.
4. Push your branch with `git push -u origin <your-branch>`, then open a Pull Request on GitHub.

> This document is the single source of truth for the project. It is written to be read by humans **and** by Claude Code.
> Claude Code loads it automatically every session through `CLAUDE.md` (which contains `@docs/PROJECT_PLAN.md`).

---

## 1. The problem (why this app exists)

UM students and staff walk between faculties, colleges, the library and transport stops all day, and campus weather changes fast:

- **Rain:** sudden afternoon downpours, flooded walkways and roads, fallen trees, traffic jams at peak hours.
- **Heat:** long exposed walks between buildings in high heat index; dehydration risk.
- **Haze:** seasonal haze with unhealthy air quality, worst for people with asthma or other respiratory conditions.

General weather apps give a city-level forecast for "Kuala Lumpur". They don't say *what to do on campus right now*, or *which way to walk* to stay dry, shaded, or away from a flooded road.

**CuacaUM** monitors weather at UM, warns users before bad weather hits, gives practical tips, and routes them across campus by the path that suits the weather: covered when it rains, shaded when it's hot, and around reported flood spots.

The prototype covers **UM campus only**. Future versions could add other climates and hazards (snow, flood forecasting for other campuses, and so on).

## 2. SDG alignment

| SDG | Target | How CuacaUM contributes |
|---|---|---|
| **13 Climate Action** | **13.1** Strengthen resilience and adaptive capacity to climate-related hazards | Early alerts for rain, heat and haze. Weather-aware routing helps people avoid exposure and flooded paths. Crowdsourced hazard reports (flooded path, fallen tree, blocked drain). |
| **13 Climate Action** | **13.3** Improve education, awareness-raising and early warning | Contextual tips explain *why* a condition is risky and what to do. Push notifications act as campus-level early warning. |

**Primary target: 13.1.** 13.3 is secondary.

## 3. Feature ideas → modules

Source of each idea: Zi Hao (weather monitoring), Ammar (tips + bad-weather routing), Daya (shadiest route).

The report needs **at least 5 modules × 3 functional requirements**, including at least one AI module. Draft:

### M1. Campus Weather Monitoring (Zi Hao)
- FR1.1 The user shall be able to view current conditions at UM: temperature, feels-like, humidity, rain probability and air quality.
- FR1.2 The user shall be able to view an hourly forecast for the next 24 hours.
- FR1.3 The system shall classify the current state as Normal / Rain / Thunderstorm / Heat / Moderate Haze / Unhealthy Haze and show a matching status banner.

### M2. Alerts & Notifications (Zi Hao)
- FR2.1 The system shall send a push notification when rain is expected within the next 60 minutes (the threshold is configurable).
- FR2.2 The system shall alert the user when the apparent temperature or air quality crosses a set threshold.
- FR2.3 The user shall be able to choose which alert types to receive and set quiet hours.

### M3. Smart Tips — AI module (Ammar)
- FR3.1 The system shall generate a short, actionable tip for the forecast condition. Examples: "Carry an umbrella, heavy rain from 3pm"; "Haze is unhealthy, stay indoors and wear a mask"; "Very hot today, refill your water bottle".
- FR3.2 Tips shall be personalised using the user's profile (for example, an asthma flag means stronger haze advice) and time of day (for example, peak-hour traffic warnings).
- FR3.3 The system shall fall back to a curated rule-based tip library when the AI service is unavailable.

*AI justification:* a fixed rule table can't combine forecast, time, user sensitivity and location into one natural sentence. The LLM does that. The rule library guarantees safe output when the model is offline or wrong.

### M4. Weather-Aware Campus Routing (Daya)
- FR4.1 The user shall be able to pick a start and destination from a list of UM locations, or use their current location.
- FR4.2 The system shall offer route modes: **Fastest**, **Shadiest** (max tree and building shade), and **Driest** (max covered walkways).
- FR4.3 The system shall automatically recommend a mode based on current weather and avoid path segments with active flood or hazard reports.

### M5. Hazard Reporting (Intan)
- FR5.1 The user shall be able to report a hazard (flooded path, fallen tree, blocked drain, other) with a photo, a category and a GPS pin.
- FR5.2 The user shall be able to view active hazard reports on a campus map.
- FR5.3 Reports shall expire automatically after a set time unless other users confirm them.

### M6. User Profile & Settings (Intan, Daniel)
- FR6.1 The user shall be able to sign in (UM email or Google) or continue as a guest.
- FR6.2 The user shall be able to set their usual locations (faculty, residential college) and health sensitivities.
- FR6.3 The user shall be able to switch the app language (English / Bahasa Melayu) and text size.

### M7. Emergency Info (Arieth)
- FR7.1 The user shall be able to view UM emergency contacts (UM Security, campus clinic, JPPHB) and call them in one tap.
- FR7.2 The user shall be able to view safety guidance for each hazard type.
- FR7.3 Emergency info shall be available offline.

### Non-functional requirements (the report allows up to 2)
- **NFR1 Performance:** the home screen shall show cached weather within 2 s of launch and refresh live data within 5 s on a 4G connection.
- **NFR2 Reliability:** weather, tips (rule-based fallback) and emergency info shall stay usable offline using the last cached data, with a visible "last updated" time.

## 4. Tech stack

| Layer | Default choice | Notes |
|---|---|---|
| App | **Android, Java + XML layouts** (Android Studio) | Matches the course labs. |
| Backend | **Firebase**: Auth, Firestore, Storage (hazard photos), Cloud Messaging (push), AI Logic (Gemini), App Check | Free tier is enough. |
| Weather data | **Open-Meteo** Forecast API + Air Quality API (no API key) | UM ≈ lat 3.1209, lon 101.6538. Optionally add MET Malaysia warnings. |
| Maps | Google Maps SDK for Android **or** osmdroid (OpenStreetMap) | Routing runs on our own campus graph (see §5), not the Google Directions API. |
| AI | **Gemini via Firebase AI Logic** for M3 tips | Calls go through our Firebase project, so there is **no Gemini API key** in the app or in `local.properties`. Turn on **Firebase App Check** so only our app can use the quota. Check the free-tier limits. |
| Collaboration | GitHub (one repo, PRs into `main`) + Trello | Commit logs and the Trello board are evidence for the report's contribution section. |

## 5. Routing design (core original work)

Daya's shade idea plus Ammar's bad-weather idea, combined:

- Hand-build a **campus graph**. Nodes are key points (e.g. FSKTM, Main Library, DTC, Perdanasiswa, residential colleges, bus/LRT stops). Edges are walkable path segments.
- Each edge stores `distance_m`, `covered` (bool), `shade_score` (0–1), `flood_prone` (bool) and `traffic_peak` (bool, for roads beside heavy traffic).
- Collect the data by **walking the campus**: photograph covered walkways and shaded stretches. These photos are also stakeholder and problem evidence for the report and video.
- Run **Dijkstra / A\*** on the device with a weather-dependent cost:
  `cost = distance × (1 + w_rain·!covered + w_heat·(1−shade) + w_flood·flood_prone + w_traffic·traffic_peak)`, plus a large penalty on edges with an active hazard report.
- The team designs and tunes the weights itself and documents them in the report. This counts as "significant human contribution" under the AI policy.

## 6. Team & roles

| Member | Project role | Dev ownership | Load during dev sprints |
|---|---|---|---|
| **Zi Hao** | Group leader / Project manager / Lead developer | M1 Weather, M2 Alerts, architecture, integration, releases | Full |
| **Intan** | UI/UX lead / Developer | Design system & Figma, M5 Hazard Reporting, M6 Auth/Profile, Firestore schema | Full |
| **Daya** | Developer (Maps & Routing) | M4 Routing, campus graph data, map screens | Full |
| **Ammar Kapadia** | **Secretary** / Developer | M3 Smart Tips (rules + AI prompt), survey & interviews | Light (minutes, Trello, report compilation) |
| **Daniel Vivian Menezes** | **Video editor** / Tester | M6 Settings & onboarding screens, test cases, test log | Light; heavy in Weeks 11–12 |
| **Arieth Thaqif Bin Arnizzam** | **Narrator** / Developer | M7 Emergency Info, BM translations & accessibility, NFR measurements | Light; heavy in Weeks 11–12 |

Rules from the brief:
- Every member must write code. No one does documentation only.
- Each member needs **≥ 20 hours** of work.
- Each member presents their own part in the video.

Target hours over Weeks 3–10: about **30 h** each for Zi Hao, Intan and Daya; about **20 h** each for Ammar, Daniel and Arieth. Role duties are extra: secretary work for Ammar, and editing in Weeks 11–12 for Daniel and Arieth.

## 7. Timeline

| Milestone | When |
|---|---|
| Group registration (leader) | **By 21 Oct 2026.** The brief says "Sunday 21 October", but 21 Oct 2026 is a Wednesday, so register by Wed to be safe. |
| Development sprints | Weeks 3–10 |
| Code freeze + final APK | End of Week 10 |
| Recording & editing | Weeks 11–12 |
| **Video due** | **12 pm, Sun 3 Jan 2027** (Week 12) |
| **Report due** | **12 pm, Sun 10 Jan 2027** (Week 13) |

Week 3 starts **Mon 19 Oct 2026**. Week 12 ends Sun 3 Jan 2027, which leaves room for one non-teaching week (mid-semester break) in between. Check the UM calendar and use that week as a **buffer**, not a sprint.

## 8. Weekly sprints

Each sprint runs Monday–Sunday.
- **Sunday:** short stand-up. Ammar takes minutes and updates Trello.
- **Every task:** ends in a PR merged to `main`, or a file in `/docs`.
- **Every AI-assisted task:** gets an entry in `AI_USAGE_LOG.md` (see §10).

### Week 3 — Kick-off & problem validation
**Goal:** group registered, stack decided, repo live, problem evidence collection started.

| Member | Tasks |
|---|---|
| Zi Hao | Register the group by 21 Oct. Create the GitHub repo, Trello board and Android project skeleton. Confirm the stack (§4). Test the Open-Meteo forecast + air quality calls for UM. |
| Intan | Gather UI references. Draft the user flow (Home → Forecast → Tips → Route → Report). Set up the Figma file. |
| Daya | Research shade-routing apps and approaches. Draft the list of campus nodes for the graph (around 15–25 locations). |
| Ammar | Set up the minutes template and Trello. Draft the stakeholder survey (rain/heat/haze pain points, current routes, wanted features). **UM unit outreach:** find contacts for 2–3 target units (JPPHB, UM Sustainability Centre, a residential college office). Ask the lecturer for an introduction. Send the meeting-request emails by **Fri**, CC'ing the lecturer, because replies can take 1–2 weeks. |
| Daniel | Read the brief's video rubric and set up the shared folder for footage. Set up the test case template (spreadsheet). |
| Arieth | Collect UM emergency contacts and hazard safety guidance from official sources. Collect news articles on UM floods, fallen trees and haze (for the report intro). |

### Week 4 — Requirements & design
**Goal:** survey out, FRs/NFRs agreed, wireframes and data model done.

| Member | Tasks |
|---|---|
| Zi Hao | Finalise the module list and FRs/NFRs (§3) with the team. Define the app architecture (MVVM, repository layer, caching). Set the weather-state rules (thresholds for Rain/Thunderstorm/Heat/Moderate Haze/Unhealthy Haze). |
| Intan | Make low-fi wireframes for all screens. Define the colour scheme and accessibility (contrast, text size). Write the Firestore schema (users, hazard_reports, tips_cache). |
| Daya | Walk campus routes and photograph covered and shaded segments. Build v1 of the graph data file (`campus_graph.json`). |
| Ammar | Send the survey out (target 30+ UM responses). Follow up on the UM unit emails and **hold at least 1 meeting with a UM unit**, with Zi Hao attending. Ask about flood spots, how hazards are reported today, and whether they would adopt the app. Interview 2–3 students or a college fellow. Save the notes, photos and email threads in `/docs/stakeholders`. |
| Daniel | Turn the FRs into test cases (one or more per FR). |
| Arieth | Write the Bahasa Melayu string list for the main UI labels (draft). |

### Week 5 — Sprint 1: foundations
**Goal:** app runs and shows real UM weather, with navigation between empty screens.

| Member | Tasks |
|---|---|
| Zi Hao | M1: weather repository (Open-Meteo), Home screen with live data, local cache. |
| Intan | Theme/design system in code (colours, typography, components). Bottom navigation. M6: Firebase Auth (Google + guest). |
| Daya | Map screen showing the campus with graph nodes plotted. Load `campus_graph.json`. |
| Ammar | Analyse survey results so far. M3: write the rule-based tip library v1 (≥ 20 tips mapped to conditions). |
| Daniel | M6: onboarding screens (3 slides + permissions). |
| Arieth | M7: Emergency Info screen (static, offline, tap-to-call). |

### Week 6 — Sprint 2: core logic
**Goal:** weather states, basic routing and rule-based tips all work end to end.

| Member | Tasks |
|---|---|
| Zi Hao | M1: hourly forecast screen, weather-state classifier, status banner. M2: alert-check logic (WorkManager background job). |
| Intan | M5: hazard report form (category, photo, GPS) saving to Firestore + Storage. |
| Daya | M4: Dijkstra/A* on the graph, Fastest route drawn on the map, start/destination pickers. |
| Ammar | M3: Tips screen and Home card showing rule-based tips for the current state. Share survey findings with the team. |
| Daniel | Run test cases on M1 and M7 and log bugs as GitHub issues. |
| Arieth | Add BM strings to the app (`strings.xml` in `values-ms`). |

### Week 7 — Sprint 3: AI + smart routing
**Goal:** AI tips live, shade/dry route modes working, push alerts firing.

| Member | Tasks |
|---|---|
| Zi Hao | M2: Firebase Cloud Messaging push notifications, user alert preferences and quiet hours. |
| Intan | M5: hazard map layer (active reports), confirm/expiry logic. |
| Daya | M4: Shadiest and Driest modes with the weighted cost function (§5). Tune the weights. |
| Ammar | M3: Gemini tip generation through Firebase AI Logic (prompt with forecast + profile + time), safety guardrails, fallback to the rule library. Write the prompt design notes for the report. |
| Daniel | M6: Settings screen (alert toggles, language, text size). |
| Arieth | M7: per-hazard safety guidance pages. Run an accessibility check (TalkBack, large text). |

### Week 8 — Sprint 4: integration
**Goal:** modules talk to each other and all major features are complete.

| Member | Tasks |
|---|---|
| Zi Hao | Integration: weather state drives the recommended route mode and the tip content. Handle errors and offline mode (NFR2). |
| Intan | M6: profile (usual locations, health sensitivity). UI polish pass against the Figma designs. |
| Daya | M4: auto-recommend route mode from the weather. Avoid hazard-reported edges. Add a "why this route" explanation chip. |
| Ammar | M3: personalisation (asthma → haze, peak hours → traffic tip). Start the report outline and collect each member's sections. |
| Daniel | Full regression run of the test cases. Track bugs. |
| Arieth | Draft the NFR measurement plan (how to time launch and refresh, offline test steps). |

### Week 9 — Feature freeze, testing & user testing
**Goal:** no new features after this week. App tested by real UM users.

| Member | Tasks |
|---|---|
| Zi Hao | Fix bugs from the test log. Release v0.9 APK for user testing. Run the user testing session (target 8–10 UM students). |
| Intan | Help run user testing and collect usability feedback (SUS questionnaire). Fix UI issues. |
| Daya | Field-test the routes on campus (walk 3 routes per mode and record times). Fix routing bugs. |
| Ammar | Evaluate AI tips: rate 30 generated tips for usefulness and safety and record the results. Collect testimonials from testers. **Go back to the UM unit** with the v0.9 prototype (meeting or short demo video by email) and get 1–2 lines of feedback or a quote for the video's Stakeholder Validation section. |
| Daniel | Re-test fixed issues. Record raw screen captures of each feature (early B-roll for the video). |
| Arieth | Run the NFR measurements (launch time, refresh time, offline behaviour) and log the results. Draft the video script outline (following the rubric's 8 sections). |

### Week 10 — Polish & code freeze
**Goal:** final APK built, demo data ready, script locked. **Development ends this week.**

| Member | Tasks |
|---|---|
| Zi Hao | Final bug fixes, tag `v1.0`, build the release APK. Prepare demo scenarios (e.g. "rain at 3pm, FSKTM → Main Library"). Prepare an architecture diagram for the video. |
| Intan | Final UI polish and screenshots for the report. Write the design rationale (colours, layout, accessibility). |
| Daya | Write up the routing algorithm and weights (with a diagram) for the report and video. |
| Ammar | Finalise the AI Usage Log. Merge report section drafts. |
| Daniel | Final test pass and sign-off. Plan the recording schedule and shot list with Arieth. |
| Arieth | Finalise the video script and assign each member their segment (≈ 1.5–2 min each). |

### Week 11 — Recording (everyone) & report drafting
**Goal:** all footage recorded by Wednesday. Editing underway.

| Member | Tasks |
|---|---|
| **Everyone** | Record your own segment (your module + contribution) by **Wed**. Record on campus where it fits (rain/shade route demo). |
| Daniel | **Editing:** rough cut and screen-recording assembly. |
| Arieth | **Narration:** record voiceover for the intro, transitions and closing. **Editing** with Daniel. |
| Zi Hao | Report: Architecture & Design, System Implementation. Review the rough cut. |
| Intan | Report: UI/UX design and screenshots, Testing & Validation (with Daniel's test log). |
| Daya | Report: routing implementation, Impact/Limitations/Future Work. |
| Ammar | Report: Introduction, User Study findings, Team Roles (from Trello and Git logs), AI Usage Log appendix. |

### Week 12 — Final edit & video submission
**Goal:** video submitted by **12 pm, Sun 3 Jan 2027**.

| Member | Tasks |
|---|---|
| Daniel & Arieth | Final edit: captions, audio levels, length check (**10–15 min**). Export. Aim for team review by **Thu**. |
| Zi Hao | Team review, then submit the video (leader). |
| Intan, Daya, Ammar | Finish the report draft (≤ 15 pages, ≤ 2,500 words, 12-pt Times New Roman, double spaced). |

### Week 13 — Report submission
Ammar compiles and proofreads, everyone reviews, and Zi Hao submits by **12 pm, Sun 10 Jan 2027**. Then everyone completes the peer evaluation.

## 9. Repo conventions

```
/app                 Android app module (created by Android Studio)
/docs                survey results, minutes, test log, NFR results, diagrams
/docs/PROJECT_PLAN.md  this document
/data/campus_graph.json
AI_USAGE_LOG.md
README.md
CLAUDE.md            (contains: @docs/PROJECT_PLAN.md)
```

- Branches: `feature/<module>-<short-desc>`, e.g. `feature/m4-shade-route`. PR into `main` with at least 1 reviewer.
- Commits: `M4: add shade weight to edge cost`. Commit under **your own** GitHub account; the commit history is contribution evidence.
- Java style: standard Java conventions (Google Java Style). One ViewModel per screen (Android `ViewModel` + `LiveData`). Network/data access only through repositories.
- Secrets: Gemini needs no API key (it goes through Firebase AI Logic). Any other API keys go in `local.properties` (git-ignored). Never hard-code them.

## 10. Academic integrity & AI usage (read this, Claude)

This assignment is at **UM AI policy Level 2 (limited use)**. AI may help brainstorm, explain, refine and debug. The core design and problem-solving must be the team's own. The submission must include an **AI Usage Log** with the tool used, the prompts, and how the output was used, tested and modified.

**Instructions for Claude Code in this repo:**
1. Before writing non-trivial code, explain the approach and ask which team member is working on it. Prefer guiding them and reviewing their code over generating whole modules.
2. Do not invent the routing cost weights, weather thresholds or FR/NFR wording. Propose options and let the team decide.
3. After every session that produced code or design used in the project, append an entry to `AI_USAGE_LOG.md`:
   ```
   ## YYYY-MM-DD — <member name> — <module>
   - Tool: Claude Code
   - Prompt(s): <what was asked, verbatim or close to it>
   - Output used: <files / functions affected>
   - Verification: <how it was tested, what was changed by the member>
   ```
4. Keep explanations short enough that the member can present and defend the code in the video.
5. Never commit API keys or personal data from survey responses.

## 11. Open decisions

- [ ] Final app name (CuacaUM is a placeholder)
- [x] Kotlin + Compose vs Java + XML → **Java + XML**
- [ ] Google Maps SDK vs osmdroid
- [x] Which LLM API for tips → **Gemini via Firebase AI Logic**
- [ ] Which UM unit to approach for validation (JPPHB, UM Sustainability Centre, a residential college). This earns extra credit.
