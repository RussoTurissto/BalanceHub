package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test fun fourDigitPinOnly() {
        assertTrue(AppLockPolicy.validPin("0000"))
        assertTrue(AppLockPolicy.validPin("9876"))
        assertFalse(AppLockPolicy.validPin("123"))
        assertFalse(AppLockPolicy.validPin("12345"))
        assertFalse(AppLockPolicy.validPin("abcd"))
        assertFalse(AppLockPolicy.validPin("12a4"))
    }

    @Test fun repeatedWrongPinIsRateLimited() {
        assertEquals(0L, AppLockPolicy.lockoutMillis(4))
        assertEquals(30_000L, AppLockPolicy.lockoutMillis(5))
        assertEquals(30_000L, AppLockPolicy.lockoutMillis(9))
        assertEquals(120_000L, AppLockPolicy.lockoutMillis(10))
        assertEquals(300_000L, AppLockPolicy.lockoutMillis(15))
    }

    @Test fun relockOnlyWhenSelectedConditionIsMet() {
        assertTrue(AppLockPolicy.shouldRelock(AppLockPolicy.IMMEDIATE, 0, false))
        assertFalse(AppLockPolicy.shouldRelock(AppLockPolicy.ONE_MINUTE, 59_999, false))
        assertTrue(AppLockPolicy.shouldRelock(AppLockPolicy.ONE_MINUTE, 60_000, false))
        assertFalse(AppLockPolicy.shouldRelock(AppLockPolicy.FIVE_MINUTES, 299_999, false))
        assertTrue(AppLockPolicy.shouldRelock(AppLockPolicy.FIVE_MINUTES, 300_000, false))
        assertFalse(AppLockPolicy.shouldRelock(AppLockPolicy.DEVICE_LOCK, 1_000_000, false))
        assertTrue(AppLockPolicy.shouldRelock(AppLockPolicy.DEVICE_LOCK, 0, true))
    }
}
