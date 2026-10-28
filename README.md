# KITT Launcher.2

A Knight Rider (KITT) themed Android home-screen launcher for aftermarket car head units, built
for **7" to 10" landscape** screens (1024x600, 1280x720, 1280x800 and similar). It is the
landscape, small-screen edition of the original 12.1" portrait KITT Launcher, with a lighter
footprint for cheap head units. It includes:

- an ELM327 OBD-II car scanner (gauges, fault codes, readiness, custom PIDs, data logging);
- KITT, a voice and text AI co-pilot (Gemini or Perplexity) that sees live vehicle data;
- map search with spoken turn-by-turn directions (OpenStreetMap, no account);
- a tabbed browser for streaming, AI chat sites and browser games;
- the always-on red scanner light and ten themes.

See [FEATURES.md](FEATURES.md) for everything it does.

## What's different from the original

- **Landscape shell:** a side rail (KITT plus the page buttons) and a slim one-row dock replace
  the page tabs and the tall dock, so the pages get nearly the whole height of a short screen.
  The KITT panel splits into controls on the left and the conversation on the right.
- **Screen size setting** (KITT Config → Display): Auto fits 480–800 dp tall screens; Compact,
  Normal and Large override it.
- **Lite mode** (Auto, Lite, Full): on cheap units (3 GB RAM or less, or four cores or fewer) the
  browser keeps fewer pages loaded, the scanner and its overlay draw at 30 fps, idle animations
  stop and the Matrix rain is off. Every feature still works.
- **Lighter browser everywhere:** hidden silent pages pause, the least recently used hidden
  tabs are freed (and reload when opened), and every hidden page is freed when Android runs
  low on memory. Playing pages keep playing.
- **Savage KITT**, the new default personality: even more sarcasm and swearing than Unfiltered,
  with the same accuracy and safety rules. Unfiltered and Classic are still there.
- Installs alongside the original (`com.aiconsole.kittlauncher2`).

## Editions

One codebase builds four apps (product flavors), each installing side by side:

| Edition | Flavor | App ID | For |
|---|---|---|---|
| KITT Launcher.2 | `kitt2` | `com.aiconsole.kittlauncher2` | 7"–10" landscape |
| KITT Launcher | `kitt` | `com.aiconsole.knightriderlauncher` | 12.1" portrait (the original) |
| KITT Holo Landscape | `holoLandscape` | `com.aiconsole.kittdual.landscape` | landscape, redesigned cockpit |
| KITT Holo Portrait | `holoPortrait` | `com.aiconsole.kittdual.portrait` | portrait, redesigned cockpit |

**KITT Holo** is the same launcher with a redesigned cockpit: a spinning hologram of the car
with live data on it, film-themed Drive pages, a trip-computer dock, icon keys and a CRT-style
KITT panel. Its redesign is switched on by `BuildConfig.HOLO_DESIGN`, so the classic editions
look and behave exactly as before; its car art lives in `app/src/holo` so they don't carry it.

All editions share the gauges: eleven faces (Dial, LED bar, Digital, Graph, Needle, Nixie,
Skyline, HUD tape, Scope, Halo, Film HUD), a live style gallery, peak hold and a Cluster layout.

## Building

Native Kotlin and Jetpack Compose, single `:app` module. Android 7.0+ (minSdk 24), target SDK 37.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # Android Studio's bundled JDK
.\gradlew.bat testKitt2DebugUnitTest assembleKitt2Debug assembleKittDebug
.\gradlew.bat testHoloLandscapeDebugUnitTest assembleHoloLandscapeDebug assembleHoloPortraitDebug
```

Debug APKs land in `app/build/outputs/apk/<flavor>/debug/`.

- AI keys are entered in the app (KITT Config) and stored encrypted on the device. Optionally put
  `GEMINI_API_KEY=...` in `local.properties` as a development fallback; that file is never committed.
- Signed release builds come from the GitHub workflow when the `KITT_KEYSTORE_BASE64`,
  `KITT_KEYSTORE_PASSWORD`, `KITT_KEY_ALIAS` and `KITT_KEY_PASSWORD` repository secrets are set.
  Pushing a `v*` tag publishes the signed APK as a release for the in-app updater.

## Licence

Copyright © 2026 travisdater. All rights reserved. This is a personal project, provided as-is
with no support.
