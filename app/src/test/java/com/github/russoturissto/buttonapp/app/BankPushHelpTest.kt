package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPushHelpTest {
    @Test fun guideUsesTheInstalledPackageIdRatherThanHardcodedSample() {
        val packageName = "com.example.balancehub"
        val commands = BankPushHelp.steps(packageName).mapNotNull { it.command }
        assertTrue(commands.any { it.contains(
            "appops get $packageName RECEIVE_SENSITIVE_NOTIFICATIONS") })
        assertTrue(commands.any { it.contains(
            "appops set $packageName RECEIVE_SENSITIVE_NOTIFICATIONS allow") })
    }

    @Test fun guideIncludesPairingReconnectionAndSecurityRestoration() {
        val steps = BankPushHelp.steps("com.test")
        assertTrue(steps.size >= 9)
        assertTrue(steps.any { it.command?.contains("adb pair") == true })
        assertTrue(steps.any { it.command?.contains("adb connect") == true })
        assertTrue(steps.any { it.heading.contains("SecurityException") ||
            it.detail.contains("SecurityException") })
        assertTrue(steps.any { it.detail.contains("Выключите беспроводную отладку") })
        assertEquals("RECEIVE_SENSITIVE_NOTIFICATIONS", BankPushHelp.APP_OP)
    }
}
