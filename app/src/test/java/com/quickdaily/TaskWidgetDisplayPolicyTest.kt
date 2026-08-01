package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskWidgetDisplayPolicyTest {
    @Test
    fun fullContentIsOffByDefaultAndUsesStablePreferenceKey() {
        assertFalse(TaskWidgetDisplayPolicy.DEFAULT_SHOW_FULL_CONTENT)
        assertEquals("task_show_full_content", TaskWidgetDisplayPolicy.SHOW_FULL_CONTENT_PREF_KEY)
    }
}
