# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## How to work on this project

This is the owner's personal project, and they vibe code it. Deliver complete, working solutions with as little back-and-forth as possible.

- **Look before changing:** read the relevant code, `gradle/libs.versions.toml` and `AndroidManifest.xml` first. Don't assume the structure matches older notes, this file included; if they disagree, the code wins.
- **Decide routine things yourself.** Ask only when you're blocked, or before a risky or irreversible action.
- **Preserve what works:** don't regress existing features, and never discard, overwrite or commit the owner's unrelated uncommitted changes.
- **Edit sources, not generated output:** never hand-edit `app/build/`, `build/`, `.gradle/`, `.kotlin/` or KSP output. The project has no Capacitor or `node_modules`, so don't add web-wrapper layers.
- **Android versions:** gate permissions and APIs by SDK level (minSdk 24). Bluetooth is `BLUETOOTH`/`BLUETOOTH_ADMIN` below API 31 and `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` from API 31. Location is needed for scans on older versions. `POST_NOTIFICATIONS` applies from API 33, and foreground-service types from API 34. Handle denied permissions, a disabled adapter and missing hardware with a visible error, not a crash.
- **OBD diagnosis:** name the failing layer and don't blur them together:
  1. Bluetooth Classic (SPP/RFCOMM), BLE (GATT), or the Wi-Fi transport;
  2. adapter connection (socket or GATT link up);
  3. ELM327 initialization (`AT` commands, `>` prompt, protocol detect);
  4. ECU response (`NO DATA`, `?`, CAN errors, headers);
  5. PID decoding (parser and formulas).

  The owner's adapters are a Veepeak OBDCheck BLE and an OBDAi Gen-1 (likely Classic). The OBDAi connects on the head unit in Torque Pro and Car Scanner Pro, so the head unit supports Classic SPP. If our Classic connect fails there, treat it as an app bug; don't blame the hardware.
- **Vehicle safety:** never clear DTCs, change ECU settings or send any vehicle write command (Mode 04, Mode 08, writes to Mode 2E/2F/31/3B, etc.) unless the owner explicitly asks for that operation. The existing user-initiated `ObdManager.clearCodes()` stays behind its explicit UI action. Never call it automatically, from the assistant, or during testing, and don't add new write paths unasked.
- **Verify before calling it done:** after implementing, run `testDebugUnitTest` (plus any new tests) and `assembleDebug`. Fix failures and rerun until both pass. A successful build or passing tests is not proof of a live adapter, ECU or head-unit behavior, so never claim otherwise.
- **Commit without asking:** once the checks pass, commit your own changes. Stage only the files you changed, never the owner's unrelated uncommitted changes, and don't push unless asked.
- **Final report:** end with:
  - what changed;
  - the actual verification results (commands run, pass/fail);
  - the APK path if one was built (`app/build/outputs/apk/debug/app-debug.apk`);
  - only the specific on-device test the owner needs to do.

## What this is

A Knight Rider (KITT) themed Android **home-screen launcher** for aftermarket car head units, primarily a 12.1" portrait (9:16) Tesla-style unit. It includes:
- an ELM327 OBD-II scanner (Car Scanner-style gauges, codes and custom PIDs);
- a voice/text AI copilot ("KITT") that sees live vehicle data;
- an always-on red scanner light.

Single `:app` module, Jetpack Compose, package `com.aiconsole.knightriderlauncher`.

## Build / test commands (Windows, PowerShell)

