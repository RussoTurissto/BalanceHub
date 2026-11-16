#!/usr/bin/env bash
# One-time promotion of an explicitly approved stable release.
# Never publish a signed APK before complete Android emulator CI passes.
set -euo pipefail

tag="v1.2.0"
[[ "$BUILD_VERSION_NAME" == "1.2.0" ]] || {
  echo "::error::Unexpected stable version name"; exit 1;
}
[[ "$GITHUB_REF" == "refs/heads/main" ]] || {
  echo "::error::Stable publishing is allowed only from main"; exit 1;
}
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || {
  echo "::error::Checked-out source does not match the verified commit"; exit 1;
}

apk="app/build/outputs/apk/release/BalanceHub.apk"
[[ -s "$apk" ]] || { echo "::error::Signed release APK missing"; exit 1; }
code="$((GITHUB_RUN_NUMBER + 300))"
actual_sha256="$(sha256sum "$apk" | awk '{print $1}')"
actual_size="$(stat -c %s "$apk")"
notes="release/notes-1.2.0.md"
[[ -s "$notes" ]] || { echo "::error::Approved stable release notes missing"; exit 1; }

# A stable tag is immutable for this publishing path. Never overwrite an
# existing release (including a preexisting draft).
existing="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" |
  jq -r --arg tag "$tag" '.[] | select(.tag_name == $tag) | .id' | head -1)"
if [[ -n "$existing" ]]; then
  # Recover the already signed and fully emulator-tested 1.2.0 / build 711.
  # The first publishing attempt created a valid private draft, then stopped
  # because GitHub uses temporary "untagged-..." asset URLs for drafts.
  # Promote ONLY this pinned, authenticated binary; never replace its bytes.
  previous_sha="ace9fd179d88d12b44edc253de8089cfd80af26b"
  previous_code="711"
  previous_digest="sha256:5c1c4d501c61579ff9240cbcf1e7415062b83f979322235eae4f25474d23c99a"
  previous_size=10217983
  draft="$(gh api "repos/$GITHUB_REPOSITORY/releases/$existing")"
  if printf '%s' "$draft" | jq -e '.draft == false and .prerelease == false' >/dev/null; then
    echo "::notice::Already published stable release $tag; refusing to overwrite."
    exit 0
  fi
  printf '%s' "$draft" | jq -e \
    --arg tag "$tag" --arg sha "$previous_sha" --arg code "$previous_code" \
    --arg digest "$previous_digest" --argjson size "$previous_size" \
    '.tag_name == $tag and .draft == true and .prerelease == false and
     .target_commitish == $sha and
     (.body | contains("<!-- BalanceHub-VersionCode: " + $code + " -->")) and
     ([.assets[] | select(.name == "BalanceHub.apk" and
        .digest == $digest and .size == $size)] | length) == 1' >/dev/null || {
    echo "::error::Existing stable draft differs from pinned verified build 711."
    exit 1
  }

  # The same APK must be present in the CI-signed private draft created before
  # stable publishing. Both assets have identical size and SHA-256.
  # GitHub renames older draft tag_name values to "untagged-..." when
  # another draft is created. The immutable release ID and asset digest are
  # the reliable identity of the originally signed 711 build.
  signed_draft="$(gh api "repos/$GITHUB_REPOSITORY/releases/446908808")"
  [[ -n "$signed_draft" ]] || {
    echo "::error::Private signed build 711 is missing"; exit 1;
  }
  printf '%s' "$signed_draft" | jq -e \
    --arg sha "$previous_sha" --arg digest "$previous_digest" \
    --argjson size "$previous_size" \
    '.draft == true and .prerelease == true and
     (.name | contains("сборка 711")) and
     .target_commitish == $sha and
     ([.assets[] | select(.name == "BalanceHub.apk" and
       .digest == $digest and .size == $size)] | length) == 1' >/dev/null || {
    echo "::error::Signed build checksum/commit does not match stable draft"; exit 1;
  }

  # The original signed build successfully compiled, passed its JVM checks
  # and verified the signing certificate, while only publication failed.
  original_jobs="$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/43851659591/jobs?per_page=100")"
  for step in "Run automated checks before release build" \
              "Build signed release APK (retry transient Maven errors)" \
              "Verify signed APK and signing certificate"; do
    printf '%s' "$original_jobs" | jq -e --arg name "$step" \
      'any(.jobs[].steps[]; .name == $name and .conclusion == "success")' \
      >/dev/null || {
      echo "::error::Original signed build check missing: $step"; exit 1;
    }
  done

  validation="$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/43851659620")"
  printf '%s' "$validation" | jq -e --arg sha "$previous_sha" \
    '.head_sha == $sha and .status == "completed" and .conclusion == "success"' \
    >/dev/null || {
    echo "::error::Emulator validation did not pass for original APK sources"; exit 1;
  }
  validation_jobs="$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/43851659620/jobs?per_page=100")"
  for api in 29 35 36; do
    printf '%s' "$validation_jobs" | jq -e --arg name "Android API $api" \
      'any(.jobs[]; .name == $name and .conclusion == "success")' \
      >/dev/null || {
      echo "::error::Missing successful Android API $api validation"; exit 1;
    }
  done

  # Verify size/hash *before* publication, canonical URL *after*: GitHub
  # generates a temporary untagged URL while a release is still a draft.
  gh release edit "$tag" --repo "$GITHUB_REPOSITORY" \
    --draft=false --prerelease=false --latest
  latest="$(gh api "repos/$GITHUB_REPOSITORY/releases/latest")"
  printf '%s' "$latest" | jq -e \
    --arg tag "$tag" --arg code "$previous_code" \
    --arg digest "$previous_digest" --argjson size "$previous_size" \
    --arg url "https://github.com/$GITHUB_REPOSITORY/releases/download/$tag/BalanceHub.apk" \
    '.tag_name == $tag and .draft == false and .prerelease == false and
     (.body | contains("<!-- BalanceHub-VersionCode: " + $code + " -->")) and
     (any(.assets[]; .name == "BalanceHub.apk" and .size == $size and
         .digest == $digest and .browser_download_url == $url))' >/dev/null || {
    echo "::error::Public stable release metadata verification failed"; exit 1;
  }
  echo "Published verified BalanceHub 1.2.0 / build 711 without rebuilding or replacing the APK."
  echo "Stable 1.2.0: https://github.com/$GITHUB_REPOSITORY/releases/tag/$tag" >> "$GITHUB_STEP_SUMMARY"
  exit 0
