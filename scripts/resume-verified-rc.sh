#!/usr/bin/env bash
# Promote a fully verified signed RC draft without rebuilding its APK.
# Exit 10 ONLY if there is no pending RC draft; the caller may dispatch a build.
set -euo pipefail

[[ "$GITHUB_REPOSITORY" == "RussoTurissto/BalanceHub" ]] || {
  echo "::error::Unexpected repository"; exit 1;
}
tip="$(gh api "repos/$GITHUB_REPOSITORY/branches/main" --jq '.commit.sha')"
[[ "$(git rev-parse HEAD)" == "$tip" ]] || {
  echo "::error::Checkout does not match the current main branch"; exit 1;
}
target="$(sed -n 's/^NEXT_RELEASE_VERSION=//p' gradle.properties | head -1)"
[[ "$target" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  echo "::error::Invalid target version"; exit 1;
}
prefix="v$target-rc"
# Draft releases are hidden from /releases/tags/{tag}; search all pages.
drafts="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" |
  jq -cs --arg prefix "$prefix"     '[.[][] | select(.draft == true and .prerelease == true and
      (.tag_name | startswith($prefix)))]')"
count="$(printf '%s' "$drafts" | jq 'length')"
if [[ "$count" == 0 ]]; then
  echo "No pending RC draft: a new signed build is required."
  exit 10
fi
[[ "$count" == 1 ]] || {
  echo "::error::Multiple pending RC drafts need manual review"; exit 1;
}
draft="$(printf '%s' "$drafts" | jq -c '.[0]')"
tag="$(printf '%s' "$draft" | jq -r '.tag_name')"
[[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-rc[1-9][0-9]*$ ]] || {
  echo "::error::Invalid staged RC tag"; exit 1;
}
release_id="$(printf '%s' "$draft" | jq -r '.id')"
draft_sha="$(printf '%s' "$draft" | jq -r '.target_commitish')"
[[ "$draft_sha" =~ ^[a-f0-9]{40}$ ]] || {
  echo "::error::Staged source SHA is invalid"; exit 1;
}
git cat-file -e "$draft_sha^{commit}" || {
  echo "::error::Staged source commit cannot be found"; exit 1;
}
git merge-base --is-ancestor "$draft_sha" "$tip" || {
  echo "::error::Staged commit is not an ancestor of main"; exit 1;
}
# The staged APK may predate infrastructure-only repairs. Reject ANY other
# changes after its source SHA, including runtime, Gradle or version metadata.
while IFS= read -r changed; do
  [[ -z "$changed" ]] && continue
  case "$changed" in
    .github/workflows/chat-build-trigger.yml|.github/workflows/release-infrastructure-validation.yml|scripts/publish-verified-release.sh|scripts/resume-verified-rc.sh|scripts/test-release-versioning.sh) ;;
    *)
      echo "::error::Source changed after the staged APK: $changed"; exit 1 ;;
  esac
done < <(git diff --name-only "$draft_sha" "$tip")

code="$(printf '%s' "$draft" | jq -r   '.body | capture("BalanceHub-VersionCode: (?<code>[0-9]+)") | .code' 2>/dev/null || true)"
[[ "$code" =~ ^[0-9]+$ ]] && (( code > 722 )) || {
  echo "::error::Android versionCode is invalid or obsolete"; exit 1;
}
asset="$(printf '%s' "$draft" |
  jq -c '[.assets[] | select(.name == "BalanceHub.apk")] |
    if length == 1 then .[0] else empty end')"
[[ -n "$asset" ]] || {
  echo "::error::The draft must contain exactly one BalanceHub.apk"; exit 1;
}
asset_id="$(printf '%s' "$asset" | jq -r '.id')"
asset_size="$(printf '%s' "$asset" | jq -r '.size')"
asset_digest="$(printf '%s' "$asset" | jq -r '.digest')"
[[ "$asset_digest" =~ ^sha256:[a-f0-9]{64}$ ]] || {
  echo "::error::Missing staged APK SHA-256"; exit 1;
}

# All three Android emulator jobs must have passed for the original APK SHA.
runs="$(gh api "repos/$GITHUB_REPOSITORY/actions/workflows/notification-emulator-validation.yml/runs?event=push&head_sha=$draft_sha&per_page=30")"
tested_id="$(printf '%s' "$runs" | jq -r --arg sha "$draft_sha"   '[.workflow_runs[] | select(.event == "push" and .head_sha == $sha and
    .status == "completed" and .conclusion == "success")] |
    sort_by(.created_at) | .[-1].id // empty')"
[[ -n "$tested_id" ]] || {
  echo "::error::Successful same-SHA emulator run not found"; exit 1;
}
jobs="$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/$tested_id/jobs?per_page=100")"
for api in 29 35 36; do
  printf '%s' "$jobs" | jq -e --arg name "Android API $api"     'any(.jobs[]; .name == $name and .conclusion == "success")' >/dev/null || {
    echo "::error::Android API $api has not passed"; exit 1;
  }
