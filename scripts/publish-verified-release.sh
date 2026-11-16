#!/usr/bin/env bash
# Publish a signed, same-commit-emulator-tested APK as a public RC or stable.
# Stable requires workflow_dispatch + an explicit matching approval input.
set -euo pipefail

[[ "$GITHUB_REF" == "refs/heads/main" ]] || {
  echo "::error::Only main may publish APK releases"; exit 1;
}
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || {
  echo "::error::Source SHA differs from the workflow SHA"; exit 1;
}
[[ "$RELEASE_CHANNEL" == "rc" || "$RELEASE_CHANNEL" == "stable" ]] || {
  echo "::error::Unknown release channel"; exit 1;
}
[[ "$RELEASE_TAG" == "v$BUILD_VERSION_NAME" ]] || {
  echo "::error::Version and tag mismatch"; exit 1;
}
if [[ "$RELEASE_CHANNEL" == "stable" ]]; then
  [[ "$GITHUB_EVENT_NAME" == "workflow_dispatch" &&
     "${INPUT_PUBLISH_STABLE:-false}" == "true" &&
     "$BUILD_VERSION_NAME" == "${INPUT_APPROVED_VERSION:-}" ]] || {
    echo "::error::Explicit stable release approval is required"; exit 1;
  }
else
  [[ "$BUILD_VERSION_NAME" =~ ^[1-9][0-9]*\.[0-9]+\.[0-9]+-rc[1-9][0-9]*$ ]] || {
    echo "::error::Invalid public RC version"; exit 1;
  }
fi
[[ -s "$RELEASE_NOTES_PATH" ]] || {
  echo "::error::Release notes are missing"; exit 1;
}
apk="app/build/outputs/apk/release/BalanceHub.apk"
[[ -s "$apk" ]] || { echo "::error::Signed APK is missing"; exit 1; }
code="$((GITHUB_RUN_NUMBER + 300))"
sha256="$(sha256sum "$apk" | awk '{print $1}')"
bytes="$(stat -c %s "$apk")"
previous_stable="$(gh api "repos/$GITHUB_REPOSITORY/releases/latest" --jq '.tag_name')"

# CI must confirm Android API 29, 35 and 36 on this *exact commit*, whether
# this was an ordinary push or an explicitly approved manual stable run.
verified_run=""
for attempt in $(seq 1 100); do
  runs="$(gh api "repos/$GITHUB_REPOSITORY/actions/workflows/notification-emulator-validation.yml/runs?event=push&head_sha=$GITHUB_SHA&per_page=30")"
  candidate="$(printf '%s' "$runs" | jq -c --arg sha "$GITHUB_SHA" \
    '[.workflow_runs[] | select(.head_sha == $sha and .event == "push")] |
     sort_by(.created_at) | last // empty')"
  if [[ -n "$candidate" ]]; then
    run_id="$(printf '%s' "$candidate" | jq -r '.id')"
    status="$(printf '%s' "$candidate" | jq -r '.status')"
    result="$(printf '%s' "$candidate" | jq -r '.conclusion // empty')"
    if [[ "$status" == "completed" ]]; then
      [[ "$result" == "success" ]] || {
        echo "::error::Same-SHA emulator run $run_id failed ($result)"
        exit 1
      }
      jobs="$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/$run_id/jobs?per_page=100")"
      for api in 29 35 36; do
        printf '%s' "$jobs" | jq -e --arg name "Android API $api" \
          'any(.jobs[]; .name == $name and .conclusion == "success")' >/dev/null || {
          echo "::error::Android API $api did not pass in run $run_id"
          exit 1
        }
      done
      verified_run="$run_id"
      break
    fi
  fi
  echo "Waiting for matching emulator results ($attempt/100)"
  sleep 15
done
[[ -n "$verified_run" ]] || {
  echo "::error::Timed out waiting for emulator verification"; exit 1;
}

# A newer main revision may invalidate the APK, even if this run passed.
tip="$(gh api "repos/$GITHUB_REPOSITORY/branches/main" --jq '.commit.sha')"
[[ "$tip" == "$GITHUB_SHA" ]] || {
  echo "::error::main advanced; refusing to publish an outdated APK"; exit 1;
}
if gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$RELEASE_TAG" >/dev/null 2>&1 ||
   gh release view "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  echo "::error::Release tag already exists, refusing overwrite: $RELEASE_TAG"
  exit 1