fi

echo "Waiting for Android API 29, 35, and 36 emulator results for commit $GITHUB_SHA"
verified_run=""
for attempt in $(seq 1 80); do
  # All workflows must test the *same commit* as the signed APK.
  runs="$(gh api "repos/$GITHUB_REPOSITORY/actions/workflows/notification-emulator-validation.yml/runs?event=push&head_sha=$GITHUB_SHA&per_page=20")"
  latest="$(printf '%s' "$runs" |
    jq -c --arg sha "$GITHUB_SHA" \
      '[.workflow_runs[] | select(.head_sha == $sha and .event == "push")] |
       sort_by(.created_at) | last // empty')"
  if [[ -n "$latest" ]]; then
    run_id="$(printf '%s' "$latest" | jq -r '.id')"
    conclusion="$(printf '%s' "$latest" | jq -r '.conclusion // empty')"
    status="$(printf '%s' "$latest" | jq -r '.status')"
    if [[ "$status" == "completed" && "$conclusion" == "success" ]]; then
      jobs="$(gh api --paginate "repos/$GITHUB_REPOSITORY/actions/runs/$run_id/jobs?per_page=100")"
      if ! printf '%s' "$jobs" | jq -e '
          ([.jobs[] | select(.name == "Android API 29" and .conclusion == "success")] | length) >= 1 and
          ([.jobs[] | select(.name == "Android API 35" and .conclusion == "success")] | length) >= 1 and
          ([.jobs[] | select(.name == "Android API 36" and .conclusion == "success")] | length) >= 1
        ' >/dev/null; then
        echo "::error::Not all required Android emulator jobs passed"
        exit 1
      fi
      verified_run="$run_id"
      break
    fi
    if [[ "$status" == "completed" && "$conclusion" != "success" ]]; then
      echo "::error::Android emulator checks failed for commit $GITHUB_SHA (run $run_id)"
      exit 1
    fi
  fi
  echo "Emulator verification pending ($attempt/80)"
  sleep 15
