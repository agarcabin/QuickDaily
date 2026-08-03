package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Test

class DiaryConfigTest {

    @Test
    fun removedBlurSettingsAreNotPartOfThePersistedConfig() {
        val fieldNames = DiaryConfig::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("widget blur must be removed", "widgetBackgroundBlur" in fieldNames)
        assertFalse("floating blur must be removed", "floatingBackgroundBlur" in fieldNames)
    }

    @Test
    fun systemSidebarSupportIsOptInByDefault() {
        assertFalse(DiaryConfig().systemSidebarSupport)
        assertFalse(FloatingNoteEntryPolicy.DEFAULT_SYSTEM_SIDEBAR_SUPPORT)
    }
}
