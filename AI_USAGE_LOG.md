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
- Verification: `./gradlew :app:compileDebugJavaWithJavac` passes. Later built and run in Android Studio by Zi Hao: live weather data did not work (the Home screen didn't update).

## 2026-11-12 — Zi Hao — M1 Weather (Forecast screens, FR1.2)
- Tool: Claude Code
- Prompt(s) (summarised): build `forecast_fragment.xml` from mockup 4.1; add five forecast columns with time, temperature, feels-like and an emoji from the existing strings; "why didn't the values change in home fragment"; "help me code this loop" and make the current hour bold; start the app on Home after login; Back returns to Home; AQI and rain cards (with humidity and thunderstorm check, using `WeatherClassifier`); horizontal RecyclerView for 24 hours; date under the time; a new daily forecast fragment (7 days) switched with the Hourly/Daily buttons; daily average temperature and feels-like; tappable hour/day columns that update the cards; hourly and daily AQI; 😊 for good air; AQI summary line. Also asked for explanations: `SimpleDateFormat`, Logcat, why feels-like is higher than the temperature, how the RecyclerView and click listeners work, what the daily numbers mean.
- Output used:
  - Claude wrote: `ui/forecast/ForecastFragment.java`, `ui/forecast/DailyForecastFragment.java`, `ui/forecast/ForecastHourAdapter.java`, `ui/forecast/ForecastDayAdapter.java`, `data/model/HourlyWeather.java`, `data/model/DailyWeather.java`
  - Claude wrote: `layout/forecast_fragment.xml` (first version), `layout/item_forecast_column.xml`, `layout/daily_forecast_fragment.xml`
  - Claude edited: `data/repository/WeatherRepository.java` (hourly, daily and hourly-AQI requests; `publishHourly`, `publishDaily`, `toAqiMap`, `aqiAt`, `worstAqiOfDay`), `data/model/ForecastResponse.java`, `data/model/AirQualityResponse.java`, `data/remote/OpenMeteoApi.java`, `data/remote/OpenMeteoAirQualityApi.java`, `ui/home/WeatherViewModel.java` (`getHourly`, `getDaily`)
  - Claude edited: `MainActivity.java` (Back handler that returns to Home)
  - Bug fix found by Claude: the space in `CURRENT_FIELDS` (`" weather_code"`) made Open-Meteo return HTTP 400, so Home never updated
  - Strings added by Claude: `forecast_*`, `emoji_*`, `emoji_air_good`
  - Bottom nav with no tab highlighted on Home: explanation only, no code written by Claude. Claude explained why the icon disappeared with `setGroupCheckable(0, false, true)` (Forecast stayed checked, so its navy "selected" icon colour showed on the navy bar without the yellow pill) and suggested unchecking every tab instead. Zi Hao wrote the change in `MainActivity.java`.
- Design decisions by Zi Hao: screen layout (mockup 4.1); Hourly/Daily buttons; current hour first and 24 hours ahead; daily average temperature and average feels-like instead of max/min; daily AQI = worst hour of the day; rain/AQI cards follow the tapped column; 😊 for good air; AQI summary wording. All thresholds come from the existing `WeatherClassifier` (no new ones).
- Changed by member: Zi Hao edited `forecast_fragment.xml` (Hourly/Daily buttons, title, colours), `MainActivity.java` (opens `HomeFragment` after login and Forecast on reselect), and wrote the `aqicard_*` strings. <add any further changes you make after reviewing>
- Verification: Open-Meteo responses checked with `curl` (hourly window, daily means, hourly AQI). Built and run in Android Studio by Zi Hao; the hourly and daily forecast screens work, the Hourly/Daily buttons switch between them, and tapping each column works. Bottom nav: on Home no tab is highlighted, and tapping a tab highlights it again (tested by Zi Hao).
