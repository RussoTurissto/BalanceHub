#!/usr/bin/env bash
# Keep Android emulator reports and winter previews even if instrumentation fails.
set -uo pipefail

api="${1:?Android API level is required}"

# Android 15 can return from boot to a background window with no input focus.
# Put the launcher in the foreground before instrumentation opens MainActivity.
if [[ "$api" == "35" ]]; then
  adb shell wm dismiss-keyguard || true
  adb shell input keyevent KEYCODE_HOME || true
fi

test_status=0
./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest || test_status=$?

# A previous API 35 run passed all 52 tests, but another run lost window focus
# in seven unrelated Espresso tests. Retry once only for this known emulator
# transient; retain the failed attempt for review rather than hiding it.
results_dir="app/build/outputs/androidTest-results/connected/debug"
if [[ "$api" == "35" && "$test_status" -ne 0 ]] \
    && [[ -d "$results_dir" ]] \
    && grep -Rql 'RootViewWithoutFocusException' "$results_dir"; then
  echo '::warning::Android 15 Espresso lost window focus; preserving first attempt and retrying once'
  mkdir -p app/build/emulator-focus-retry
  cp -a "$results_dir" app/build/emulator-focus-retry/first-attempt
  adb shell wm dismiss-keyguard || true
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
  test_status=0
  ./gradlew --no-daemon --console=plain --rerun-tasks :app:connectedDebugAndroidTest || test_status=$?
fi

if [[ "$api" == "35" ]]; then
  # MediaStore screenshots persist outside the app sandbox after test cleanup.
  # This executes even after test failures; the upload-artifact step uses always().
  mkdir -p app/build/winter-previews
  if ! adb pull /sdcard/Pictures/BalanceHubWinter app/build/winter-previews; then
    echo '::error::Could not retrieve winter screenshots from Android 15 emulator'
    if [[ "$test_status" -eq 0 ]]; then test_status=1; fi
  fi
  for appearance in night day; do
    for tab in 0 1 2 3 4; do
      preview="app/build/winter-previews/BalanceHubWinter/winter-${appearance}-tab${tab}.png"
      if [[ ! -s "$preview" ]]; then
        echo "::error::Winter preview is missing or empty: $preview"
        if [[ "$test_status" -eq 0 ]]; then test_status=1; fi
      fi
    done
  done
fi

exit "$test_status"
