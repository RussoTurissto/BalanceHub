#!/usr/bin/env bash
# Offline tests of monotonic RC numbering and explicit stable approval.
set -euo pipefail
bash -n scripts/prepare-release-version.sh
bash -n scripts/publish-verified-release.sh
bash -n scripts/test-release-versioning.sh

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
next="$(run_scenario next false "")"
[[ "$next" == *"BUILD_VERSION_NAME=1.2.1-rc3"* ]]
[[ "$next" == *"RELEASE_TAG=v1.2.1-rc3"* ]]
stable="$(run_scenario next true 1.2.1)"
[[ "$stable" == *"BUILD_VERSION_NAME=1.2.1"* ]]
[[ "$stable" == *"RELEASE_CHANNEL=stable"* ]]

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
