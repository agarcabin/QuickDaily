package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskWidgetConfigTest {
    @Test
    fun widgetStorageKeysAreIsolatedByAppWidgetId() {
        assertNotEquals(
            TaskWidgetConfigStore.storageKeys(101),
            TaskWidgetConfigStore.storageKeys(202),
        )
    }

    @Test
    fun customPageTargetKeepsItsScopeAndDisplayName() {
        val config = TaskWidgetConfig(TaskWidgetScope.CUSTOM, "Projects/出差.md")

        assertEquals(TaskWidgetScope.CUSTOM, config.scope)
        assertEquals("出差", TaskWidgetConfigStore.displayName(config))
        assertTrue(TaskWidgetConfigStore.isMarkdownPath(config.customRelativePath))
    }

    @Test
    fun invalidCustomPageExtensionIsRejectedWithoutChangingScope() {
        val config = TaskWidgetConfig(TaskWidgetScope.CUSTOM, "Projects/出差.txt")

        assertEquals(TaskWidgetScope.CUSTOM, config.scope)
        assertFalse(TaskWidgetConfigStore.isMarkdownPath(config.customRelativePath))
    }

    @Test
    fun customPageHistoryMovesLatestPageToTheFrontAndRemovesIt() {
        val first = TaskWidgetPageHistory.remember(emptyList(), "/notes/出差.md")
        val second = TaskWidgetPageHistory.remember(first, "/notes/旅行.md")
        val latestAgain = TaskWidgetPageHistory.remember(second, "/notes/出差.md")

        assertEquals(listOf("/notes/出差.md", "/notes/旅行.md"), latestAgain)
        assertEquals(
            listOf("/notes/旅行.md"),
            TaskWidgetPageHistory.remove(latestAgain, "/notes/出差.md"),
        )
    }
}
