# BalanceHub

BalanceHub is an Android app for mobile balances, banking Push balance cards, payment reminders and calendar events. The Android package ID and signing certificate stay unchanged between APK updates.

## Features

- Local payment reminders and recurring calendar events with reminders and archives.
- At most one reminder per item and local calendar day.
- Mobile phone balance information where supported, and read-only banking Push balance cards.
- Light, dark and system theme; engineer-only diagnostics.

## Android notifications

On Android 8 and newer, BalanceHub maintains exactly three permanent system channels:

| Category | System channel ID |
| --- | --- |
| Payments | balancehub.channel.payment |
| Events | balancehub.channel.event |
| Updates | balancehub.channel.update |

Changing melody, vibration or importance for an existing Android channel must happen in its Android settings. BalanceHub displays the actual setting and opens that screen. It does not override a user-muted channel, Do Not Disturb or system sound modes. The first upgrade migrates from previously generated tone/volume channel variants, deleting only old BalanceHub variants. Saved events and payments are not modified by channel migration.

Reminder scheduling uses AlarmManager. Exact-alarm permission improves timing; without it, Android may delay a reminder. Recently completed events can still deliver their previously scheduled notifications within a bounded three-hour catch-up window. Completed or deleted events cannot generate notifications indefinitely.

## Building and testing

- Android source: `app/`; auxiliary `library-*` modules are required Gradle dependencies.
- Java 17, Android SDK, Gradle Kotlin DSL; local debug build: `./gradlew :app:assembleDebug`.
- Canonical signed APK workflow: `.github/workflows/balancehub-apk.yml`. It builds on pushes to `main` only when runtime app sources change; manual RC attempts are also blocked if the app has not changed since the last published release. A separate fast workflow checks release infrastructure without producing an APK.
- Emulator tests: `.github/workflows/notification-emulator-validation.yml` (API 29, 35 and 36).
- CI checks compilation, Android lint, JVM tests, signing certificate and APK package/version metadata. Successful CI does not replace physical OnePlus/ColorOS testing.
- Four `BALANCEHUB_*` signing secrets remain stored only in GitHub Actions. Never commit keystores, passwords or real account credentials.

## Installing an update

Install the signed `BalanceHub.apk` **over** the existing Android app. Do not uninstall it: the application ID (`com.github.russoturissto.buttonapp.app`) and release signing key must stay unchanged, and the new `versionCode` must be greater than the installed one.

The latest approved stable version is **1.2.0**. Starting with **1.2.1-rc1**, test builds are published as **public GitHub Pre-releases** with direct signed APK links. Only explicitly approved stable releases are published as **Latest** and offered through the in-app updater. See [release workflow](docs/RELEASE_PROCESS.md).

## Repository and QA

- Maintenance, protected checkpoints and release rules: [docs/REPOSITORY_MAINTENANCE.md](docs/REPOSITORY_MAINTENANCE.md)
- Manually confirmed build 701 checks and pending tests: [docs/QA_BUILD_701.md](docs/QA_BUILD_701.md)
- Architecture and provider approach: [docs/BALANCEHUB_ARCHITECTURE.md](docs/BALANCEHUB_ARCHITECTURE.md)
- Notification/channel history: [docs/android-notification-channels.md](docs/android-notification-channels.md)
- Troubleshooting: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- Historical release notes remain in `release/notes-*.md`; older releases, tags and rollback branches are retained for traceability.
