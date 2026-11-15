package com.github.russoturissto.buttonapp.app

/**
 * MediaStore may refer to the same row as external_primary (inserts) or
 * external (system ringtone picker). Match the numeric row ID only when
 * both URIs use these two supported views, never by filename alone.
 */
internal object NotificationToneIdentity {
    private val mediaRow = Regex(
        """^content://media/(external|external_primary)/audio/media/([0-9]+)$"""
    )

    fun same(expected: String?, actual: String?): Boolean {
        if (expected == actual) return true
        val first = expected?.substringBefore('?')?.let {
            mediaRow.matchEntire(it)?.groupValues?.get(2)?.toLongOrNull()
        }
        val second = actual?.substringBefore('?')?.let {
            mediaRow.matchEntire(it)?.groupValues?.get(2)?.toLongOrNull()
        }
        return first != null && first == second
    }
}
