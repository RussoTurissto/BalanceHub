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

## Build and release process

- Kotlin, Android SDK, Gradle Kotlin DSL, Java 17.
- Main project: app. Build locally with ./gradlew :app:assembleDebug.
- Canonical CI workflow: .github/workflows/balancehub-apk.yml.
- CI checks compilation, lint, JVM unit tests, signed release APK, package name, Android version and signer certificate.
- The four BALANCEHUB_* signing secrets remain private in GitHub Actions. Never commit a keystore or password.
- Approved publishing workflow: .github/workflows/publish-approved-apk.yml.
- Only after the owner authorizes a version and its successful CI run, update release/approved.json and release/notes.md. The publisher verifies the specific run, APK signature and version, then creates a GitHub prerelease without replacing the stable latest release.

## Installing on Android

Download BalanceHub.apk directly from an approved GitHub prerelease/release page. Open it and update the app without uninstalling. A newer monotonically increasing versionCode and the existing signing certificate preserve update compatibility.

## Repository maintenance

The active Android application is the root app module. Obsolete ButtonApp-only and inactive Maven publishing workflows were removed from the GitHub Actions configuration. Historical Git commits, releases and tags are retained for safe rollback.

See docs/BALANCEHUB_ARCHITECTURE.md for architectural background.
