---
name: android-finish
description: Finish a requested change in the KITT launcher end to end. Inspect the repo, implement, run unit tests and lint, build the debug APK, fix and rerun failures, review the diff, and report what was verified versus what still needs the head unit or vehicle.
disable-model-invocation: true
argument-hint: "[change to finish]"
---

# /android-finish

Finish this change: $ARGUMENTS

If no change was given, finish the work in progress: the uncommitted diff plus the current conversation's request. Follow the root `CLAUDE.md`, especially "How to work on this project". This skill doesn't override it.

## 1. Inspect

- Run `git status --short` and `git diff --stat`. Note which uncommitted changes aren't part of this task and leave them alone.
- Read the code you'll touch, plus `gradle/libs.versions.toml`, `app/build.gradle.kts` and `app/src/main/AndroidManifest.xml` if the change involves dependencies, permissions or services.
- Check for web-wrapper files (`capacitor.config.*`, `package.json`). None exist in this repo today; it's native Kotlin/Compose.

## 2. Implement

- Make the complete change in source files. Never edit `app/build/`, `build/`, `.gradle/`, `.kotlin/` or generated KSP output.
- Add or extend JVM unit tests in `app/src/test/java/com/aiconsole/knightriderlauncher/` for any plain-Kotlin logic (parsers, formulas, formatters). These tests can't use `org.json` or Android framework classes.
- Never add or trigger vehicle write commands (Mode 04, ECU writes) unless the owner explicitly asked for them.

## 3. Capacitor sync (only if necessary)

Run a sync only if a `capacitor.config.*` exists **and** this change touched web assets or Capacitor plugins/config. That means `npx cap sync android` from the directory that holds the config. Otherwise, skip it and say it was skipped because the repo has no Capacitor layer, or the change didn't touch it.

## 4. Checks and build

Use the PowerShell tool from the repo root. Gradle needs Android Studio's JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

- Unit test reports: `app/build/reports/tests/testDebugUnitTest/`
- Lint text report: `app/build/intermediates/lint_intermediate_text_report/`
- APK: `app/build/outputs/apk/debug/app-debug.apk` (confirm it exists and its timestamp is from this run)
- Instrumented tests (`app/src/androidTest`) only if `adb devices` lists a device:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat connectedDebugAndroidTest
  ```
  Otherwise, report them as not run.

If a check fails:
1. Read the actual error.
2. Fix the root cause in source. Don't weaken, skip or delete tests, and don't add lint suppressions just to go green.
3. Rerun the failing task, then the full command above once more before finishing.

Lint warnings that predate this change don't block the run, but new errors do. Mention any new warnings.

## 5. Review the final diff

Run `git diff` (plus `git status --short` for new files) and check that:
- only intended files changed, and the owner's unrelated changes are intact;
- nothing got left behind: debug logging, commented-out code, hardcoded keys or secrets (API keys belong in `SecureStore`/`local.properties`);
- SDK-gated APIs and permissions are guarded, and permission-denied, adapter-off and disconnected paths show an error instead of crashing;
- `CancellationException` is rethrown before broad catches, and IO work runs on `Dispatchers.IO`;
- source files have no mojibake in `°`, `·`, `…` (never rewrite them via PowerShell `Get-Content`/`Set-Content`).

Fix anything you find, then rerun step 4.

Once everything passes, commit without asking. Stage only the files this task changed by explicit path (never `git add -A`), and leave the owner's unrelated changes unstaged. Don't push unless asked.

## 6. Report

End with exactly these sections, kept short:

- **Changed:** files and what each change does, plus the commit hash.
- **Verified:** each command that ran and its real result (tests passed/failed with counts, lint errors, build result). Also say whether Capacitor sync or the instrumented tests were skipped and why.
- **APK:** the full path, or "not built" and why.
- **Not verified, needs hardware:** only the specific on-device steps this change needs. For example: "On the head unit, connect the BLE adapter, open Diagnostics, confirm RPM updates." For OBD changes, name the layer being tested: transport (Classic/BLE/Wi-Fi), adapter connection, ELM init, ECU response or PID decoding.

A green build and passing JVM tests prove the code compiles and the pure-Kotlin logic works. They say nothing about Bluetooth, the adapter, the ECU or head-unit behavior, so never phrase them as if they do.
