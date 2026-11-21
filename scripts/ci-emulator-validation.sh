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
# Hosted runners sometimes kill the 3-GB Android 15/16 VM during large
# Skia screenshot tests. Keep the same ~360dp phone layout at a lower pixel
# resolution, cutting framebuffer and bitmap pressure without skipping tests.
if [[ "$api" == "35" || "$api" == "36" ]]; then
  adb shell wm size 720x1600
  adb shell wm density 320
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0
fi

# API 35/36 run captures and structure in separate GitHub Actions jobs,
# each with a fresh emulator. This avoids cumulative ActivityScenario/GPU
# pressure that previously disconnected ADB on the fifth visual method.
# Every visual method and every non-visual test still runs.
results_dir="app/build/outputs/androidTest-results/connected/debug"
visual_class="com.github.russoturissto.buttonapp.app.WinterThemeVisualEmulatorTest"

# Use already-compiled APKs for the Mac emulator tests.
# Re-running Gradle during instrumentation triggers extensive Kotlin/D8
# transformation (tens of MB of warnings), delaying actual tests.
# We explicitly validate the AndroidJUnitRunner result; adb's exit status
# alone does not indicate whether JUnit assertions passed.
macos_direct=false
adb_results_dir="app/build/emulator-adb-results"
if [[ "$(uname -s)" == "Darwin" && ( "$api" == "35" || "$api" == "36" ) ]]; then
  macos_direct=true
  mkdir -p "$adb_results_dir"
  apk="app/build/outputs/apk/debug/app-debug.apk"
  test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
  [[ -s "$apk" && -s "$test_apk" ]] || {
    echo "::error::Prebuilt Android test APKs are missing"
    exit 1
  }
  adb install -r "$apk" || exit 1
  adb install -r "$test_apk" || exit 1
fi

run_suite() {
  local label="$1"
  shift
  local status=0
  echo "::group::Android API $api: $label"
  if [[ "$macos_direct" == "true" ]]; then
    local filter_kind="" filter_value="" argument
    for argument in "$@"; do
      case "$argument" in
        -Pandroid.testInstrumentationRunnerArguments.class=*)
          filter_kind=class
          filter_value="${argument#*=}"
          ;;
        -Pandroid.testInstrumentationRunnerArguments.notClass=*)
          filter_kind=notClass
          filter_value="${argument#*=}"
          ;;
        *)
          echo "::error::Unsupported instrumentation selection: $argument"
          status=2
          ;;
      esac
    done
    local log="$adb_results_dir/${label}.log"
    if [[ "$status" -eq 0 && -n "$filter_kind" && -n "$filter_value" ]]; then
      adb shell am instrument -w -r -e "$filter_kind" "$filter_value" \
        "com.github.russoturissto.buttonapp.app.test/androidx.test.runner.AndroidJUnitRunner" \
        2>&1 | tee "$log" || status=$?
      if [[ "$status" -eq 0 ]] && {
        ! grep -Eq 'INSTRUMENTATION_CODE: -1' "$log" ||
        ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$log" ||
        grep -Eq 'INSTRUMENTATION_STATUS_CODE: -2|FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' "$log"
      }; then
        echo "::error::Instrumentation failed or ran zero tests: $label"
        # Preserve the actual Android exception before the emulator is torn
        # down; a bare 'Process crashed' result is not enough to diagnose.
        adb logcat -d -b crash > "$adb_results_dir/${label}-crash.log" 2>&1 || true
        adb logcat -d -t 400 > "$adb_results_dir/${label}-recent.log" 2>&1 || true
        status=1
      fi
    elif [[ "$status" -eq 0 ]]; then
      echo "::error::Missing instrumentation selection for $label"
      status=2
    fi
  else
    local gradle_args=(--no-daemon --console=plain)
    if [[ "$api" == "35" || "$api" == "36" ]]; then
      gradle_args+=("-Dorg.gradle.jvmargs=-Xmx1024m" "--max-workers=2")
    fi
    ./gradlew "${gradle_args[@]}" :app:connectedDebugAndroidTest "$@" || status=$?
    if [[ "$status" -ne 0 ]] \
        && [[ -d "$results_dir" ]] \
        && grep -Rql 'RootViewWithoutFocusException' "$results_dir"; then
      echo "::warning::Android lost window focus in $label; retrying once"
      mkdir -p "app/build/emulator-focus-retry"
      cp -a "$results_dir" "app/build/emulator-focus-retry/${label}-first-attempt"
      adb shell wm dismiss-keyguard || true
      adb shell input keyevent KEYCODE_HOME || true
      sleep 2
      status=0
      ./gradlew "${gradle_args[@]}" --rerun-tasks \
        :app:connectedDebugAndroidTest "$@" || status=$?
    fi
  fi
  echo "::endgroup::"
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
      # Do not count an unexecuted method or a stale test report as green.
      if [[ "$macos_direct" == "true" ]]; then
        if ! grep -Fq "INSTRUMENTATION_STATUS: test=$method" \
            "$adb_results_dir/${label}.log"; then
          echo "::error::Required visual test did not run: $method"
          test_status=1
          break
        fi
      else
        if ! grep -Rql "<testcase name=\\\"$method\\\"" "$results_dir"; then
          echo "::error::Required visual test did not run: $method"
          test_status=1
          break
        fi
        mkdir -p app/build/emulator-focus-retry
        cp -a "$results_dir" "app/build/emulator-focus-retry/${label}-success"
      fi
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
