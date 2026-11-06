# BalanceHub

Android app for tracking mobile and bank account balances and upcoming recurring payments.

> BalanceHub is being integrated incrementally into the existing Kotlin Android project. Provider integrations are not active yet; automatic refresh will be implemented only through official, permitted APIs.

## Product scope

- Track multiple mobile accounts (initial targets: MTS and Tele2).
- Track bank accounts from multiple banks.
- Show balances with currency and last-updated time.
- Remind about recurring subscription fees and other scheduled payments.
- No income/expense ledger in the initial scope.
- Automatic refresh where a supported official integration exists; manual fallback otherwise.

## Technology

- Kotlin, Android, Gradle Kotlin DSL, Java 17.
- Existing application module and package are preserved.
- Planned UI: Jetpack Compose.
- Planned local persistence: Room.
- Planned background refresh: WorkManager.
- Planned payment reminders: Android notifications and scheduling APIs.

## Repository status

The `feature/balancehub-foundation` branch contains the initial architecture, provider policy and domain models.

- [Architecture and integration plan](docs/BALANCEHUB_ARCHITECTURE.md)
- [Provider capability matrix](docs/BALANCEHUB_PROVIDER_MATRIX.md)
- [Domain models](app/src/main/java/com/github/russoturissto/buttonapp/balancehub/domain/Models.kt)

## Build

Open the repository in Android Studio and run the `app` configuration. The repository's existing Gradle wrapper and CI setup are retained.

## Security

- Do not store bank passwords, SMS one-time codes or card CVV values.
- Prefer read-only official bank integrations.
- Keep local data on-device by default.
- Display the timestamp and source of every balance.
- Never present stale or manually entered data as a live provider result.

## Roadmap

1. Foundation and domain model.
2. Compose dashboard and local account/payment management.
3. Local persistence and payment reminders.
4. Official provider integrations, where available.
5. Tests, privacy review and release build.
