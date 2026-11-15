# BalanceHub — troubleshooting

## Signed APK does not install as an update

1. Confirm you are installing an **owner-approved signed** `BalanceHub.apk`, not a debug APK.
2. Verify the Android package is `com.github.russoturissto.buttonapp.app`.
3. Check that the new APK `versionCode` exceeds the installed version; do not rely on the displayed version name.
4. Verify that the APK uses the **same signing certificate** as the previously installed release.
5. Never uninstall the application as a routine fix: doing so can delete locally stored reminders and account settings.

The signed-build workflow checks the APK's signature and package metadata. Signing secrets must never appear in the repository or issue logs.

## Notification arrives without sound or vibration

- Check the per-category configuration in BalanceHub (payments, events or updates).
- Confirm notification permission, the relevant Android channel, volume, silent mode and Do Not Disturb settings.
- Use the built-in diagnostic test and record the physical result on the phone. Emulator checks cannot establish actual speaker or motor behavior.
- If the notification is delayed, review Android background restrictions and exact-alarm settings; delivery with the screen locked remains a separate handset test.

The latest owner-confirmed results are in [docs/QA_BUILD_701.md](docs/QA_BUILD_701.md). The rc5 investigation is kept in [docs/android-notification-channels.md](docs/android-notification-channels.md).

## Build fails

- Use Java 17 and the checked-in Gradle wrapper.
- Run `./gradlew :app:compileReleaseKotlin :app:lintRelease :app:testDebugUnitTest :app:assembleDebugAndroidTest`.
- Inspect the GitHub Actions logs for the **Build BalanceHub update APK** or **Validate notification sound and vibration on Android emulators** workflows.
- Do not try to bypass lint, signature verification or test failures when publishing a release.

## Repository / release recovery

- Historical source snapshot for confirmed build 701: `4749759a73a95461bc729ae04bd9ab9d233dd98a`.
- Backup branches: `backup/balancehub-701-verified-notifications` and `backup/balancehub-688-verified-payments`.
- Do not delete historical tags or draft release APKs as a cleanup shortcut.

For maintainers, see [docs/REPOSITORY_MAINTENANCE.md](docs/REPOSITORY_MAINTENANCE.md).
