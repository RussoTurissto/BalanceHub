package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseNotesTest {
    @Test fun eachRcShowsOnlyWhatChangedInThatBuild() {
        assertEquals(listOf(
            "Обновлена информация в окне «Что нового».",
            "Добавлена история изменений по версиям."
        ), AppReleaseNotes.forVersion("1.2.1-rc4").map { it.title })
        assertFalse(AppReleaseNotes.forVersion("1.2.1-rc4").any {
            it.title.contains("балансов") || it.title.contains("обучения")
        })
        assertEquals(3, AppReleaseNotes.forVersion("1.2.1-rc3").size)
        assertEquals(4, AppReleaseNotes.forVersion("1.2.1-rc2").size)
        assertEquals(1, AppReleaseNotes.forVersion("1.2.1-rc1").size)
    }

    @Test fun stableReleaseCombinesAllUserFacingRcChangesWithoutDuplicates() {
        val stable = AppReleaseNotes.forVersion("1.2.1").map { it.title }
        assertTrue(stable.containsAll(AppReleaseNotes.forVersion("1.2.1-rc2")
            .map { it.title }))
        assertTrue(stable.containsAll(AppReleaseNotes.forVersion("1.2.1-rc3")
            .map { it.title }))
        assertTrue(stable.containsAll(AppReleaseNotes.forVersion("1.2.1-rc4")
            .map { it.title }))
        assertEquals(stable.size, stable.distinct().size)
        assertFalse(stable.any { it.contains("Тестовая сборка") })
        // Previous stable-release improvements belong only in history.
        val oldStable = AppReleaseNotes.forVersion("1.2.0").map { it.title }
        assertTrue(stable.none { it in oldStable })
    }

    @Test fun stable123ConsolidatesAllSecurityAndCalendarChanges() {
        val stable = AppReleaseNotes.forVersion("1.2.3").map { it.title }
        val first = AppReleaseNotes.forVersion("1.2.3-rc1").map { it.title }
        val second = AppReleaseNotes.forVersion("1.2.3-rc2").map { it.title }
        assertEquals(first + second, stable)
        assertEquals(stable, AppReleaseNotes.forStableRelease("1.2.3")
            .map { it.title })
        assertEquals(stable.size, stable.distinct().size)
        assertEquals("1.2.3", AppReleaseNotes.offlineStableHistory("1.2.3").first())
        assertEquals("1.2.2", AppReleaseNotes.offlineStableHistory("1.2.3-rc2").first())
    }

    @Test fun stable122HasExactlyThreeApprovedNotesWithoutUpdatesSection() {
        val expected = listOf(
            "Платежи: улучшено выравнивание элементов.",
            "События: унифицировано оформление полей и повторений.",
            "Интерфейс: выровнены отступы и расстояния между элементами."
        )
        assertEquals(expected, AppReleaseNotes.forVersion("1.2.2").map { it.title })
        assertEquals(expected, AppReleaseNotes.forStableRelease("1.2.2")
            .map { it.title })
        assertTrue(expected.none { it.contains("Обновления") || it.contains("APK") })
        assertEquals("1.2.2", AppReleaseNotes.offlineStableHistory("1.2.2").first())
    }

    @Test fun securityCandidateNotesDescribeOnlyApprovedProtection() {
        val notes = AppReleaseNotes.forVersion("1.2.3-rc1").map { it.title }
        assertEquals(4, notes.size)
        assertTrue(notes.any { "четырёхзначным кодом" in it })
        assertTrue(notes.any { "отпечатку пальца" in it })
        assertTrue(notes.none { "распознавание лица" in it })
    }

    @Test fun securityRc2NotesDescribeCalendarAndBiometricUpdates() {
        val notes = AppReleaseNotes.forVersion("1.2.3-rc2").map { it.title }
        assertEquals(3, notes.size)
        assertTrue(notes.any { "автоматически" in it })
        assertTrue(notes.any { "снимков экрана" in it })
        assertTrue(notes.any { "календари" in it })
    }

    @Test fun bankSettingsCandidateNotesDescribeOnlyTheApprovedLayoutChange() {
        val entries = AppReleaseNotes.forVersion("1.2.4-rc1").map { it.title }
        assertEquals(2, entries.size)
        assertTrue(entries.any { "группированы по банкам" in it })
        assertTrue(entries.any { "Push" in it })
        assertTrue(entries.none { "баланс" in it || "API" in it })
        assertEquals("1.2.3", AppReleaseNotes.offlineStableHistory("1.2.4-rc1").first())
    }

    @Test fun navigationRc2NotesDescribeOnlyApprovedBottomBarAdjustment() {
        val entries = AppReleaseNotes.forVersion("1.2.4-rc2").map { it.title }
        assertEquals(1, entries.size)
        assertTrue(entries.single().contains("Нижняя панель"))
        assertTrue(entries.single().contains("системными жестами"))
        assertEquals("1.2.3",
            AppReleaseNotes.offlineStableHistory("1.2.4-rc2").first())
    }

    @Test fun navigationRc3NotesDescribeSystemInsets() {
        val notes = AppReleaseNotes.forVersion("1.2.4-rc3").map { it.title }
        assertEquals(1, notes.size)
        assertTrue(notes.single().contains("жесты и кнопки Android"))
        assertEquals("1.2.3",
            AppReleaseNotes.offlineStableHistory("1.2.4-rc3").first())
    }

    @Test fun navigationRc4NotesDescribeTheAdjustedBottomBar() {
        val notes = AppReleaseNotes.forVersion("1.2.4-rc4").map { it.title }
        assertEquals(1, notes.size)
        assertTrue(notes.single().contains("системной навигацией"))
        assertEquals("1.2.3",
            AppReleaseNotes.offlineStableHistory("1.2.4-rc4").first())
    }

    @Test fun unknownVersionsNeverSilentlyReuseOldReleaseNotes() {
        assertTrue(AppReleaseNotes.forVersion("1.2.1-rc5").isEmpty())
        assertFalse(AppReleaseNotes.forVersion("1.2.3").isEmpty())
        assertTrue(AppReleaseNotes.forVersion(null).isEmpty())
        assertFalse(AppReleaseNotes.forVersion("1.2.0").isEmpty())
    }

    @Test fun offlineHistoryNeverShowsTestCandidatesOrUnpublishedStable121() {
        val current = AppReleaseNotes.offlineStableHistory("1.2.1-rc4")
        assertEquals("1.2.0", current.first())
        assertTrue(current.none { "-rc" in it || it == "1.2.1" })
        assertEquals("1.2.1",
            AppReleaseNotes.offlineStableHistory("1.2.1").first())
    }

    @Test fun userTextContainsNoBuildOrLayoutImplementationDetails() {
        val versions = listOf("1.2.0", "1.2.1", "1.2.2", "1.2.3",
            "1.2.3-rc1", "1.2.3-rc2", "1.2.4-rc1", "1.2.4-rc2", "1.2.4-rc3", "1.2.4-rc4", "1.2.1-rc1",
            "1.2.1-rc2", "1.2.1-rc3", "1.2.1-rc4")
        for (version in versions) {
            for (note in AppReleaseNotes.forVersion(version)) {
                assertFalse(note.title.contains(" sp"))
                assertFalse(note.title.contains(" dp"))
                assertFalse(note.title.contains("versionCode"))
                assertTrue(note.title.length <= 76)
            }
        }
    }
}
