package com.github.russoturissto.buttonapp.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.OpenableColumns
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * File-backed, seekable WAV provider for Android notification playback.
 *
 * Android's sound service may call openFile() instead of openAssetFile().
 * Previously only openAssetFile() was implemented, leaving those requests
 * with ContentProvider's default FileNotFoundException.
 *
 * Keep existing /v3/<volume> URIs and their saved notification channels valid.
 * Permanent app files (not cacheDir) survive ordinary cache cleanup and updates.
 */
class BalanceSoundProvider : ContentProvider() {
    private val writeLock = Any()
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String = "audio/wav"

    private fun soundFile(uri: Uri, mode: String): File {
        if (mode != "r") throw FileNotFoundException("Notification tones are read only")
        val app = context ?: throw FileNotFoundException("No application context")
        if (uri.authority != app.packageName + ".sounds") {
            throw FileNotFoundException("Unrecognized notification tone authority")
        }
        val selection = NotificationSoundPath.parse(uri.pathSegments)
            ?: throw FileNotFoundException("Invalid notification tone path")
        val sound = BalanceNotificationSounds.tones.firstOrNull {
            it.key == selection.first
        } ?: throw FileNotFoundException("Unknown notification tone")
        val gain = selection.second
        return synchronized(writeLock) {
            val folder = File(app.filesDir, "notification_tones_v3")
            if (!folder.isDirectory && !folder.mkdirs()) {
                throw FileNotFoundException("Cannot prepare the notification audio directory")
            }
            val file = File(folder, sound.key + "_" + gain + ".wav")
            if (!file.isFile || file.length() < 44L) {
                val source = app.resources.openRawResourceFd(sound.resource)?.use { descriptor ->
                    descriptor.createInputStream().use { it.readBytes() }
                } ?: throw FileNotFoundException("Bundled notification sound is missing")
                if (source.size < 44 ||
                    !source.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) ||
                    !source.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) {
                    throw FileNotFoundException("Bundled notification WAV is invalid")
                }
                val adjusted = WavSoundGain.attenuate(source, gain)
                val temp = File(folder, sound.key + "_" + gain + ".tmp")
                temp.writeBytes(adjusted)
                if (!temp.renameTo(file)) {
                    temp.delete()
                    throw FileNotFoundException("Cannot make notification audio available")
                }
            }
            file
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(soundFile(uri, mode), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        val descriptor = openFile(uri, mode)
        return AssetFileDescriptor(descriptor, 0L, descriptor.statSize)
    }

    override fun query(uri: Uri, projection: Array<out String>?,
        selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?): Cursor? {
        val file = try { soundFile(uri, "r") } catch (_: FileNotFoundException) {
            return null
        }
        val columns = projection?.toList() ?: listOf(
            OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val values = arrayOfNulls<Any>(columns.size)
        for ((index, column) in columns.withIndex()) {
            values[index] = when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }
        return MatrixCursor(columns.toTypedArray()).apply { addRow(values) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?,
        selectionArgs: Array<out String>?): Int = 0
}
