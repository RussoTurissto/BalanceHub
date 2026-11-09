package com.github.russoturissto.buttonapp.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Only serves bundled WAV notification tones, optionally attenuated by app gain. */
class BalanceSoundProvider : ContentProvider() {
    private val writeLock = Any()
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String = "audio/wav"

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val segments = uri.pathSegments
        if (segments.size != 1 && (segments.size != 3 || segments[1] != "v3"))
            throw FileNotFoundException("Invalid sound path")
        val sound = BalanceNotificationSounds.tones.firstOrNull {
            it.key == segments.firstOrNull()
        } ?: throw FileNotFoundException("Unknown sound")
        val app = context ?: throw FileNotFoundException("No context")
        if (segments.size == 1) return app.resources.openRawResourceFd(sound.resource)
            ?: throw FileNotFoundException("Sound missing")
        val gain = segments[2].toIntOrNull()?.takeIf { it in 0..100 }
            ?: throw FileNotFoundException("Invalid volume")
        if (gain == 100) return app.resources.openRawResourceFd(sound.resource)
            ?: throw FileNotFoundException("Sound missing")
        return synchronized(writeLock) {
            val dir = File(app.cacheDir, "notification_wav_v3")
            if (!dir.isDirectory && !dir.mkdirs()) throw FileNotFoundException("Cache unavailable")
            val file = File(dir, sound.key + "_" + gain + ".wav")
            if (!file.isFile || file.length() == 0L) {
                val input = app.resources.openRawResourceFd(sound.resource)
                    ?: throw FileNotFoundException("Sound missing")
                val original = input.createInputStream().use { it.readBytes() }
                val adjusted = WavSoundGain.attenuate(original, gain)
                val temporary = File(dir, sound.key + "_" + gain + ".tmp")
                temporary.writeBytes(adjusted)
                if (!temporary.renameTo(file)) {
                    temporary.delete()
                    throw FileNotFoundException("Unable to cache sound")
                }
            }
            AssetFileDescriptor(ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_ONLY), 0L, file.length())
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?,
        selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?,
        selectionArgs: Array<out String>?): Int = 0
}