fi

notes="$RUNNER_TEMP/balancehub-public-release-notes.md"
cat "$RELEASE_NOTES_PATH" > "$notes"
# The in-app stable updater requires a hidden numeric Android code marker.
# This is harmless on prereleases too; it does not make them eligible.
printf '\n<!-- BalanceHub-VersionCode: %s -->\n' "$code" >> "$notes"

title="BalanceHub $BUILD_VERSION_NAME"
if [[ "$RELEASE_CHANNEL" == "rc" ]]; then
  gh release create "$RELEASE_TAG" "$apk#BalanceHub.apk" \
    --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" \
    --title "$title · тестовая версия" --notes-file "$notes" \
    --draft --prerelease --latest=false
else
  gh release create "$RELEASE_TAG" "$apk#BalanceHub.apk" \
    --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" \
    --title "$title" --notes-file "$notes" \
    --draft --latest=false
fi

# GitHub gives *draft* assets a temporary untagged download URL. Validate
# digest/size while private, then check permanent tag URL after publishing.
staged="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" |
  jq -c --arg tag "$RELEASE_TAG" '.[] | select(.tag_name == $tag)' | head -1)"
[[ -n "$staged" ]] || { echo "::error::Cannot verify staged release"; exit 1; }
if [[ "$RELEASE_CHANNEL" == "rc" ]]; then
  expected_pre="true"
else
  expected_pre="false"
fi
printf '%s' "$staged" | jq -e \
  --arg tag "$RELEASE_TAG" --arg commit "$GITHUB_SHA" \
  --arg code "$code" --arg digest "sha256:$sha256" \
  --argjson bytes "$bytes" --argjson pre "$expected_pre" \
  '.tag_name == $tag and .draft == true and .prerelease == $pre and
   .target_commitish == $commit and
   (.body | contains("<!-- BalanceHub-VersionCode: " + $code + " -->")) and
   ([.assets[] | select(.name == "BalanceHub.apk" and
       .digest == $digest and .size == $bytes)] | length) == 1' >/dev/null || {
  echo "::error::Staged APK version, hash, size, or release metadata mismatch"
  exit 1
}

if [[ "$RELEASE_CHANNEL" == "rc" ]]; then
  gh release edit "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" \
    --draft=false --prerelease --latest=false
else
  gh release edit "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" \
    --draft=false --prerelease=false --latest
fi

published="$(gh api "repos/$GITHUB_REPOSITORY/releases/tags/$RELEASE_TAG")"
printf '%s' "$published" | jq -e \
  --arg tag "$RELEASE_TAG" --arg commit "$GITHUB_SHA" \
  --arg digest "sha256:$sha256" --argjson bytes "$bytes" \
  --argjson pre "$expected_pre" \
  --arg url "https://github.com/$GITHUB_REPOSITORY/releases/download/$RELEASE_TAG/BalanceHub.apk" \
  '.tag_name == $tag and .draft == false and .prerelease == $pre and
   .target_commitish == $commit and
   (any(.assets[]; .name == "BalanceHub.apk" and .digest == $digest and
       .size == $bytes and .browser_download_url == $url))' >/dev/null || {
  echo "::error::Public release metadata or direct APK URL failed verification"
  exit 1
}

latest="$(gh api "repos/$GITHUB_REPOSITORY/releases/latest" --jq '.tag_name')"
if [[ "$RELEASE_CHANNEL" == "rc" ]]; then
  [[ "$latest" == "$previous_stable" ]] || {
    echo "::error::Pre-release unexpectedly replaced latest stable release"; exit 1;
  }
else
  [[ "$latest" == "$RELEASE_TAG" ]] || {
    echo "::error::Approved stable version is not GitHub latest"; exit 1;
  }
fi

echo "Published public $RELEASE_CHANNEL $RELEASE_TAG (Android code $code, SHA-256 $sha256)"
echo "Release: https://github.com/$GITHUB_REPOSITORY/releases/tag/$RELEASE_TAG" >> "$GITHUB_STEP_SUMMARY"
echo "Direct APK: https://github.com/$GITHUB_REPOSITORY/releases/download/$RELEASE_TAG/BalanceHub.apk" >> "$GITHUB_STEP_SUMMARY"
echo "Same-SHA Android tests: https://github.com/$GITHUB_REPOSITORY/actions/runs/$verified_run" >> "$GITHUB_STEP_SUMMARY"
