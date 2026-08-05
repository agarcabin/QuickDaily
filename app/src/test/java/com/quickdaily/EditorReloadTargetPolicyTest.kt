package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorReloadTargetPolicyTest {
    @Test
    fun explicitTargetSurvivesReloadFallback() {
        assertEquals("Projects/Today.md", EditorReloadTargetPolicy.targetToReload("  Projects/Today.md  "))
        assertEquals("", EditorReloadTargetPolicy.targetToReload("  "))
    }
}
