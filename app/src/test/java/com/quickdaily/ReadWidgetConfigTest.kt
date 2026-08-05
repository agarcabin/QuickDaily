package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadWidgetConfigTest {
    @Test
    fun defaultTargetIsToday() {
        assertEquals(ReadWidgetTarget.TODAY, ReadWidgetConfig().target)
        assertEquals("today", ReadWidgetTarget.fromKey("unknown").key)
    }

    @Test
    fun widgetStorageKeysAreIndependent() {
        val first = ReadWidgetConfigStore.storageKeys(101)
        val second = ReadWidgetConfigStore.storageKeys(102)
        assertNotEquals(first, second)
        assertTrue(first.first.contains("101"))
        assertTrue(second.second.contains("102"))
    }

    @Test
    fun customTargetKeepsItsPathAndDisplayName() {
        val config = ReadWidgetConfig(ReadWidgetTarget.CUSTOM, "Projects/Launch.md")
        assertEquals("Projects/Launch.md", config.customRelativePath)
        assertEquals("Launch", ReadWidgetConfigStore.displayName(config))
    }
}
