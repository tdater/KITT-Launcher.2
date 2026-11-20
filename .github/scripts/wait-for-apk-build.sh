#!/usr/bin/env bash
# Waits for the background APK build started earlier in the smoke job, then fails with its log if
# it failed. The emulator runner runs each script line in its own shell, so this lives in a file.
set -euo pipefail
exit_file="$RUNNER_TEMP/apk-build.exit"
log_file="$RUNNER_TEMP/apk-build.log"
waited=0
until [ -f "$exit_file" ]; do
  if [ "$waited" -ge 1800 ]; then
    echo "The APK build did not finish within 30 minutes."
    tail -n 100 "$log_file" || true
    exit 1
  fi
  sleep 5
  waited=$((waited + 5))
done
echo "APK build finished after waiting ${waited}s here."
if [ "$(cat "$exit_file")" != 0 ]; then
  echo "The APK build failed:"
  cat "$log_file"
  exit 1
fi
tail -n 5 "$log_file"
