# BalanceHub repository maintenance

## Source of truth

- `main`: current repository baseline for review and manual builds.
- `feature/balancehub-foundation`: historical development/integration branch. Retain it until all pending work has been reconciled with `main`.
- `backup/balancehub-701-verified-notifications`: exact owner-tested build 701 source snapshot, commit `4749759a73a95461bc729ae04bd9ab9d233dd98a`.
- `backup/balancehub-688-verified-payments`: last separately verified payments baseline.
- Other historical branches and tags should be reviewed before archival or deletion, not removed blindly.

## Active GitHub Actions

1. `.github/workflows/balancehub-apk.yml` — signed APK build and owner-only draft prerelease. Development-branch pushes trigger builds; `workflow_dispatch` is available from the default branch. The workflow currently uses `github.run_number + 300` as `versionCode`, which must always exceed the last installed build.
2. `.github/workflows/notification-emulator-validation.yml` — Android API 29/35/36 instrumented tests.

One-off rc4/rc5 publishers and the old draft-migration workflow were removed from the current source tree after their releases. They remain recoverable through Git history.

## Preserve update compatibility

- Do **not** rename `applicationId` `com.github.russoturissto.buttonapp.app` merely to remove legacy package naming. This would create a different Android app.
- Do **not** rotate the release signing key during repository cleanup.
- Never commit `.jks`, `.keystore`, release passwords or user financial data.
- Confirm APK signature and increasing `versionCode` before offering a new version.
- Keep release notes, Git tags and the previous approved APKs to support rollback and diagnosis.
- A draft prerelease is owner-only; it is not a public download or a production release.

## Safeguards for future changes

- Preserve the owner-confirmed behavior of build 701: payment, event and update test notifications, melodies and vibration.
- The handset's **locked-screen/background** event and update delivery has not yet been confirmed for build 701. Do not claim otherwise.
- Run JVM, Android lint and emulator tests for code changes; test physical notifications on the handset before calling a new release verified.
- For APK build/publishing changes, verify source SHA, `applicationId`, version, certificate and artifact retention.
- The separate `SettingsButtonApp/` sample project was not part of the root Gradle build. Its history remains available in earlier commits, while the root `app` and all required `library-*` modules are retained.
- Do not rewrite published Git history or force-push `main` as part of housekeeping.

## Repository layout

- `app/` — BalanceHub Android app, resources and tests
- `library-android/`, `library-compose/`, `library-kotlin/` — referenced Gradle modules
- `gradle/`, `buildSrc/`, `config/` — build configuration
- `.github/workflows/` — active automation
- `docs/` — architecture, maintenance and test reports
- `release/` — historical release notes
