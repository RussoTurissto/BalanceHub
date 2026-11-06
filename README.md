# BalanceHub

Android app for tracking mobile and bank account balances and upcoming payments.

## Current MVP

- The Android application and Gradle root project are named **BalanceHub**.
- Add payment reminders manually: title, amount, ISO currency code, due date and reminder lead time (0–30 days).
- Reminders are stored locally on the device and can be deleted from the upcoming-payments list.
- Android schedules a local notification for the selected reminder date. Android 13+ asks for notification permission.
- Initial mobile-account targets: MTS and Tele2; multiple bank accounts are in the product scope.
- No income/expense ledger.

Provider integrations are not active yet. Automatic balance refresh will be implemented only through official, permitted APIs; manual entry remains available otherwise.

## Technology

- Kotlin, Android, Gradle Kotlin DSL, Java 17.
- Existing application ID and package are preserved.
- Local reminder storage currently uses Android SharedPreferences; the data layer can be migrated to Room as the MVP expands.
- Payment notifications use Android AlarmManager and NotificationCompat.

## Repository status

The feature/balancehub-foundation branch contains the initial BalanceHub implementation.

- [Architecture and integration plan](docs/BALANCEHUB_ARCHITECTURE.md)
- [Provider capability matrix](docs/BALANCEHUB_PROVIDER_MATRIX.md)
- [Domain models](app/src/main/java/com/github/russoturissto/buttonapp/balancehub/domain/Models.kt)

## Build

Open the repository in Android Studio and run the app configuration. The repository's existing Gradle wrapper and CI setup are retained.

## Security

- Do not store bank passwords, SMS one-time codes or card CVV values.
- Prefer read-only official bank integrations.
- Keep personal data on-device by default.
- Display the timestamp and source of every balance.
- Never present stale or manually entered data as a live provider result.

## Roadmap

1. Foundation and domain model.
2. Manual payment reminders with local persistence and Android notifications.
3. Balance dashboard and manual mobile/bank account management.
4. Official provider integrations, where available.
5. Tests, privacy review and release build.


## Installing updates without reinstalling

BalanceHub release APKs must always use the same private signing keystore. The application ID is intentionally kept stable. The signed APK workflow increments `versionCode` for every run and uploads an installable APK artifact.

Before the first signed release, create a private upload keystore on a trusted computer and keep a secure backup:

```bash
keytool -genkeypair -v -keystore balancehub-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias balancehub
```

Add these GitHub Actions repository secrets in **Settings → Secrets and variables → Actions**:

- `BALANCEHUB_KEYSTORE_BASE64` — base64-encoded contents of `balancehub-upload.jks`
- `BALANCEHUB_KEYSTORE_PASSWORD` — keystore password
- `BALANCEHUB_KEY_ALIAS` — `balancehub` (or the alias chosen during key generation)
- `BALANCEHUB_KEY_PASSWORD` — private-key password

To create the base64 value:
- macOS/Linux: `base64 -w 0 balancehub-upload.jks` (on macOS, use `base64 balancehub-upload.jks | tr -d '\\n'`)
- PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("balancehub-upload.jks"))`

Never commit the keystore, passwords, or base64 value to Git. Losing the keystore means Android will not accept future APKs as updates to installations signed with that key.

After adding the secrets, run **Actions → Build BalanceHub update APK → Run workflow**. Download the `BalanceHub-...-signed-apk` artifact and install the APK. Later builds from this workflow use the same signing key and a higher version code.

If an earlier APK on the phone was signed with a different key (for example, a debug key), Android may require a one-time uninstall before installing the first APK signed with this release key. Back up any important app data first. After the first signed release is installed, subsequent APKs from this workflow install as updates and preserve app data.
