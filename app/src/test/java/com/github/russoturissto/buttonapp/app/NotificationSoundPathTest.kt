package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationSoundPathTest {
    @Test fun persistedCustomChannelUriIsAccepted() {
        assertEquals("crystal" to 75,
            NotificationSoundPath.parse(listOf("crystal", "v3", "75")))
        assertEquals("balancehub" to 100,
            NotificationSoundPath.parse(listOf("balancehub", "v3", "100")))
    }
    @Test fun legacySingleSegmentUriStillWorks() {
        assertEquals("spark" to 100,
            NotificationSoundPath.parse(listOf("spark")))
    }
    @Test fun invalidOrUnsafeUriIsRejected() {
        for (path in listOf(
            listOf("crystal", "v3", "-1"),
            listOf("crystal", "v3", "101"),
            listOf("crystal", "v4", "50"),
            listOf("..", "v3", "50"),
            listOf("crystal", "v3", "abc"),
            emptyList(),
            listOf("crystal", "v3", "50", "extra")
        )) assertNull(NotificationSoundPath.parse(path))
    }
}
