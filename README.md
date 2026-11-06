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
