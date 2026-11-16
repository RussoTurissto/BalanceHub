package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * System-channel tests, run on real Android OS emulators.
 * These tests validate actual NotificationManager state, not UI preferences.
 */
@RunWith(AndroidJUnit4::class)
class NotificationChannelEmulatorTest {
    private val app: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun manager(): NotificationManager =
        app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test fun allThreeChannelsHaveEffectiveSoundAndVibrationOnFreshInstall() {
        val context = app
        BalanceNotificationSounds.ensureDefaultChannels(context)
        val channels = listOf(
            BalanceNotificationSounds.UPDATE, BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT)
        if (Build.VERSION.SDK_INT >= 26) {
            for (category in channels) {
                val channel = manager().getNotificationChannel(
                    BalanceNotificationSounds.channelId(context, category))
                assertNotNull("Channel missing " + category, channel)
                assertTrue("Importance silent " + category,
                    channel!!.importance >= NotificationManager.IMPORTANCE_DEFAULT)
                assertNotNull("Sound missing " + category, channel.sound)
                if (Build.VERSION.SDK_INT >= 29) {
                    assertEquals("System audio must use a MediaStore URI", "media",
                        channel.sound?.authority)
                    assertNull("MediaStore sound cannot be read",
                        BalanceNotificationSounds.audioProbe(context, channel.sound!!))
                }
                assertTrue("No vibration " + category, channel.shouldVibrate())
            }
        }
    }

    @Test fun allFifteenSoundsAreIndexedInAndroidMediaStore() {
        if (Build.VERSION.SDK_INT < 29) return
        val context = app
        assertEquals(15, BalanceNotificationSounds.tones.size)
        for (tone in BalanceNotificationSounds.tones) {
            val uri = BalanceSystemToneStore.uri(context, tone.key, 100)
            assertNotNull("Cannot register " + tone.key + " with Android", uri)
            assertEquals("Unexpected media provider for " + tone.key, "media",
                uri!!.authority)
            assertNull("System media read failed for " + tone.key,
                BalanceNotificationSounds.audioProbe(context, uri))
            assertEquals("MediaStore URI changed for " + tone.key, uri,
                BalanceSystemToneStore.uri(context, tone.key, 100))
        }
    }

    @Test fun notificationUsesSelectedChannelAfterRepeatedInitialization() {
        if (Build.VERSION.SDK_INT < 26) return
        val context = app
        val category = BalanceNotificationSounds.PAYMENT
        val applyError = BalanceNotificationSounds.applyUserChoice(
            context, category, "balancehub", true)
        assertNull("Cannot configure payment tone: " + applyError, applyError)
        BalanceNotificationSounds.ensureDefaultChannels(context)
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName,
                    android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val id = BalanceNotificationSounds.channelId(context, category)
        val notificationId = 479882
        val notification = NotificationCompat.Builder(context, id)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Тест реального уведомления BalanceHub")
            .setContentText("Звук и вибрация должны следовать параметрам канала")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager().notify(notificationId, notification)
        try {
            // NotificationManager posts asynchronously on recent Android.
            var posted: android.service.notification.StatusBarNotification? = null
            for (attempt in 1..25) {
                posted = manager().activeNotifications.firstOrNull {
                    it.id == notificationId
                }
                if (posted != null) break
                Thread.sleep(120L)
            }
            assertNotNull("Android did not post notification after 3 s", posted)
            assertEquals("Wrong channel used by real notification", id,
                posted!!.notification.channelId)
            val channel = manager().getNotificationChannel(id)
            assertNotNull(channel)
            assertNotNull("Posted channel has no sound", channel!!.sound)
            assertTrue("Posted channel has no vibration", channel.shouldVibrate())
        } finally {
            manager().cancel(notificationId)
        }
    }

    @Test fun aPreviouslySilentCategoryCanApplyBundledSoundOnExplicitSelection() {
        if (Build.VERSION.SDK_INT < 26) return
        val context = app
        val category = BalanceNotificationSounds.UPDATE
        val silent = BalanceNotificationSounds.applySettings(
            context, category, "silent", NotificationVibrationMode.OFF, 100)
        assertNull("Cannot start with silent channel: " + silent, silent)
        val silentChannel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(context, category))
        assertNotNull(silentChannel)
        assertNull("Silent configuration must have no sound", silentChannel!!.sound)

