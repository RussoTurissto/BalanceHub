# BalanceHub — architecture and integration plan

## Product goal
BalanceHub is an Android-first personal dashboard for mobile balances (MTS, Tele2 and extensible providers), bank account balances across multiple banks, and recurring bill reminders. Expense/income ledger is intentionally out of scope.

## Existing repository audit
- Repository: `RussoTurissto/ButtonApp`, default branch `main`, Kotlin Android Gradle project.
- Main application module: `app/`, package/application ID: `com.github.russoturissto.buttonapp.app`.
- Current launcher: a minimal AppCompat Activity with one demo button.
- Build: Gradle Kotlin DSL, Kotlin 2.4.21, AGP 8.13.2, compile/target SDK 36, min SDK 23, Java 17.
- Existing template includes additional sample modules (`library-android`, `library-compose`, `library-kotlin`) and GitHub Actions. Keep these intact during the first integration.
- A legacy nested sample project exists at `SettingsButtonApp/`; it is not the active root Gradle application and should not be modified as part of BalanceHub.

## Architecture
Use a feature package inside the existing `app` module first. Avoid introducing a second Android application or changing the application ID.

Layers:
1. **UI (Jetpack Compose)** — dashboard, operator accounts, bank accounts, upcoming payments, edit/add flows, settings. Material 3; Russian-first strings; light/dark themes.
2. **Presentation (ViewModel + UI state)** — screen state, validation, loading/error/last-updated states.
3. **Domain** — account and payment models, balance aggregation, due-date calculations, provider contracts.
4. **Data** — Room database as the source of truth; repositories; encrypted storage only for integration tokens if a future provider requires them.
5. **Sync** — WorkManager periodic/background refresh with per-provider status, retry/backoff and clear last-success timestamp.
6. **Notifications** — scheduled reminders for upcoming recurring payments. Request notification permission on Android 13+ and explain why it is needed.

## Data model principles
- Store money as integer minor units (kopecks/cents), never floating point.
- Every balance has a currency code and an observation timestamp.
- Distinguish a fresh provider balance from a manually entered balance.
- Keep bank and mobile accounts as separate account types but show a consolidated total only when currencies match. Never silently add RUB and EUR.
- Recurring payment records contain amount, currency, billing day/date, reminder lead time, active flag and optional linked account.
- Store phone numbers masked in the dashboard; allow full number only in the edit screen.

## Provider integration strategy
- Define provider adapters behind interfaces. Start with manual fallback and provider capability detection.
- MTS/Tele2: automate only through documented, permitted operator APIs or official account integrations. Do not scrape private pages, intercept app traffic, or store operator passwords.
- Banks: prefer official Open Banking/PSD2 integrations where the bank and user's country support them. Use read-only scopes. Otherwise offer manual balance entry or statement import.
- A provider must report unsupported, authentication-required, stale, and temporary-error states separately; do not display stale values as current.
- Do not promise that every provider can be refreshed automatically. Availability depends on the provider, region, and account permissions.

## Privacy and security
- No bank passwords, SMS one-time codes, or card CVV values.
- Keep local data on-device by default. Do not add analytics or cloud sync without explicit product decision.
- Use Android Keystore-backed encryption for any future access tokens; never commit secrets or test credentials.
- Provide export/delete controls before public release.

## Delivery milestones
- **M0 — foundation (this branch):** repository audit, domain model, architecture and provider policy.
- **M1 — usable local MVP:** Compose dashboard, add/edit mobile and bank accounts, manual balance values, recurring payments, local persistence.
- **M2 — reminders:** notification channel, exact/inexact scheduling policy, WorkManager reconciliation after reboot/time changes.
- **M3 — integrations:** evaluate official MTS/Tele2 and bank APIs individually; implement only supported read-only adapters.
- **M4 — release hardening:** tests, accessibility, localization, backup/export, privacy review, signed APK/AAB.

## Integration constraints
The current app uses a legacy XML/AppCompat launcher. The recommended migration is incremental: add Compose dependencies and a Compose dashboard entry point, then replace the demo launcher only after the new screen and smoke tests compile. Keep existing template modules and CI until the app build is verified.
