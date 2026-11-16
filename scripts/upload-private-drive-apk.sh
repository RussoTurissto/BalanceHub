#!/usr/bin/env bash
# Private signed APK delivery. Requires a dedicated rclone Drive remote
# authorized with the restricted drive.file scope, never full Drive access.
set -euo pipefail

if [[ -z "$PRIVATE_DRIVE_RCLONE_CONFIG_B64" ]]; then
  echo "::notice::Private Google Drive delivery is not connected yet; the signed APK remains in the private Draft Release."
  exit 0
fi

config="$RUNNER_TEMP/balancehub-private-rclone.conf"
printf '%s' "$PRIVATE_DRIVE_RCLONE_CONFIG_B64" | base64 --decode > "$config"
chmod 600 "$config"
trap 'rm -f "$config"' EXIT

# Avoid accidentally granting CI access to unrelated Drive contents.
# The OAuth token must have been issued with this restricted scope.
if [[ "$(grep -cE '^\[[^]]+\]' "$config" || true)" -ne 1 ]] ||
   ! grep -qE '^\[balancehub_apks\][[:space:]]*$' "$config" ||
   ! grep -qE '^[[:space:]]*type[[:space:]]*=[[:space:]]*drive[[:space:]]*$' "$config" ||
   ! grep -qE '^[[:space:]]*scope[[:space:]]*=[[:space:]]*drive\.file[[:space:]]*$' "$config"; then
  echo "::error::Expected one [balancehub_apks] Drive remote with scope=drive.file."
  exit 1
fi

if ! command -v rclone >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq rclone
fi

apk="app/build/outputs/apk/release/BalanceHub.apk"
[[ -s "$apk" ]] || { echo "::error::Signed APK is missing"; exit 1; }
code="$((GITHUB_RUN_NUMBER + 300))"
filename="BalanceHub-$BUILD_VERSION_NAME-build$code.apk"
inbox="balancehub_apks:BalanceHub-Private-Delivery-Inbox"

# A drive.file OAuth client cannot see folders created by other clients.
# Its own private inbox can be viewed by the user's connected Google Drive.
rclone --config "$config" mkdir "$inbox"
if rclone --config "$config" lsf "$inbox" --files-only | grep -Fxq "$filename"; then
  echo "::error::Private APK already exists; refusing overwrite."
  exit 1
fi
rclone --config "$config" copyto "$apk" "$inbox/$filename" --retries 3

local_md5="$(md5sum "$apk" | awk '{print $1}')"
remote_md5="$(rclone --config "$config" md5sum "$inbox/$filename" | awk 'NR == 1 {print $1}')"
if [[ -z "$remote_md5" || "$local_md5" != "$remote_md5" ]]; then
  echo "::error::Private Drive upload failed checksum verification."
  exit 1
fi

sha256="$(sha256sum "$apk" | awk '{print $1}')"
printf '%s  %s\n' "$sha256" "$filename" > "$RUNNER_TEMP/$filename.sha256"
rclone --config "$config" copyto "$RUNNER_TEMP/$filename.sha256" "$inbox/$filename.sha256"

echo "Private Google Drive APK: $filename (SHA-256 $sha256)" >> "$GITHUB_STEP_SUMMARY"
echo "::notice::Signed APK delivered to the private Drive inbox, not to public Actions artifacts."