done
[[ -n "$verified_run" ]] || {
  echo "::error::Timed out waiting for matching Android emulator checks"; exit 1;
}

# A later main push must not silently make a release from stale sources.
tip="$(gh api "repos/$GITHUB_REPOSITORY/branches/main" --jq '.commit.sha')"
[[ "$tip" == "$GITHUB_SHA" ]] || {
  echo "::error::main moved since the approved APK build; refusing publication"; exit 1;
}

# User-facing notes are plain text because Android shows them in a TextView,
# not a Markdown renderer. Append only an HTML marker consumed by the in-app
# update client, keeping the notes concise for users.
release_notes="$RUNNER_TEMP/balancehub-1.2.0-notes.md"
cat "$notes" > "$release_notes"
printf '\n<!-- BalanceHub-VersionCode: %s -->\n' "$code" >> "$release_notes"

# Upload the signed file *privately*, verify all GitHub metadata and its digest,
# then publish it as a full (non-prerelease) Latest release.
gh release create "$tag" "$apk#BalanceHub.apk" \
  --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" \
  --title "BalanceHub 1.2.0" --notes-file "$release_notes" \
  --draft --latest=false

draft="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" |
  jq -c --arg tag "$tag" '[.[] | select(.tag_name == $tag)] | first // empty')"
[[ -n "$draft" ]] || { echo "::error::Draft release cannot be verified"; exit 1; }
printf '%s' "$draft" | jq -e --arg tag "$tag" --arg code "$code" \
  '.tag_name == $tag and .draft == true and .prerelease == false and
   (.body | contains("<!-- BalanceHub-VersionCode: " + $code + " -->"))' >/dev/null || {
  echo "::error::Draft release metadata differs from expected version"; exit 1;
}
asset="$(printf '%s' "$draft" | jq -c '[.assets[] | select(.name == "BalanceHub.apk")] | first // empty')"
[[ -n "$asset" ]] || { echo "::error::Draft signed APK asset missing"; exit 1; }
printf '%s' "$asset" | jq -e \
  --arg digest "sha256:$actual_sha256" \
  --argjson size "$actual_size" \
  '.digest == $digest and .size == $size' >/dev/null || {
  echo "::error::GitHub APK size, digest, or download URL mismatch"; exit 1;
}

gh release edit "$tag" --repo "$GITHUB_REPOSITORY" \
  --draft=false --prerelease=false --latest

latest="$(gh api "repos/$GITHUB_REPOSITORY/releases/latest")"
printf '%s' "$latest" | jq -e \
  --arg tag "$tag" --arg code "$code" --arg digest "sha256:$actual_sha256" \
  --arg url "https://github.com/$GITHUB_REPOSITORY/releases/download/$tag/BalanceHub.apk" \
  '.tag_name == $tag and .draft == false and .prerelease == false and
   (.body | contains("<!-- BalanceHub-VersionCode: " + $code + " -->")) and
   (any(.assets[]; .name == "BalanceHub.apk" and .digest == $digest and
       .browser_download_url == $url))' >/dev/null || {
  echo "::error::Stable release not available as GitHub latest, or metadata failed"; exit 1;
}
echo "Verified public stable release $tag, Android versionCode $code, SHA-256 $actual_sha256"
echo "Android emulator validation: https://github.com/$GITHUB_REPOSITORY/actions/runs/$verified_run" >> "$GITHUB_STEP_SUMMARY"
echo "Stable 1.2.0: https://github.com/$GITHUB_REPOSITORY/releases/tag/$tag" >> "$GITHUB_STEP_SUMMARY"