        val enabled = BalanceNotificationSounds.applySettings(
            context, category, "crystal", NotificationVibrationMode.DOUBLE, 90)
        assertNull("Cannot re-enable a saved silent channel: " + enabled, enabled)
        val restoredChannel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(context, category))
        assertNotNull(restoredChannel)
        assertNotNull("Restored custom sound was dropped by Android", restoredChannel!!.sound)
        assertTrue("Restored vibration is missing", restoredChannel.shouldVibrate())
        assertNull("Restored WAV cannot be opened",
            BalanceNotificationSounds.audioProbe(context, restoredChannel.sound!!))
    }

    @Test fun changingMelodyAndVibrationPersistsAfterSettingsRecreated() {
        val context = app
        val category = BalanceNotificationSounds.EVENT
        BalanceNotificationSounds.ensureDefaultChannels(context)
        if (Build.VERSION.SDK_INT < 26) return
        // Tests run in one installed app. A previous system-ringtone test may
        // have saved a 0% custom WAV gain, so this case must select its own
        // audible gain rather than inheriting another test's preference.
        val first = BalanceNotificationSounds.applySettings(context, category,
            "crystal", NotificationVibrationMode.DEFAULT, 100)
        assertNull("Initial tone failed: " + first, first)
        val firstChannelId = BalanceNotificationSounds.channelId(context, category)
        var channel = manager().getNotificationChannel(firstChannelId)
        assertNotNull(channel)
        assertTrue(channel!!.shouldVibrate())
        assertNotNull(channel.sound)
        assertNull("System URI unreadable",
            BalanceNotificationSounds.audioProbe(context, channel.sound!!))
        // Same code path as returning from Android Settings and the app's next launch.
        BalanceNotificationSounds.ensureDefaultChannels(context)
        channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(context, category))
        assertTrue("Vibration lost during reinitialization", channel!!.shouldVibrate())
        assertNotNull("Sound lost during reinitialization", channel.sound)
        val second = BalanceNotificationSounds.applyVibrationChoice(context, category, false)
        assertNull("Disable vibration failed: " + second, second)
        channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(context, category))
        assertFalse(channel!!.shouldVibrate())
        assertNotNull("Tone lost when disabling vibration", channel.sound)
        val third = BalanceNotificationSounds.applyVibrationChoice(context, category, true)
        assertNull("Reenable vibration failed: " + third, third)
        BalanceNotificationSounds.ensureDefaultChannels(context)
        channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(context, category))
        assertTrue("Vibration not restored", channel!!.shouldVibrate())
        assertNotNull("Tone not restored", channel.sound)
    }

    @Test fun explicitRepairRestoresSignalsWhenAppAndAndroidSettingsDiverged() {
        if (Build.VERSION.SDK_INT < 26) return
        val context = app
        val category = BalanceNotificationSounds.UPDATE
        val muted = BalanceNotificationSounds.applySettings(
            context, category, "silent", NotificationVibrationMode.OFF, 100)
        assertNull("Cannot prepare silent channel: " + muted, muted)
        val oldId = BalanceNotificationSounds.channelId(context, category)
        val before = manager().getNotificationChannel(oldId)
        assertNotNull(before)
        assertNull(before!!.sound)
        assertFalse(before.shouldVibrate())

        // Simulate an upgrade that preserved the user's desired app settings
        // while the OS channel remained silent. Saving a preference is NOT
        // allowed to silently replace the system channel.
        BalanceNotificationSounds.choose(context, category, "balancehub")
        BalanceNotificationSounds.setVibrationFor(context, category, true)
        assertEquals(oldId, BalanceNotificationSounds.channelId(context, category))
        assertNull(manager().getNotificationChannel(oldId)!!.sound)
        assertTrue(BalanceNotificationSounds.needsRepair(context, category))

        // Explicit consent creates a new channel, without reinstalling the app.
        val repaired = BalanceNotificationSounds.repairChannel(context, category)
        assertNull("Channel repair failed: " + repaired, repaired)
        val newId = BalanceNotificationSounds.channelId(context, category)
        assertTrue("A new channel generation is required", newId != oldId)
        val after = manager().getNotificationChannel(newId)
        assertNotNull(after)
        assertNotNull("Custom tone was dropped", after!!.sound)
        assertTrue("Vibration did not return", after.shouldVibrate())
        assertFalse("Repair still reported as needed",
            BalanceNotificationSounds.needsRepair(context, category))
    }

    @Test fun choosingToneAndVibrationInsideAppUpdatesActiveChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val category = BalanceNotificationSounds.PAYMENT
        val initial = BalanceNotificationSounds.applySettings(
            app, category, "balancehub", NotificationVibrationMode.LONG, 100,
            force = true)
        assertNull("Cannot set original tone: " + initial, initial)
        val before = BalanceNotificationSounds.channelId(app, category)
        val error = BalanceNotificationSounds.applySettings(
            app, category, "crystal", NotificationVibrationMode.DOUBLE, 75)
        assertNull("In-app selection did not apply to Android: " + error, error)
        val after = BalanceNotificationSounds.channelId(app, category)
        assertNotEquals("Changed sound must use new channel generation", before, after)
        val channel = manager().getNotificationChannel(after)
        assertNotNull(channel)
        assertNotNull("New channel has no sound", channel!!.sound)
        assertTrue("New channel has no vibration", channel.shouldVibrate())
        assertArrayEquals(NotificationVibrationMode.pattern(NotificationVibrationMode.DOUBLE),
            channel.vibrationPattern)
        assertTrue("Channel disagrees with app: " +
            BalanceNotificationSounds.signalIssues(app, category),
            BalanceNotificationSounds.signalIssues(app, category).isEmpty())
        assertNull("Old channel must be retired", manager().getNotificationChannel(before))
        BalanceNotificationSounds.ensureDefaultChannels(app)
        assertEquals(after, BalanceNotificationSounds.channelId(app, category))
    }

    @Test fun bothAndroidProbesKeepOwnIdsAndPaymentChannelUntouched() {
        if (Build.VERSION.SDK_INT < 26) return
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(app.packageName,
                    android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val paymentId = BalanceNotificationSounds.ensureChannel(
            app, BalanceNotificationSounds.PAYMENT)
        val system = AndroidSignalProbe.send(app, AndroidSignalProbe.Mode.SYSTEM)
        assertNotNull("System probe not created: " + system.report, system.id)
        val systemId = system.id!!
        assertTrue(StableNotificationChannels.isProbe(systemId))
        val defaultChannel = manager().getNotificationChannel(systemId)
        assertNotNull(defaultChannel)
        assertEquals(android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_NOTIFICATION), defaultChannel!!.sound)
        assertTrue("Default OS vibration was rejected", defaultChannel.shouldVibrate())
        assertNull("OS default probe must have no custom vibration pattern",
            defaultChannel.vibrationPattern)

        val explicit = AndroidSignalProbe.send(app, AndroidSignalProbe.Mode.PATTERN)
        assertNotNull("Explicit probe not created: " + explicit.report, explicit.id)
        val patternId = explicit.id!!
        assertNotEquals("Different tests need different channels", systemId, patternId)
        assertTrue(StableNotificationChannels.isProbe(patternId))
        val patternChannel = manager().getNotificationChannel(patternId)
        assertNotNull(patternChannel)
        assertTrue("Explicit vibration rejected", patternChannel!!.shouldVibrate())
        assertArrayEquals("Explicit waveform was not registered",
            AndroidSignalProbe.explicitPattern(), patternChannel.vibrationPattern)
        assertEquals("Both tests use the same OS notification sound",
            defaultChannel.sound, patternChannel.sound)

        val againSystem = AndroidSignalProbe.send(app, AndroidSignalProbe.Mode.SYSTEM)
        val againPattern = AndroidSignalProbe.send(app, AndroidSignalProbe.Mode.PATTERN)
        assertEquals("System ID must persist across test runs", systemId, againSystem.id)
        assertEquals("Pattern ID must persist across test runs", patternId, againPattern.id)
        assertNotNull("System channel unexpectedly deleted",
            manager().getNotificationChannel(systemId))
        assertNotNull("Pattern channel unexpectedly deleted",
            manager().getNotificationChannel(patternId))
        assertEquals("Test modified a payment channel", paymentId,
            BalanceNotificationSounds.channelId(app, BalanceNotificationSounds.PAYMENT))
    }

    @Test fun newProbeModesKeepSeparateIdsAndExactWaveforms() {
        if (Build.VERSION.SDK_INT < 26) return
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(app.packageName,
                    android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val paymentId = BalanceNotificationSounds.ensureChannel(
            app, BalanceNotificationSounds.PAYMENT)
        val modes = listOf(AndroidSignalProbe.Mode.SHORT,
            AndroidSignalProbe.Mode.LONG, AndroidSignalProbe.Mode.DOUBLE)
        val ids = mutableSetOf<String>()
        for (mode in modes) {
            val result = AndroidSignalProbe.send(app, mode)
            assertNotNull("Missing probe for " + mode.label + ": " + result.report, result.id)
            val id = result.id!!
            assertTrue("Channels must use distinct IDs", ids.add(id))
            assertTrue(StableNotificationChannels.isProbe(id))
            val expected = AndroidSignalProbe.requestedPattern(mode)
            assertNotNull("No expected custom vibration for " + mode.label, expected)
            val channel = manager().getNotificationChannel(id)
            assertNotNull(channel)
            assertTrue("No vibration: " + mode.label, channel!!.shouldVibrate())
            assertArrayEquals("Wrong Android vibration: " + mode.label,
                expected, channel.vibrationPattern)
            if (Build.VERSION.SDK_INT >= 35) {
                assertNotNull("No API 35 vibration effect: " + mode.label,
                    channel.vibrationEffect)
            }
            assertEquals("Channel ID changed when test was repeated", id,
                AndroidSignalProbe.send(app, mode).id)
        }
        assertEquals("New tests changed active payment settings", paymentId,
            BalanceNotificationSounds.channelId(app, BalanceNotificationSounds.PAYMENT))
    }

    @Test fun crystalProbeUsesPublicMediaStoreSoundWithoutReplacingUserChannels() {
        if (Build.VERSION.SDK_INT < 29) return
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(app.packageName,
                    android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val oldPaymentId = BalanceNotificationSounds.ensureChannel(
            app, BalanceNotificationSounds.PAYMENT)
        val tone = AndroidSignalProbe.send(app, AndroidSignalProbe.Mode.TONE)
        assertNotNull("Crystal test channel missing: " + tone.report, tone.id)
        val channel = manager().getNotificationChannel(tone.id!!)
        assertNotNull(channel)
        assertEquals("Custom sound must use Android MediaStore",
            "media", channel!!.sound?.authority)
        assertNull("Stored WAV should be readable",
            BalanceNotificationSounds.audioProbe(app, channel.sound!!))
        assertTrue(channel.shouldVibrate())
        assertArrayEquals(NotificationVibrationMode.pattern(NotificationVibrationMode.LONG),
            channel.vibrationPattern)
        assertEquals(tone.id, AndroidSignalProbe.send(app, AndroidSignalProbe.Mode.TONE).id)
        assertEquals(oldPaymentId, BalanceNotificationSounds.channelId(
            app, BalanceNotificationSounds.PAYMENT))
    }

    @Test fun systemNotificationToneIgnoresBundledWavGain() {
        if (Build.VERSION.SDK_INT < 26) return
        val category = BalanceNotificationSounds.EVENT
        val applied = BalanceNotificationSounds.applySettings(
            app, category, "system", NotificationVibrationMode.DEFAULT, 0)
        assertNull("System ringtone must work at app WAV gain zero: " + applied, applied)
        val channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(app, category))
        assertNotNull(channel)
        assertEquals(android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_NOTIFICATION), channel!!.sound)
        assertFalse(BalanceNotificationSounds.needsRepair(app, category))
    }

    @Test fun mediaStoreSoundIsReadableBySeparateShellProcess() {
        if (Build.VERSION.SDK_INT < 29) return
        val context = app
        val uri = BalanceSystemToneStore.uri(context, "crystal", 100)
        assertNotNull(uri)
        if (Build.VERSION.SDK_INT == 29) {
            // Android 10's shell uid is not given arbitrary access to the
            // user's public audio collection. Exercise the platform ringtone
            // resolver instead of assuming shell storage permissions.
            val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
            assertNotNull("Android 10 ringtone resolver cannot open system tone", ringtone)
            ringtone?.stop()
            return
        }
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("content read --uri " + uri.toString())
        result.use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).use { stream ->
                val header = ByteArray(12)
                var count = 0
                while (count < header.size) {
                    val read = stream.read(header, count, header.size - count)
                    if (read < 1) break
                    count += read
                }
                assertEquals("External process cannot open MediaStore sound", 12, count)
                assertEquals("RIFF", String(header, 0, 4))
                assertEquals("WAVE", String(header, 8, 4))
            }
        }
    }

    @Test fun customToneIsReadableBySeparateShellProcess() {
        if (Build.VERSION.SDK_INT < 26) return
        val context = app
        val uri = Uri.parse("content://" + context.packageName +
            ".sounds/crystal/v3/100")
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("content read --uri " + uri.toString())
        result.use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).use { stream ->
                val bytes = ByteArray(12)
                var count = 0
                while (count < 12) {
                    val n = stream.read(bytes, count, 12 - count)
                    if (n <= 0) break
                    count += n
                }
                assertEquals("Android shell cannot open BalanceHub provider",12,count)
                assertEquals("RIFF",String(bytes,0,4))
                assertEquals("WAVE",String(bytes,8,4))
            }
        }
    }
    @Test fun fiveVibrationModesAndVolumeAreAppliedToAndroidChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val category = BalanceNotificationSounds.PAYMENT
        val configure = BalanceNotificationSounds.applySettings(app, category,
            "crystal", NotificationVibrationMode.DOUBLE, 55)
        assertNull("Custom vibration and volume were rejected: " + configure, configure)
        assertEquals(55, BalanceNotificationSounds.volume(app, category))
        assertEquals(NotificationVibrationMode.DOUBLE,
            BalanceNotificationSounds.vibrationMode(app, category))
        var channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(app, category))
        assertNotNull(channel)
        assertTrue(channel!!.shouldVibrate())
        assertArrayEquals(NotificationVibrationMode.pattern(NotificationVibrationMode.DOUBLE),
            channel.vibrationPattern)
        assertNotNull(channel.sound)

        val silentHaptics = BalanceNotificationSounds.applyVibrationModeChoice(
            app, category, NotificationVibrationMode.OFF)
        assertNull("Cannot disable vibration: " + silentHaptics, silentHaptics)
        channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(app, category))
        assertNotNull(channel)
        assertFalse(channel!!.shouldVibrate())
        assertNotNull("Changing vibration must preserve sound", channel.sound)

        val defaultHaptics = BalanceNotificationSounds.applyVibrationModeChoice(
            app, category, NotificationVibrationMode.DEFAULT)
        assertNull("Cannot restore default vibration: " + defaultHaptics, defaultHaptics)
        BalanceNotificationSounds.ensureDefaultChannels(app)
        channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(app, category))
        assertNotNull(channel)
        assertTrue(channel!!.shouldVibrate())
    }


    @Test fun nativeSliderGainReplacesAndroidChannelWithoutTrialDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val category = BalanceNotificationSounds.UPDATE
        if (PaymentAutomaticSound.enabled(app, category)) return
        val eventGain = BalanceNotificationSounds.volume(
            app, BalanceNotificationSounds.EVENT)
        val initial = BalanceNotificationSounds.applySettings(app, category,
            "spark", NotificationVibrationMode.DEFAULT, 100)
        assertNull("Cannot prepare native WAV channel: $initial", initial)
        val oldId = BalanceNotificationSounds.channelId(app, category)

        val applied = BalanceNotificationSounds.applyVolumeChoice(app, category, 40)
        assertNull("Slider gain was not applied: $applied", applied)
        val newId = BalanceNotificationSounds.channelId(app, category)
        assertNotEquals("Native Android WAV gain needs a new channel", oldId, newId)
        assertEquals(40, BalanceNotificationSounds.volume(app, category))
        assertNull("No melody trial must be staged",
            CategoryToneChange.pending(app, category))
        val newChannel = manager().getNotificationChannel(newId)
        assertNotNull(newChannel)
        assertNotNull("40% channel was not audible", newChannel!!.sound)
        assertNull("40% WAV cannot be read",
            BalanceNotificationSounds.audioProbe(app, newChannel.sound!!))
        val expected = BalanceSystemToneStore.cachedUri(app, "spark", 40)
        assertNotNull(expected)
        assertTrue("Android channel kept the previous WAV volume",
            NotificationSignalPolicy.sameSoundUri(
                expected.toString(), newChannel.sound.toString()))
        assertEquals("Unrelated event gain changed", eventGain,
            BalanceNotificationSounds.volume(app, BalanceNotificationSounds.EVENT))

        BalanceNotificationSounds.ensureDefaultChannels(app)
        assertEquals("Saved volume changed after channel initialization", 40,
            BalanceNotificationSounds.volume(app, category))
        assertEquals(newId, BalanceNotificationSounds.channelId(app, category))
        assertNull("Zero gain should be applied without confirmation",
            BalanceNotificationSounds.applyVolumeChoice(app, category, 0))
        assertEquals(0, BalanceNotificationSounds.volume(app, category))
        assertNull(manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(app, category))!!.sound)
    }
}
