package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStampPolicyTest {
    @Test
    fun noneDisablesTimestampInsertion() {
        assertNull(EditorStampPolicy.timestampInsertion("none"))
    }

    @Test
    fun configuredListTimestampKeepsExistingEntryShape() {
        val value = EditorStampPolicy.timestampInsertion("list_time")
        checkNotNull(value)
        assertEquals(true, value.startsWith("- "))
        assertEquals(true, value.endsWith(" "))
    }

    @Test
    fun toolbarTimestampUsesFixed24HourMinutes() {
        val value = EditorStampPolicy.toolbarTimestampInsertion()
        assertTrue(Regex("\\d{2}:\\d{2} ").matches(value))
    }

    @Test
    fun dateStampUsesIsoDateShape() {
        assertEquals(10, EditorStampPolicy.dateInsertion().length)
        assertEquals(true, EditorStampPolicy.dateInsertion()[4] == '-')
        assertEquals(true, EditorStampPolicy.dateInsertion()[7] == '-')
    }
}
