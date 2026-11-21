#!/usr/bin/env bash
# Keep Android emulator reports and winter previews even if instrumentation fails.
set -uo pipefail

api="${1:?Android API level is required}"
suite="${2:-full}"
case "$api:$suite" in
  29:full|30:full|35:captures|35:layout|36:captures|36:layout) ;;
  *) echo "::error::Unsupported emulator suite: Android $api / $suite"; exit 2 ;;
esac

# Android 15 can return from boot to a background window with no input focus.
# Put the launcher in the foreground before instrumentation opens MainActivity.
if [[ "$api" == "35" || "$api" == "36" || "$api" == "30" ]]; then
  adb shell wm dismiss-keyguard || true
  adb shell input keyevent KEYCODE_HOME || true
fi

# API 35/36 run captures and structure in separate GitHub Actions jobs,
# each with a fresh emulator. This avoids cumulative ActivityScenario/GPU
# pressure that previously disconnected ADB on the fifth visual method.
# Every visual method and every non-visual test still runs.
results_dir="app/build/outputs/androidTest-results/connected/debug"
visual_class="com.github.russoturissto.buttonapp.app.WinterThemeVisualEmulatorTest"

run_suite() {
  local label="$1"
  shift
  local status=0
  echo "::group::Android API $api: $label"
  ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest "$@" || status=$?
  echo "::endgroup::"
  if [[ "$status" -ne 0 ]] \
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
if [[ "$api" == "35" || "$api" == "36" ]]; then
  if [[ "$suite" == "captures" ]]; then
    # Snapshots and approved art checks share one fresh emulator.
    methods=(
      approvedFestiveBannerKeepsAccessibleTextAndRedHatOverCapitalH
      festiveAppearanceIsVisibleOnAllTabsInDarkAndLight
      normalAppearanceIsVisibleOnAllTabsInDarkAndLight
      frostIsUniquePerWindowAndStableBetweenDraws
    )
  else
    # The day/night structural comparisons have their own emulator and
    # cannot be crowded out by earlier screenshot/bitmap-heavy tests.
    methods=(
      winterOnlyChangesDecorationInDayMode
      winterOnlyChangesDecorationInNightMode
    )
  fi
  for method in "${methods[@]}"; do
    label="winter-$method"
    if run_suite "$label" \
        "-Pandroid.testInstrumentationRunnerArguments.class=$visual_class#$method"; then
      # Reject false-green Gradle runs that reused an earlier test report.
      if ! grep -Rql "<testcase name=\"$method\"" "$results_dir"; then
        echo "::error::Instrumented visual test did not run: $method"
        test_status=1
        break
      fi
      mkdir -p app/build/emulator-focus-retry
      cp -a "$results_dir" "app/build/emulator-focus-retry/${label}-success"
    else
      test_status=$?
      break
    fi
  done
  if [[ "$test_status" -eq 0 && "$suite" == "layout" ]]; then
    run_suite "all-other-tests" \
      "-Pandroid.testInstrumentationRunnerArguments.notClass=$visual_class" \
      || test_status=$?
  fi
else
  run_suite "full-regression" || test_status=$?
fi

if [[ "$api" == "35" && "$suite" == "captures" ]]; then
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