Gradle needs a JDK; use Android Studio's bundled JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat testDebugUnitTest --tests "com.aiconsole.knightriderlauncher.ObdProtocolTest"   # single class
.\gradlew.bat lintDebug    # text report: app/build/intermediates/lint_intermediate_text_report/
```

CI: `.github/workflows/android.yml` runs tests, lint and the debug build; with signing secrets (`KITT_KEYSTORE_BASE64` etc.) it also builds a signed release, published on `v*` tags for the in-app updater. CI sets `versionCode` from the run number.

Toolchain: AGP 9.x with **built-in Kotlin** (no `kotlin-android` plugin), Compose BOM, KSP 2.3.x (decoupled from Kotlin version) for Room, OkHttp 5. Versions live in `gradle/libs.versions.toml`. minSdk 24, target/compile 37.

Don't rewrite source files with PowerShell `Get-Content`/`Set-Content`. Windows PowerShell 5.1 reads them as ANSI and corrupts `°`, `·`, `…`. Use the editor tools.

## Architecture

**Service locator:** `KittApplication` holds lazy app-wide singletons:
- `settings`
- `tripDatabase`
- `tripRecorder`
- `gauges`
- `customPids`
- `obd`
- `kittVoice` (the single shared TTS engine)
- `alerts`
- `assistantRepository`
- `assistantInbox` (questions queued for KITT from other screens)

There's no DI framework. Composables that need them do `LocalContext.current.applicationContext as KittApplication`.

**UI shell** (`MainActivity.kt`):
- **`LauncherRoot`:** a permanent `KittScanner` strip (doubles as a shift light), then a `BoxWithConstraints` holding a `HorizontalPager` of `HomePage`s, page tabs, and the fixed dock.
  - **Drive:** tall screens (height ≥ 1.2× width) stack map 50% / grid 35%; wide screens put them side by side. The grid shows pinned favourites, else all non-hidden apps (`AppLists`).
  - **Gauges / Media / Apps:** `GaugeWallPane`, `MediaPane` (active `MediaSession` via `MediaBridge`, needs notification access), `AppDrawerPane` (search, A–Z rail, pin/hide).
  - **Stream window:** `stream/StreamSession` + `ui/panes/StreamWindow` put web video players (YouTube, YouTube Music, or any site typed in) in a WebView over the map pane only.
    - Non-web schemes are dropped so apps can't take over the screen.
    - Desktop-UA mode (default) gives full desktop players; site fullscreen stays inside the pane.
    - Userscripts (`stream/Userscripts`, `UserscriptStore`) inject at document start via `androidx.webkit`; `.user.js` links prompt to install. Chrome extensions aren't possible in WebView.
    - Site picker (`StreamSites`): built-ins are licensed free services only (don't add piracy sites); the owner's saved sites are theirs to choose.
    - Video plays while moving by the owner's explicit choice; don't add a driving gate.
  - **Swipe:** disabled on Drive while the map or stream needs horizontal drags; Home returns to Drive.
  - **More:** `DrivingPanel` (driving layout while moving), `QuickSettingsPanel` (tap the status strip), `WeatherService` (Open-Meteo + spoken road warnings), messages read aloud via `NavNotificationListener` + `MessageRules`, `DriverProfiles` (personal vs shared keys in `ProfileRules`), `SelfUpdate` (GitHub releases, `PackageInstaller`).
  - **Extras:** `StatusStrip` (clock/heading/radios/OBD/volts), GPS speed fallback (`GpsTracker`), `Screensaver` when parked and idle, `WidgetsPane` (`AppWidgetHost` in `KittApplication`), `StartupRoutine` (on start or wake after 5+ min), `HardwareKeys` button mapping via `onKeyUp`, `LauncherBackup` (API keys excluded on purpose).
- **Overlays:** full-screen features are selected by the `LauncherOverlay` enum and drawn inside that box, below the scanner. The scanner is always visible. Each overlay swallows taps and registers its own `BackHandler`.
  - `Assistant`
  - `Diagnostics`
  - `Config`
  - `Trips`
- **Live dash state:** `rememberDashState` collects the OBD, gauge and alert flows into `DashState`.
- **No recreation on rotation:** the manifest `configChanges` means the activity isn't recreated, and some state relies on that.
- **Voice button:** `VOICE_COMMAND`/`ASSIST` intents (steering-wheel voice button) bump `voiceTrigger`, which opens the assistant with `autoListen`.
- **Services start from the activity:** foreground services must start while the activity is visible, so `MainActivity.onStart` starts the OBD service and the scanner overlay, and triggers the greeting.

**OBD (`obd/`)**, layered bottom-up:
- **Transports:** `ObdTransport` implementations (Classic SPP with an RFCOMM channel-1 fallback, BLE GATT with a notify/write characteristic search, Wi-Fi TCP) all push bytes into a `Channel`.
- **`Elm327`:** commands end at the `>` prompt. Use `exclusive { }` to group a header switch plus request so the poll loop and on-demand calls never interleave.
- **`ObdResponseParser`** (pure Kotlin): ISO-TP multi-frame, multi-PID splitting, DTC count byte on CAN only, VIN.
- **`ObdManager`:** session and reconnect loop.
  - **Polling:**
    - demand-driven: only PIDs registered via `watch(key, ids)` plus `ALWAYS_POLLED`;
    - tiered: `fast` PIDs every cycle;
    - CAN multi-PID batching (6 per request), falling back to single requests automatically;
    - ELM response-count suffix (`010C1`), disabled on `?`.
  - **Headers:** CAN polling targets the engine header `7E0`; code reads switch to broadcast `7DF`.
  - **Units and logging:** values are stored in **metric base units** and converted with `Quantity.toDisplay`. Readings feed `TripMetricRecorder` at 1 Hz.
- **`ObdConnectionService`:** just holds the foreground lifetime (type `connectedDevice`).
- **Diagnostics tools** (all read-only): `ReadinessDecoder`, `Mode06`, `ModuleScan`, `PerformanceTimer`, `BatteryHealth`, `VinDecoder` + NHTSA lookup, maintenance/fuel (`VehicleService`). The raw console goes through `ConsoleSafety`'s allow-list; keep it read-only. `ConnectionLog` tags events by `LinkLayer`. Saved adapters (`AdapterProfiles`) are tried in rotation.

**PIDs:**
- **`PidDefinition`** covers standard Mode 01, manufacturer (`ManufacturerProfiles`, detected from the VIN WMI prefix, longest match wins) and custom (`CustomPidStore`) PIDs.
- **Formulas:** Torque-style strings (`((A*256)+B)/4`, `signed()`) compiled and cached by `FormulaEvaluator`.
- **Community-sourced PIDs:** manufacturer PIDs are community-sourced and each carries a `note`. Don't add ones you can't source.

**Storage:**
- **Gauges and custom PIDs:** JSON in SharedPreferences (`GaugeStore`, `CustomPidStore`), not Room. Room only holds `TripMetricLog`.
- **Room migrations:** Room uses `exportSchema = false`, so any schema change needs a hand-written `Migration`.

**Assistant (`assistant/`):**
- **Routing:** `AssistantRepository` builds the vehicle context (`ObdManager.vehicleContext()` + 30-day Room baseline via `VehicleContextFormatter`). It routes to `GeminiAssistantClient` or `PerplexityAssistantClient` (Agent API `/v1/agent`; the old Sonar endpoint is retired).
- **Diagnostic questions:** `KittPersona.isDiagnostic` sends them to the diagnostics model.
- **Gemini client resilience:**
  - retries without `google_search` when a free-tier key refuses grounding;
  - swaps a retired model (404) for the newest same-tier model from `listModels` and persists it.
- **Errors:** clients return the sealed `AssistantResult` instead of throwing.
- **Persona:** `KittPersona` holds the personality (`PersonaStyle.Unfiltered`, the default and the owner's explicit choice: crude, sarcastic and profane on every reply; or `Classic`) plus non-negotiable accuracy and driving-safety rules. `KittAlerts` has matching spoken variants.
- **Keys:** API keys are entered in-app and encrypted with an Android Keystore AES-GCM key (`SecureStore`). `BuildConfig.GEMINI_API_KEY` from `local.properties` is only a dev fallback.
- **Voice commands:** `VoiceCommands` (pure parser) is tried before the AI; `LauncherCommandRunner` runs matches and sends home-screen changes over `LauncherBus`. `WakeWordListener` ("Hey KITT") restarts the recognizer in short sessions and pauses while an overlay or KITT's voice is active.
- **Problem reports:** `ProblemReport` installs a crash handler (files in `filesDir/crashes`) and writes a text report (crashes, connection log, settings without keys, own logcat) from KITT Config.
- **Subscriptions:** consumer subscriptions (Gemini/Claude/Perplexity Pro) can't be used via API; `AiAppHandoff` sends questions to those official apps instead.

**Scanner (`ui/scanner/`):**
- **`ScannerEngine`:** pure-Kotlin light simulation (eased head plus exponential lamp decay) shared by the Compose `KittScanner` and the overlay's plain `View`.
- **`ScannerOverlayService`:** a non-touchable `TYPE_APPLICATION_OVERLAY` at alpha 0.8, the Android 12+ pass-through-touch limit. It hides while `LauncherVisibility.visible` is true.

## Conventions

- **KITT visual language:** new UI uses `KittPanel` framing, uppercase labels, palette constants (`ScannerRed`, `DashAmber`, `LedGreen`, `KittBlack`…) and Canvas-drawn indicators (`SegmentDisplay`, `LedBarGauge`, `VoiceModulator`). There is intentionally no material-icons dependency.
- **Shared controls:** reusable controls live in `ui/components/KittControls.kt` (`KittTextField`, `KittToggle`, `ChoiceRow`, `SectionHeader`).
- **Lazy lists:** wrap multiple composables inside a `LazyColumn` `item {}` in a `Column`, otherwise they overlap.
- **Animations:** read animated values inside draw lambdas, so frames only redraw instead of recomposing.
- **Threading:** network, DB, Keystore and IO work goes through `Dispatchers.IO` or Room suspend functions. Always rethrow `CancellationException` before a broad `catch`. `SpeechRecognizer` must be driven on the main thread.
- **Unit tests:** JVM tests can't use `org.json` or Android framework classes. Keep protocol and formatting logic in plain-Kotlin objects (`ObdResponseParser`, `FormulaEvaluator`, `DtcDecoder`, `VehicleContextFormatter`, `ScannerEngine`), which is what the tests cover.
