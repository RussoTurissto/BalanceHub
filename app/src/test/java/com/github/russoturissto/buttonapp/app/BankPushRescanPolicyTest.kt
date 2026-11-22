package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPushRescanPolicyTest {
    private val now = 1_800_000_000_000L

    @Test fun newPushIsAcceptedAndDuplicateIsIgnored() {
        assertTrue(BankPushRescanPolicy.shouldProcess(null, 7, now, now, false))
        assertFalse(BankPushRescanPolicy.shouldProcess(7, 7, now, now, false))
        assertFalse(BankPushRescanPolicy.shouldProcess(7, 7, now, now, true))
    }

    @Test fun revisedNotificationWithSamePostTimeIsRescanned() {
        assertTrue(BankPushRescanPolicy.shouldProcess(7, 8, now, now, true))
    }

    @Test fun oldNotificationsAreNotMistakenForNewForegroundEvents() {
        assertFalse(BankPushRescanPolicy.shouldProcess(null, 7,
            now - 16L * 60_000L, now, true))
        assertFalse(BankPushRescanPolicy.shouldProcess(null, 7,
            now + 120_000L, now, true))
        assertTrue(BankPushRescanPolicy.shouldProcess(null, 7,
            now - 16L * 60_000L, now, false))
    }
}
