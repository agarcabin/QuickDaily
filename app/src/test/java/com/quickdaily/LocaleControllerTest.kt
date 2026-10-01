package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleControllerTest {
    @Test
    fun blankAndUnknownSelectionsFollowTheSystem() {
        assertEquals(LocaleController.SYSTEM, LocaleController.normalizeSelection(null))
        assertEquals(LocaleController.SYSTEM, LocaleController.normalizeSelection(""))
        assertEquals(LocaleController.SYSTEM, LocaleController.normalizeSelection("fr"))
    }

    @Test
    fun registeredLanguageTagsRemainCanonical() {
        assertEquals("zh", LocaleController.normalizeSelection("zh"))
        assertEquals("en", LocaleController.normalizeSelection("en"))
        assertEquals("en", LocaleController.normalizeSelection("en-US"))
        assertEquals("zh", LocaleController.normalizeSelection("zh-Hans-CN"))
    }

    @Test
    fun systemSentinelIsStable() {
        assertEquals(LocaleController.SYSTEM, LocaleController.normalizeSelection(LocaleController.SYSTEM))
    }
}
