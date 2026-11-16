# BalanceHub: private APK delivery to ChatGPT

## How it works

1. GitHub Actions builds, signs and verifies the APK as before.
2. A GitHub Draft Pre-release is kept as a private fallback.
3. With the optional Actions Secret BALANCEHUB_DRIVE_RCLONE_CONFIG_B64,
   GitHub Actions uploads the signed APK directly to the account's Google Drive
   using a restricted rclone OAuth connection (drive.file scope).
4. Rclone creates its own private folder, BalanceHub-Private-Delivery-Inbox.
5. ChatGPT uses the already connected Google Drive to retrieve that APK and
   its SHA-256 checksum, verifies the binary in its working container, then
   attaches the APK directly as a downloadable file in the conversation.
6. The APK may then be moved to the private Drive archive:
   BalanceHub — закрытые тестовые APK.

Never use upload-artifact for APKs in the PUBLIC BalanceHub repo, since
public-repository readers can download its Actions artifacts. Existing
Draft Releases remain private and should not be published for this feature.

## One-time Android / Termux setup

An OAuth credential is necessary for Github Actions to upload to Drive.
Never send tokens, rclone config, secret values, or screenshots of them in chat.

1. Install rclone on your Android phone in Termux:

       pkg update
       pkg install rclone

2. Create a separate one-remote config file:

       rclone --config "$HOME/balancehub-apks-rclone.conf" config

   In the interactive wizard:
   - New remote (n), name: balancehub_apks
   - Storage: Google Drive (drive)
   - OAuth client ID/secret: leave blank (rclone defaults)
   - OAuth scope: drive.file (ONLY files created by rclone)
   - Root folder ID and service-account credentials: leave blank
   - Authenticate in the browser using the Google account connected to
     ChatGPT's Google Drive; save the remote.
   - Ensure this file contains no other remotes.

3. Verify that rclone can create its private inbox:

       rclone --config "$HOME/balancehub-apks-rclone.conf" mkdir balancehub_apks:BalanceHub-Private-Delivery-Inbox
       rclone --config "$HOME/balancehub-apks-rclone.conf" lsd balancehub_apks:

4. Generate one-line base64 of this single-remote config:

       base64 -w 0 "$HOME/balancehub-apks-rclone.conf"

   Do NOT send this value to ChatGPT. In your repository's private
   Settings > Secrets and variables > Actions > New repository secret,
   save this output as:

       BALANCEHUB_DRIVE_RCLONE_CONFIG_B64

5. On the next signed main-branch build the workflow uploads a uniquely
   named APK such as BalanceHub-1.1.4-rc21-buildNNN.apk and a SHA-256 file
   into the private rclone-created Drive folder.

## Security and verification

- The workflow validates that the config contains ONLY the
  balancehub_apks remote, type=drive, scope=drive.file.
- The OAuth token must actually be authorized with drive.file, not drive.
  A scope entry in a config is not proof that an over-privileged OAuth token
  was not obtained earlier.
- Upload integrity is checked by MD5 from the remote Drive file; SHA-256
  is written to a separate manifest for verification by ChatGPT.
- If the optional Secret is missing, private Drive delivery is NOT enabled
  yet. The workflow continues to save the signed APK in the existing private
  Draft Release instead; it does not disclose a public APK.
- If credentials are present but upload fails, the Actions job fails and
  the issue should be diagnosed before trying in-chat delivery.
- Standalone Google service accounts cannot own new files in consumer
  My Drive because they lack personal Drive storage quota. Use OAuth.
- With drive.file, rclone cannot see the private archive folder created
  by ChatGPT's Drive connector; therefore it creates its OWN inbox.
  The connected Drive can subsequently move the APK to the private archive.
- GitHub Actions Secrets must NEVER be pasted into code, issues, or chat.
- Do not weaken Drive permissions to public/anyone-with-link.

## How ChatGPT attaches an APK

For a request such as "Пришли APK сборки 708":
1. Verify GitHub signed APK build success.
2. Find the APK and its .sha256 in connected Drive, checking the build number.
3. Download the complete RAW file into the current conversation's container.
4. Verify file size, APK ZIP structure and SHA-256 against the manifest.
5. Return a sandbox download link to that exact verified APK file.
6. Install the APK over the old app, never uninstall it.

This is a user-invoked, in-chat file transfer. No claim of background
delivery or persistent automatic access outside the requested conversation.
