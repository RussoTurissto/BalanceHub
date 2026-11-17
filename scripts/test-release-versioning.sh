#!/usr/bin/env bash
# Offline tests of monotonic RC numbering and explicit stable approval.
set -euo pipefail
bash -n scripts/prepare-release-version.sh
bash -n scripts/publish-verified-release.sh
bash -n scripts/resume-verified-rc.sh
bash -n scripts/require-app-changes.sh
bash -n scripts/test-release-versioning.sh

repo_root="$(pwd)"
temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
mkdir -p "$temp/bin" "$temp/scripts" "$temp/release"
cp scripts/prepare-release-version.sh "$temp/scripts/"
printf 'NEXT_RELEASE_VERSION=1.2.1\n' > "$temp/gradle.properties"
printf 'RC notes\n' > "$temp/release/notes-1.2.1-rc.md"
printf 'Stable notes\n' > "$temp/release/notes-1.2.1.md"

cat > "$temp/bin/gh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == "api repos/RussoTurissto/BalanceHub/releases/latest --jq .tag_name" ]]; then
  echo v1.2.0
elif [[ "$*" == "api --paginate repos/RussoTurissto/BalanceHub/releases?per_page=100" ]]; then
  if [[ "$SCENARIO" == "first" ]]; then
    echo '[{"tag_name":"v1.2.0","prerelease":false,"draft":false}]'
  else
    echo '[{"tag_name":"v1.2.0","prerelease":false,"draft":false},
           {"tag_name":"v1.2.1-rc1","prerelease":true,"draft":false},
           {"tag_name":"v1.2.1-rc2","prerelease":true,"draft":false},
           {"tag_name":"v1.2.1-rc100","prerelease":true,"draft":true},
           {"tag_name":"v1.3.0-rc50","prerelease":true,"draft":false}]'
  fi
elif [[ "$*" == api\ repos/RussoTurissto/BalanceHub/git/ref/tags/* ||
        "$*" == release\ view* ]]; then
  exit 1
else
  echo "Unmocked gh command: $*" >&2
  exit 3
fi
MOCK
chmod +x "$temp/bin/gh"

run_scenario() {
  local scenario="$1" stable="$2" approved="$3"
  local envfile="$temp/$scenario-$stable.env"
  (
    cd "$temp"
    SCENARIO="$scenario" PATH="$temp/bin:$PATH" \
    GITHUB_REPOSITORY="RussoTurissto/BalanceHub" \
    GITHUB_EVENT_NAME="$([[ "$stable" == "true" ]] && echo workflow_dispatch || echo push)" \
    INPUT_PUBLISH_STABLE="$stable" INPUT_APPROVED_VERSION="$approved" \
    GITHUB_ENV="$envfile" GITHUB_STEP_SUMMARY="$temp/summary" \
    bash scripts/prepare-release-version.sh >/dev/null
  )
  cat "$envfile"
}

first="$(run_scenario first false "")"
[[ "$first" == *"BUILD_VERSION_NAME=1.2.1-rc1"* ]]
[[ "$first" == *"RELEASE_CHANNEL=rc"* ]]
[[ "$first" == *"PREVIOUS_RELEASE_TAG=v1.2.0"* ]]
next="$(run_scenario next false "")"
[[ "$next" == *"BUILD_VERSION_NAME=1.2.1-rc3"* ]]
[[ "$next" == *"RELEASE_TAG=v1.2.1-rc3"* ]]
[[ "$next" == *"PREVIOUS_RELEASE_TAG=v1.2.1-rc2"* ]]
stable="$(run_scenario next true 1.2.1)"
[[ "$stable" == *"BUILD_VERSION_NAME=1.2.1"* ]]
[[ "$stable" == *"RELEASE_CHANNEL=stable"* ]]
[[ "$stable" == *"PREVIOUS_RELEASE_TAG=v1.2.0"* ]]

if (
  cd "$temp"
  SCENARIO=next PATH="$temp/bin:$PATH" \
  GITHUB_REPOSITORY="RussoTurissto/BalanceHub" \
  GITHUB_EVENT_NAME=workflow_dispatch \
  INPUT_PUBLISH_STABLE=true INPUT_APPROVED_VERSION=1.3.0 \
  GITHUB_ENV="$temp/fail.env" GITHUB_STEP_SUMMARY="$temp/summary" \
  bash scripts/prepare-release-version.sh >/dev/null 2>&1
); then
  echo "Stable release without exact matching approval was accepted" >&2
  exit 1
fi

echo "Release versioning smoke tests passed: rc1, rc3, manual stable, mismatch rejection"

# Test the release guard with an actual local Git history (no network).
fixture="$temp/source-check"
mkdir -p "$fixture/app/src/main" "$fixture/scripts"
git init -q "$fixture"
git -C "$fixture" config user.email "tests@example.invalid"
git -C "$fixture" config user.name "BalanceHub CI"
printf 'original app\n' > "$fixture/app/src/main/Screen.kt"
git -C "$fixture" add .
git -C "$fixture" commit -qm "Original app"
git -C "$fixture" tag v1.2.0
git clone -q --bare "$fixture" "$temp/source-origin.git"
git -C "$fixture" remote add origin "$temp/source-origin.git"

run_guard() {
  local sha="$1" channel="${2:-rc}"
  (
    cd "$fixture"
    RELEASE_CHANNEL="$channel" PREVIOUS_RELEASE_TAG=v1.2.0 \
    GITHUB_REF=refs/heads/main GITHUB_SHA="$sha" \
    bash "$repo_root/scripts/require-app-changes.sh"
  )
}

printf 'changed workflow only\n' > "$fixture/scripts/ci.sh"
git -C "$fixture" add .
git -C "$fixture" commit -qm "Only infrastructure changed"
infra_sha="$(git -C "$fixture" rev-parse HEAD)"
if run_guard "$infra_sha" >/dev/null 2>&1; then
  echo "RC incorrectly allowed after infrastructure-only changes" >&2
  exit 1
fi
if run_guard "$infra_sha" stable >/dev/null 2>&1; then
  echo "Stable incorrectly allowed after infrastructure-only changes" >&2
  exit 1
fi

printf 'new app behavior\n' >> "$fixture/app/src/main/Screen.kt"
git -C "$fixture" add .
git -C "$fixture" commit -qm "Application changed"
app_sha="$(git -C "$fixture" rev-parse HEAD)"
run_guard "$app_sha" >/dev/null
run_guard "$app_sha" stable >/dev/null

printf 'original app\n' > "$fixture/app/src/main/Screen.kt"
git -C "$fixture" add .
git -C "$fixture" commit -qm "Application reverted"
reverted_sha="$(git -C "$fixture" rev-parse HEAD)"
if run_guard "$reverted_sha" >/dev/null 2>&1; then
  echo "RC incorrectly allowed when application source was reverted" >&2
  exit 1
fi

echo "Release-source gate tests passed: infra-only RC/stable rejected, app change RC/stable accepted, reverted app rejected"
