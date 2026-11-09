package com.github.russoturissto.buttonapp.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import java.io.FileNotFoundException

/**
 * Stable, read-only URIs for public notification tones.
 * Channel sound URIs survive upgrades even if Android raw resource IDs change.
 * No personal data or app files are exposed.
 */
class BalanceSoundProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "audio/wav"

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val key = uri.pathSegments.singleOrNull()
            ?: throw FileNotFoundException("Invalid sound")
        val sound = BalanceNotificationSounds.tones.firstOrNull { it.key == key }
            ?: throw FileNotFoundException("Unknown sound")
        return context?.resources?.openRawResourceFd(sound.resource)
            ?: throw FileNotFoundException("Sound missing")
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
