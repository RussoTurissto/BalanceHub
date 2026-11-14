package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationToneIdentityTest {
    private val primary = "content://media/external_primary/audio/media/321"
    private val picker = "content://media/external/audio/media/321"

    @Test fun sameRowAcrossSystemMediaStoreVolumesIsEquivalent() {
        assertTrue(NotificationToneIdentity.same(primary, picker))
        assertTrue(NotificationToneIdentity.same(picker, primary))
        assertTrue(NotificationToneIdentity.same(primary, primary))
    }

    @Test fun sameMediaRowWithPickerQueryStillMatches() {
        assertTrue(NotificationToneIdentity.same(primary, picker + "?foo=bar"))
    }

    @Test fun differentAudioIdsAreNeverTreatedAsSameFile() {
        assertFalse(NotificationToneIdentity.same(primary,
            "content://media/external/audio/media/322"))
        assertFalse(NotificationToneIdentity.same(primary,
            "content://media/internal/audio/media/321"))
    }

    @Test fun unrelatedOrInvalidUrisDoNotMatch() {
        assertFalse(NotificationToneIdentity.same(primary, null))
        assertFalse(NotificationToneIdentity.same(primary,
            "content://another.app/sounds/crystal"))
        assertFalse(NotificationToneIdentity.same(null, picker))
        assertTrue(NotificationToneIdentity.same(null, null))
    }
}
