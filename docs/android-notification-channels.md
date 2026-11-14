# Android notification channels in BalanceHub 1.1.4-rc4

Android 8+ owns sound, vibration and importance of an existing notification
channel. Calling `createNotificationChannel` again with the same ID cannot
overwrite them. BalanceHub checks `NotificationManager.getNotificationChannel`,
not merely the tone and vibration stored in local app preferences.

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
