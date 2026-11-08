# AI Usage Log

UM AI policy Level 2 (limited use). One entry per AI-assisted session that produced code or design used in the project (see README §10).

## 2026-11-08 — Zi Hao — M1 Weather (WeatherRepository)
- Tool: Claude Code
- Prompt(s): "What am I missing out in sprint week 5?"; "what exception should I catch for response.body"; "What's wrong with usaqi?"; "is my backend done now?"; "where should I add permissions in manifest?"; "help me do 1 to 3" (fix the missing `return` after the forecast error check, show weather when the AQI call fails, clean up the `usAqi` array / redundant returns / `cahce` typo).
- Output used: Review comments and explanations (Retrofit `onResponse` vs `onFailure`, async `enqueue` ordering bug, separate air-quality host). Zi Hao wrote the AQI call, the `temperature` field, `AQI_FIELDS` and the INTERNET permission without AI-written code, based on those explanations. Claude edited `data/repository/WeatherRepository.java`: `return` after the forecast error check, new `publish(...)` and `fallbackAqi()` helpers, `AQI_UNAVAILABLE = -1` constant, removed the `int[]` workaround and redundant returns, fixed the `cahce` typo.
- Verification: `./gradlew :app:compileDebugJavaWithJavac` passes. Not yet run on a device (Retrofit instances and ViewModel still to be written).