done

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/staged" "$tmp/stable"
# Use the asset ID, not its private untagged download URL.
curl --fail --silent --show-error --location --retry 3   -H 'Accept: application/octet-stream'   -H 'X-GitHub-Api-Version: 2022-11-28'   -H "Authorization: Bearer $GH_TOKEN"   "https://api.github.com/repos/$GITHUB_REPOSITORY/releases/assets/$asset_id"   --output "$tmp/staged/BalanceHub.apk"
new="$tmp/staged/BalanceHub.apk"
[[ -s "$new" ]] || { echo "::error::Staged APK download failed"; exit 1; }
actual_size="$(stat -c %s "$new")"
actual_digest="sha256:$(sha256sum "$new" | awk '{print $1}')"
[[ "$actual_size" == "$asset_size" && "$actual_digest" == "$asset_digest" ]] || {
  echo "::error::APK checksum/size differs from the staged asset"; exit 1;
}

gh release download v1.2.1 --repo "$GITHUB_REPOSITORY"   --pattern BalanceHub.apk --dir "$tmp/stable"
old="$tmp/stable/BalanceHub.apk"
[[ -s "$old" ]] || { echo "::error::Previous stable APK missing"; exit 1; }
stable_digest="$(gh api "repos/$GITHUB_REPOSITORY/releases/tags/v1.2.1"   --jq '.assets[] | select(.name == "BalanceHub.apk") | .digest')"
[[ "sha256:$(sha256sum "$old" | awk '{print $1}')" == "$stable_digest" ]] || {
  echo "::error::Stable APK checksum verification failed"; exit 1;
}
sdk_tools="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1   -type d | sort -V | tail -n 1)"
[[ -x "$sdk_tools/apksigner" && -x "$sdk_tools/aapt" ]] || {
  echo "::error::Android build-tools for APK verification are unavailable"; exit 1;
}
"$sdk_tools/apksigner" verify --print-certs "$old" > "$tmp/old-signature"
"$sdk_tools/apksigner" verify --print-certs "$new" > "$tmp/new-signature"
old_fp="$(grep -m1 'certificate SHA-256 digest:' "$tmp/old-signature" |
  sed -E 's/.*digest: //' | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
new_fp="$(grep -m1 'certificate SHA-256 digest:' "$tmp/new-signature" |
  sed -E 's/.*digest: //' | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
[[ -n "$old_fp" && "$old_fp" == "$new_fp" ]] || {
  echo "::error::The staged APK is not signed with the stable release key"; exit 1;
}
"$sdk_tools/aapt" dump badging "$old" > "$tmp/old-package"
"$sdk_tools/aapt" dump badging "$new" > "$tmp/new-package"
package="com.github.russoturissto.buttonapp.app"
grep -q "^package: name='$package' versionCode='722'" "$tmp/old-package" || {
  echo "::error::Stable APK package identity mismatch"; exit 1;
}
name="$(printf '%s' "$tag" | sed 's/^v//')"
grep -q "^package: name='$package' versionCode='$code' versionName='$name'" "$tmp/new-package" || {
  echo "::error::Staged APK name, package or Android versionCode mismatch"; exit 1;
}
previous_stable="$(gh api "repos/$GITHUB_REPOSITORY/releases/latest" --jq '.tag_name')"
[[ "$previous_stable" == "v1.2.1" ]] || {
  echo "::error::Stable release changed; refuse staged promotion"; exit 1;
}
[[ "$(gh api "repos/$GITHUB_REPOSITORY/branches/main" --jq '.commit.sha')" == "$tip" ]] || {
  echo "::error::Main branch moved during verification"; exit 1;
}

# Publish the SAME draft asset and release ID; do not create or rebuild APK.
gh api --method PATCH "repos/$GITHUB_REPOSITORY/releases/$release_id"   -F draft=false -F prerelease=true >/dev/null
published=""
for attempt in $(seq 1 12); do
  published="$(gh api "repos/$GITHUB_REPOSITORY/releases/tags/$tag" 2>/dev/null || true)"
  [[ -n "$published" ]] && break
  if (( attempt < 12 )); then sleep 2; fi
done
[[ -n "$published" ]] || {
  echo "::error::Published release did not become visible by tag"; exit 1;
}
url="https://github.com/$GITHUB_REPOSITORY/releases/download/$tag/BalanceHub.apk"
printf '%s' "$published" | jq -e   --arg tag "$tag" --arg sha "$draft_sha" --arg url "$url"   --arg digest "$asset_digest" --argjson size "$asset_size"   '.tag_name == $tag and .target_commitish == $sha and
   .draft == false and .prerelease == true and
   (any(.assets[]; .name == "BalanceHub.apk" and .digest == $digest and
     .size == $size and .browser_download_url == $url))' >/dev/null || {
  echo "::error::Published APK verification failed"; exit 1;
}
[[ "$(gh api "repos/$GITHUB_REPOSITORY/releases/latest" --jq '.tag_name')" == "$previous_stable" ]] || {
  echo "::error::Public RC unexpectedly replaced latest stable"; exit 1;
}
echo "Promoted verified existing $tag APK (versionCode $code, SHA-256 $asset_digest)"
echo "Release: https://github.com/$GITHUB_REPOSITORY/releases/tag/$tag" >> "$GITHUB_STEP_SUMMARY"
echo "Direct APK: $url" >> "$GITHUB_STEP_SUMMARY"
echo "Original Android tests: https://github.com/$GITHUB_REPOSITORY/actions/runs/$tested_id" >> "$GITHUB_STEP_SUMMARY"
