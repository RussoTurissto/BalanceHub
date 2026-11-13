package com.github.russoturissto.buttonapp.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android-device check for the exact failure reported by users:
 * the internal WAV content URI must be readable by ContentResolver.
 * CI compiles this test; executing it requires an emulator/device.
 */
@RunWith(AndroidJUnit4::class)
class NotificationToneIntegrationTest {
    @Test fun allFifteenBundledSoundsAreReadableThroughContentProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(15, BalanceNotificationSounds.tones.size)
        BalanceNotificationSounds.tones.forEach { tone ->
            val uri = Uri.parse("content://" + context.packageName + ".sounds/" +
                tone.key + "/v3/100")
            val stream = context.contentResolver.openInputStream(uri)
            assertNotNull("Provider did not open " + tone.key, stream)
            stream!!.use {
                val header = ByteArray(12)
                var count = 0
                while (count < header.size) {
                    val read = it.read(header, count, header.size - count)
                    if (read <= 0) break
                    count += read
                }
                assertEquals("Short WAV " + tone.key, 12, count)
                assertTrue("Not RIFF: " + tone.key,
                    header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()))
                assertTrue("Not WAVE: " + tone.key,
                    header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray()))
            }
        }
    }

    @Test fun allBundledSoundsHaveNamedResourceFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        BalanceNotificationSounds.tones.forEach { tone ->
            val entry = context.resources.getResourceEntryName(tone.resource)
            val uri = Uri.parse("android.resource://" + context.packageName +
                "/raw/" + entry)
            context.contentResolver.openInputStream(uri).use {
                assertNotNull("Resource URI unavailable for " + tone.key, it)
            }
        }
    }
}
