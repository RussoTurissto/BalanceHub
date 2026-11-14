# Android notification channels in BalanceHub 1.1.4-rc5

Android 8+ owns sound, vibration and importance of an existing notification
channel. Calling `createNotificationChannel` again with the same ID cannot
overwrite them. BalanceHub checks `NotificationManager.getNotificationChannel`,
not merely the tone and vibration stored in local app preferences.

## OEM compatibility diagnostics (rc5)

The real handset reported Android Payments sound=None and vibration=OFF,
even immediately after the app read the newly created channel as sound=on,
vibration=on. A direct motor test did vibrate. This indicates a mismatch
between the Android notification pipeline / OEM settings UI and the app,
but it does not identify which component changed the state.

- Each explicit selection or repair now gets a **UUID-suffixed, never reused**
  channel ID, stored atomically alongside user preferences. This avoids Android
  resurrecting deleted channel IDs and their old user-muted settings. Legacy
  deterministic rc4 IDs still work until an explicit reconfiguration.
- The app shows the exact Android channel ID and any extra IDs in its
  engineering diagnostics, so a wrong OEM channel-screen target can be found.
- Repair is checked again after a delay or when the app regains window focus.
  UI text distinguishes an Android API read-back from proven physical playback.
- **Чистый тест Android** creates a separate short-lived diagnostic category
  with the device's default notification sound and default vibration
  (no custom WAV and no custom pattern). It does not modify Payment, Event,
  or Update channels. A new unique diagnostic ID is used per manual test,
  and the previous probe is retired. The exact test ID has its own Settings
  deep link and can be inspected after returning to BalanceHub.
- A failed independent probe suggests an OEM/global notification issue;
  success for the probe but failure for a custom channel points to the custom
  sound/pattern/channel configuration. Neither result is a guarantee for a
  particular future notification.

## User changes and conflict repair

- Choosing a tone, bundled-WAV volume or vibration inside BalanceHub creates
  a new versioned channel if settings changed. The app reads back Android's
  actual signal configuration, saves app preferences and only then retires the
  old channel. On failure the old channel and saved settings remain.
- Opening a notification category or its real notification test compares the
  actual Android sound, vibration, pattern and channel importance with saved
  BalanceHub preferences. An alternate audible ringtone is a *difference*,
  not mistaken for the absence of sound.
- A direct, **user-confirmed** action can apply the chosen signals to a new
  channel. The test can rebuild the channel and post a new test notification,
  send the test unchanged or open the system channel settings.
- BalanceHub never resets a channel merely because the app launches or
  upgrades. Android's notification permission, user-muted channels,
  Do Not Disturb, device sound profile and system volume remain authoritative.
- Payment/event data, scheduled alarms and app update preferences survive
  rebuilding their notification channels.

## Manual device QA

For the reported OPPO/ColorOS-family UI, inspect the actual Android toggle
**Вибрировать** and the selected sound **Звонить** in the **specific active ID**
printed by engineering diagnostics, not just the in-app summary.

0. Run **Чистый тест Android** and compare the platform's default ringtone
   and vibration in the newly opened test channel. Record whether the physical
   vibration is felt, whether the switch is ON, and whether it persists after
   returning to the app.


1. Install the signed APK on top of the prior version; do not uninstall.
2. In BalanceHub → Settings → Notifications → Payments, choose a melody and
   vibration. Run **Проверить уведомление** and check real phone output.
3. In Android's Payments channel set the sound to None and disable vibration.
   Return to BalanceHub. Test must report both differences.
4. Use **Восстановить**, confirm replacement, and verify the new test notification
   produces the selected sound and vibration on the handset.
5. Repeat for Events and Updates. Check an alternate Android ringtone,
   vibration pattern, silent mode, and app upgrade without re-selection.
6. A test emulator can verify Android channel metadata but not actual physical
   speaker and vibration output on the OEM device.

## References

- https://developer.android.com/develop/ui/views/notifications/channels
- https://developer.android.com/reference/android/app/NotificationChannel
- https://github.com/element-hq/element-x-android/issues/6672
- https://github.com/signalapp/Signal-Android/issues/13118
- https://docs.ntfy.sh/subscribe/phone/
