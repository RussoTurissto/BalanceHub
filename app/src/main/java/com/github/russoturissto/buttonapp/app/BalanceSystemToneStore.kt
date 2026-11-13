package com.github.russoturissto.buttonapp.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

/**
 * Provides system-readable, persistent URIs for notification WAV sounds.
 *
 * A NotificationChannel is owned by Android, not by our process. App-private
 * content URIs may play inside the app and still fail when Android tries to
 * resolve them. On Android 10+ the platform's public MediaStore is preferred.
 *
 * The app owns these files; no READ_MEDIA_AUDIO or broad storage permission
 * is necessary to insert and read the files it creates.
 */
internal object BalanceSystemToneStore {
    private const val PREFS = "balancehub_system_tones_v1"

    fun cachedUri(context: Context, key: String, gain: Int): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("tone_" + key + "_" + gain.coerceIn(0, 100), null)
            ?.let(Uri::parse)

    private fun readable(context: Context, uri: Uri): Boolean = try {
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) false else try {
            val header = ByteArray(12)
            var offset = 0
            while (offset < 12) {
                val size = input.read(header, offset, 12 - offset)
                if (size < 1) break
                offset += size
            }
            offset == 12 &&
                header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())
        } finally {
            input.close()
        }
    } catch (error: Exception) {
        Log.w("BalanceHubToneStore", "MediaStore tone unavailable", error)
        false
    }

    /**
     * Copies a bundled WAV to the Android Notifications media directory exactly
     * once per melody+gain. The URI remains stable across normal APK upgrades.
     */
    @Synchronized
    fun uri(context: Context, key: String, gain: Int): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val tone = BalanceNotificationSounds.tones.firstOrNull { it.key == key }
            ?: return null
        val level = gain.coerceIn(0, 100)
        cachedUri(context, key, level)?.let { saved ->
            if (readable(context, saved)) return saved
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var inserted: Uri? = null
        try {
            val source = context.resources.openRawResource(tone.resource)
                .use { it.readBytes() }
            if (source.size < 44 ||
                !source.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) ||
                !source.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) {
                Log.w("BalanceHubToneStore", "Bundled WAV invalid: " + key)
                return null
            }
            val data = WavSoundGain.attenuate(source, level)
            val filename = "BalanceHub_" + key + "_" + level + ".wav"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
                put(MediaStore.Audio.Media.TITLE, "BalanceHub — " + tone.title)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                // IS_NOTIFICATION is derived by MediaProvider from Notifications/
                // and is read-only on modern Android. Never write this column.
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_NOTIFICATIONS + "/BalanceHub/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            inserted = resolver.insert(collection, values) ?: return null
            val target = inserted
            val output = resolver.openOutputStream(target, "w")
                ?: throw java.io.IOException("MediaStore output not available")
            try {
                output.write(data)
                output.flush()
            } finally {
                output.close()
            }
            val published = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (resolver.update(target, published, null, null) != 1) {
                throw java.io.IOException("Cannot publish system notification tone")
            }
            if (!readable(context, target)) {
                throw java.io.IOException("Published WAV cannot be read back")
            }
            val ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("tone_" + key + "_" + level,
                    target.toString()).commit()
            if (!ok) throw java.io.IOException("Cannot persist MediaStore URI")
            return target
        } catch (error: Exception) {
            Log.e("BalanceHubToneStore", "MediaStore audio insertion failed", error)
            inserted?.let {
                try { resolver.delete(it, null, null) } catch (_: Exception) { }
            }
            return null
        }
    }
}
