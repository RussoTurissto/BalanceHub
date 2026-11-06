# BalanceHub — architecture and integration plan

## Product goal
BalanceHub is an Android-first personal dashboard for mobile balances (MTS, Tele2 and extensible providers), bank account balances across multiple banks, and payment reminders. Expense/income ledger is intentionally out of scope.

## Existing repository audit
- Repository: RussoTurissto/ButtonApp, default branch main, Kotlin Android Gradle project.
- Android app display name and Gradle root project name: BalanceHub.
- Main application module: app/, package/application ID: com.github.russoturissto.buttonapp.app (preserved).
- Build: Gradle Kotlin DSL, Kotlin 2.4.21, AGP 8.13.2, compile/target SDK 36, min SDK 23, Java 17.
- Existing template includes additional sample modules (library-android, library-compose, library-kotlin) and GitHub Actions. Keep these intact during the first integration.
- A legacy nested sample project exists at SettingsButtonApp/; it is not the active root Gradle application.

## Implemented in the foundation branch
- BalanceHub application label and Gradle root project name.
- Manual payment reminder form: title, amount, three-letter currency, due date and reminder lead time (0–30 days).
- Local persistence of reminders using SharedPreferences, list display and deletion.
- Local Android notification scheduling through AlarmManager and a notification receiver. Android 13+ notification permission is requested after a reminder is saved.

## Target architecture
1. UI — dashboard, operator accounts, bank accounts, upcoming payments, edit/add flows, settings. Russian-first strings; light/dark themes.
2. Presentation — screen state, validation, loading/error/last-updated states.
3. Domain — account and payment models, balance aggregation, due-date calculations, provider contracts.
4. Data — migrate the initial SharedPreferences reminder store to Room as the account and payment features grow; repositories.
5. Sync — WorkManager periodic/background refresh with per-provider status, retry/backoff and clear last-success timestamp.
6. Notifications — scheduled reminders for upcoming payments; reconcile schedules after reboot and time changes.

## Data model principles
- Store money as integer minor units (kopecks/cents), never floating point.
- Every balance has a currency code and an observation timestamp.
- Distinguish a fresh provider balance from a manually entered balance.
- Show a consolidated total only when currencies match. Never silently add RUB and EUR.
- Payment records contain amount, currency, due date, reminder lead time, active flag and optional linked account.
- Mask phone numbers in the dashboard; allow full number only in the edit screen.

## Provider integration strategy
- Start with manual fallback and provider capability detection.
- MTS/Tele2: automate only through documented, permitted operator APIs or official account integrations. Do not scrape private pages, intercept app traffic, or store operator passwords.
- Banks: prefer official Open Banking/PSD2 integrations where supported. Use read-only scopes. Otherwise offer manual balance entry or statement import.
- Do not promise that every provider can be refreshed automatically; availability depends on provider, region and account permissions.

## Privacy and security
- No bank passwords, SMS one-time codes, or card CVV values.
- Keep local data on-device by default. Do not add analytics or cloud sync without explicit product decision.
- Use Android Keystore-backed encryption for any future access tokens; never commit secrets or test credentials.

## Delivery milestones
- M0 — foundation: repository audit, domain model, architecture and provider policy.
- M1 — payment reminder MVP: manual entry, local persistence, upcoming list and local notifications (initial implementation).
- M2 — balances: manual mobile and bank account management with local persistence.
- M3 — integrations: evaluate official MTS/Tele2 and bank APIs individually; implement only supported read-only adapters.
- M4 — release hardening: tests, accessibility, localization, backup/export, privacy review, signed APK/AAB.
