package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRegistryTest {
    @Test fun russianTitlesGetSpecificSemanticIcons() {
        assertEquals("cake", IconRegistry.select("День рождения мамы"))
        assertEquals("stethoscope", IconRegistry.select("Приём у врача"))
        assertEquals("wrench", IconRegistry.select("Техосмотр автомобиля"))
        assertEquals("wifi", IconRegistry.select("Платёж за интернет", "credit-card"))
    }

    @Test fun unknownTitleUsesAppropriateFallback() {
        assertEquals("calendar-days", IconRegistry.select("Zzzz unknown"))
        assertEquals("credit-card", IconRegistry.select("Zzzz unknown", "credit-card"))
    }

    @Test fun iconsHaveStableDistinctIdentifiersAndColorFamilies() {
        assertTrue(IconRegistry.all.size >= 65)
        assertEquals(IconRegistry.all.size, IconRegistry.all.map { it.id }.distinct().size)
        assertNotNull(IconRegistry.find("settings"))
        assertNotNull(IconRegistry.find("archive"))
    }
}
