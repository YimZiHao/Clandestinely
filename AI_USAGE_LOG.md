# AI Usage Log

UM AI policy Level 2 (limited use). One entry per AI-assisted session that produced code or design used in the project (see README §10).

## 2026-11-08 — Daniel — M6 Onboarding
- Tool: Claude Code
- Prompt(s): "Do what I am required to do for Week 5 | Daniel | M6: onboarding screens (3 slides + permissions). Follow Intan's style but change up some things that look ugly (for example the fonts)... keep it simple, do not use AI-generated icons, use public repo icons."
- Output used:
  - `ui/onboarding/OnboardingActivity.java`, `ui/onboarding/OnboardingAdapter.java`
  - `layout/activity_onboarding.xml`, `layout/item_onboarding_slide.xml`
  - `values/strings_onboarding.xml`, `values/styles_onboarding.xml`
  - `drawable/ic_onb_*.xml` (Iconoir icons, MIT licence, converted from the public repo's SVGs), `drawable/bg_onb_circle.xml`, `drawable/onb_dot.xml`
  - `AndroidManifest.xml` (onboarding is now the launcher; location + notification permissions)
  - ViewPager2 dependency in `gradle/libs.versions.toml` and `app/build.gradle.kts`
- Design input: Intan's Figma Welcome page (colours, pill buttons) and flow board ("location denied → choose location manually"). Font changed to Android's built-in sans-serif on these screens only.
- Verification: built with `./gradlew assembleDebug`; unit tests pass. Tested on the emulator (API 37):
  - fresh install shows the slides; swipe, Next, dots and Skip work;
  - both Allow buttons show the system dialog and change to "Allowed ✓";
  - Get started opens sign-in, also with both permissions denied;
  - Back on the permissions page returns to the slides;
  - relaunching skips onboarding.
- Changed by member: <fill in any changes you make after reviewing>

## 2026-11-08 — Zi Hao — M1 Weather (WeatherRepository)
- Tool: Claude Code
- Prompt(s): "What am I missing out in sprint week 5?"; "what exception should I catch for response.body"; "What's wrong with usaqi?"; "is my backend done now?"; "where should I add permissions in manifest?"; "help me do 1 to 3" (fix the missing `return` after the forecast error check, show weather when the AQI call fails, clean up the `usAqi` array / redundant returns / `cahce` typo).
- Output used: Review comments and explanations (Retrofit `onResponse` vs `onFailure`, async `enqueue` ordering bug, separate air-quality host). Zi Hao wrote the AQI call, the `temperature` field, `AQI_FIELDS` and the INTERNET permission without AI-written code, based on those explanations. Claude edited `data/repository/WeatherRepository.java`: `return` after the forecast error check, new `publish(...)` and `fallbackAqi()` helpers, `AQI_UNAVAILABLE = -1` constant, removed the `int[]` workaround and redundant returns, fixed the `cahce` typo.
- Verification: `./gradlew :app:compileDebugJavaWithJavac` passes. Not yet run on a device (Retrofit instances and ViewModel still to be written).
