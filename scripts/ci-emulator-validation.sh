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

# Use already-compiled APKs on Android 15/16 in both Linux KVM and macOS.
# Re-running Gradle during instrumentation triggers extensive Kotlin/D8
# transformation (tens of MB of warnings), delaying actual tests.
# We explicitly validate the AndroidJUnitRunner result; adb's exit status
# alone does not indicate whether JUnit assertions passed.
direct_instrumentation=false
adb_results_dir="app/build/emulator-adb-results"

# Capture the context of an ANR, not just the instrumentation "Process crashed"
# summary. These files are uploaded even when the suite fails.
collect_emulator_diagnostics() {
  local label="$1"
  mkdir -p "$adb_results_dir"
  {
    date -u
    timeout -k 2s 8s adb devices -l || true
    timeout -k 2s 8s adb shell getprop sys.boot_completed || true
    timeout -k 2s 8s adb shell getprop dev.bootcomplete || true
    timeout -k 2s 8s adb shell cat /proc/pressure/cpu || true
    timeout -k 2s 8s adb shell cat /proc/pressure/io || true
    timeout -k 2s 8s adb shell cat /proc/pressure/memory || true
    timeout -k 2s 8s adb shell dumpsys cpuinfo || true
    timeout -k 2s 8s adb shell dumpsys activity processes || true
    timeout -k 2s 8s adb shell ls -lt /data/anr || true
  } > "$adb_results_dir/${label}-system.log" 2>&1
  timeout -k 2s 12s adb logcat -d -b crash \
    > "$adb_results_dir/${label}-crash.log" 2>&1 || true
  timeout -k 2s 12s adb logcat -d -t 3000 -v threadtime -b main -b system -b events \
    > "$adb_results_dir/${label}-recent.log" 2>&1 || true
}

# ADB's boot_completed does not imply that PackageManager and ActivityManager
# are responsive. New Android 15/16 VMs have previously ANR'd *during startup*
# alongside com.android.phone and Bluetooth. Wait for consecutive healthy
# checks and allow post-boot dex/graphics work to settle, with a hard limit.
wait_for_android_services() {
  local attempt steady=0
  for ((attempt = 1; attempt <= 24; attempt++)); do
    if [[ "$(timeout -k 2s 8s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] \
        && timeout -k 2s 8s adb shell cmd package list packages android >/dev/null 2>&1 \
        && timeout -k 2s 8s adb shell dumpsys activity activities >/dev/null 2>&1; then
      steady=$((steady + 1))
      if ((steady >= 3)); then
        echo "Android API $api services responsive; waiting 45s for post-boot load"
        sleep 45
        if timeout -k 2s 8s adb shell cmd package list packages android >/dev/null 2>&1 \
            && timeout -k 2s 8s adb shell dumpsys activity activities >/dev/null 2>&1; then
          return 0
        fi
        steady=0
      fi
    else
      steady=0
    fi
    echo "Android API $api readiness: attempt $attempt/24, consecutive=$steady"
    sleep 10
  done
  echo "::error::Android API $api did not become ready for instrumentation"
  collect_emulator_diagnostics "readiness-failed"
  return 1
}

# Only retry temporary emulator/ADB verification problems. Genuine install
# failures remain fatal; never disable the Android package verifier.
install_test_apk() {
  local apk="$1" label="$2" attempt log rc
  for ((attempt = 1; attempt <= 3; attempt++)); do
    log="$adb_results_dir/install-${label}-attempt-${attempt}.log"
    timeout -k 5s 90s adb install -r "$apk" > "$log" 2>&1
    rc=$?
    cat "$log"
    if ((rc == 0)) && grep -Eq '^Success' "$log"; then
      return 0
    fi
    if ((attempt == 3)) || { ((rc != 124 && rc != 137)) && ! grep -Eqi \
        'INSTALL_FAILED_VERIFICATION_FAILURE|Integrity verification timed out|device offline|device not found|timed out|timeout|Broken pipe' "$log"; }; then
      break
    fi
    echo "::warning::Transient $label installation failure; retry $attempt/3"
    sleep $((attempt * 20))
  done
  echo "::error::Unable to install Android test APK: $label"
  collect_emulator_diagnostics "install-${label}-failed"
  return 1
}

if [[ "$api" == "35" || "$api" == "36" ]]; then
  direct_instrumentation=true
  mkdir -p "$adb_results_dir"
  apk="app/build/outputs/apk/debug/app-debug.apk"
  test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
  [[ -s "$apk" && -s "$test_apk" ]] || {
    echo "::error::Prebuilt Android test APKs are missing"
    exit 1
  }
  wait_for_android_services || exit 1
  install_test_apk "$apk" app || exit 1
  install_test_apk "$test_apk" androidTest || exit 1
  # Stream crash/system logs *before* a graphics test can disconnect ADB.
  # Post-mortem adb logcat cannot retrieve anything from an offline emulator.
  adb logcat -v threadtime -b crash -b system -b events \
    > "$adb_results_dir/live-logcat.log" 2>&1 &
  live_logcat_pid=$!
  trap 'kill "$live_logcat_pid" >/dev/null 2>&1 || true' EXIT
fi

run_suite() {
  local label="$1"
  shift
  local status=0
  echo "::group::Android API $api: $label"
  if [[ "$direct_instrumentation" == "true" ]]; then
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
      # A disconnected emulator otherwise leaves "adb shell am instrument -w"
      # waiting until the entire 55-minute GitHub Actions job is cancelled.
      # Treat any timeout as a failed test and upload its partial runner log.
      timeout -k 10s 240s adb shell am instrument -w -r -e "$filter_kind" "$filter_value" \
        "com.github.russoturissto.buttonapp.app.test/androidx.test.runner.AndroidJUnitRunner" \
        2>&1 | tee "$log" || status=$?
      if [[ "$status" -eq 124 || "$status" -eq 137 ]]; then
        echo "::error::Android API $api instrumentation timed out: $label"
      fi
      if [[ "$status" -eq 0 ]] && {
        ! grep -Eq 'INSTRUMENTATION_CODE: -1' "$log" ||
        ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$log" ||
        grep -Eq 'INSTRUMENTATION_STATUS_CODE: -2|FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' "$log"
      }; then
        echo "::error::Instrumentation failed or ran zero tests: $label"
        status=1
      fi
      if [[ "$status" -ne 0 ]]; then
        collect_emulator_diagnostics "$label"
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
      if [[ "$direct_instrumentation" == "true" ]]; then
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
  if ! timeout -k 5s 35s adb pull /sdcard/Pictures/BalanceHubWinter app/build/winter-previews; then
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
