package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelSignalStateTest {
    @Test fun selectedToneAndVibrationMutedByAndroidAreReported() {
        val issues = NotificationChannelSignalState.issues(
            wantsSound = true, channelHasSound = false,
            wantsVibration = true, channelHasVibration = false)
        assertEquals(2, issues.size)
        assertTrue(issues[0].contains("Звук"))
        assertTrue(issues[1].contains("Вибрация"))
    }

    @Test fun matchingSystemSignalsNeedNoRepair() {
        assertTrue(NotificationChannelSignalState.issues(
            wantsSound = true, channelHasSound = true,
            wantsVibration = true, channelHasVibration = true).isEmpty())
        assertTrue(NotificationChannelSignalState.issues(
            wantsSound = false, channelHasSound = false,
            wantsVibration = false, channelHasVibration = false).isEmpty())
    }

    @Test fun userEnabledSignalsAgainstSilentChoiceAreNotHidden() {
        val issues = NotificationChannelSignalState.issues(
            wantsSound = false, channelHasSound = true,
            wantsVibration = false, channelHasVibration = true)
        assertEquals(2, issues.size)
        assertTrue(issues[0].contains("вопреки"))
        assertTrue(issues[1].contains("вопреки"))
    }

    @Test fun differentAudibleAndroidToneIsReported() {
        val issues = NotificationChannelSignalState.issues(
            wantsSound = true, channelHasSound = true,
            wantsVibration = true, channelHasVibration = true,
            soundMatches = false)
        assertEquals(1, issues.size)
        assertTrue(issues.single().contains("отличается"))
    }

    @Test fun differentAndroidVibrationPatternIsReported() {
        val issues = NotificationChannelSignalState.issues(
            wantsSound = true, channelHasSound = true,
            wantsVibration = true, channelHasVibration = true,
            soundMatches = true, vibrationPatternMatches = false)
        assertEquals(1, issues.size)
        assertTrue(issues.single().contains("Рисунок"))
    }

    @Test fun lowImportanceIsDetectedEvenWithSoundUri() {
        val issues = NotificationChannelSignalState.issues(
            wantsSound = true, channelHasSound = true,
            wantsVibration = true, channelHasVibration = true,
            soundMatches = true, vibrationPatternMatches = true,
            channelCanAlert = false)
        assertEquals(1, issues.size)
        assertTrue(issues.single().contains("важность"))
    }

    @Test fun soundAndVibrationAreAuditedIndependently() {
        val issues = NotificationChannelSignalState.issues(
            wantsSound = true, channelHasSound = true,
            wantsVibration = true, channelHasVibration = false)
        assertEquals(listOf("Вибрация канала отключена в Android"), issues)
    }
}
