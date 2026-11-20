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

# On API 35 the full 55-test session exhausted or destabilised the emulator
# while rendering many winter snapshots. Run the 4 visual tests first on a fresh
# emulator session, then ALL other tests separately (no test is silently skipped).
# Keep both reports and screenshots for manual inspection.
results_dir="app/build/outputs/androidTest-results/connected/debug"
visual_class="com.github.russoturissto.buttonapp.app.WinterThemeVisualEmulatorTest"

run_suite() {
  local label="$1"
  shift
  local status=0
  echo "::group::Android API $api: $label"
  ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest "$@" || status=$?
  echo "::endgroup::"
  if [[ "$api" == "35" && "$status" -ne 0 ]] \
      && [[ -d "$results_dir" ]] \
      && grep -Rql 'RootViewWithoutFocusException' "$results_dir"; then
    echo "::warning::Android 15 lost window focus in $label; preserving report and retrying once"
    mkdir -p "app/build/emulator-focus-retry"
    cp -a "$results_dir" "app/build/emulator-focus-retry/${label}-first-attempt"
    adb shell wm dismiss-keyguard || true
    adb shell input keyevent KEYCODE_HOME || true
    sleep 2
    status=0
    ./gradlew --no-daemon --console=plain --rerun-tasks \
      :app:connectedDebugAndroidTest "$@" || status=$?
  fi
  return "$status"
}

test_status=0
if [[ "$api" == "35" ]]; then
  if run_suite "winter-visual" \
      "-Pandroid.testInstrumentationRunnerArguments.class=$visual_class"; then
    # Gradle overwrites connected-test reports on the next invocation.
    mkdir -p app/build/emulator-focus-retry
    cp -a "$results_dir" app/build/emulator-focus-retry/winter-visual-success
    run_suite "all-other-tests" \
      "-Pandroid.testInstrumentationRunnerArguments.notClass=$visual_class" \
      || test_status=$?
  else
    test_status=$?
  fi
else
  run_suite "full-regression" || test_status=$?
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
