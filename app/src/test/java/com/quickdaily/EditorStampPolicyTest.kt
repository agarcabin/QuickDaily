package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.text.TextRange
import com.quickdaily.ui.EditorToolbarLayoutPolicy
import androidx.compose.ui.unit.dp

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
        assertEquals(11, EditorStampPolicy.dateInsertion().length)
        assertEquals(true, EditorStampPolicy.dateInsertion()[4] == '-')
        assertEquals(true, EditorStampPolicy.dateInsertion()[7] == '-')
        assertEquals(' ', EditorStampPolicy.dateInsertion().last())
    }

    @Test
    fun chineseDateTimeFormatsKeepTheRequestedShape() {
        val dateTime = EditorStampPolicy.timestampInsertion("date_time")
        val listDateTime = EditorStampPolicy.timestampInsertion("list_date_time")
        assertTrue(Regex("\\d{4}年\\d{1,2}月\\d{1,2}日 \\d{2}:\\d{2} ").matches(checkNotNull(dateTime)))
        assertTrue(Regex("- \\d{4}年\\d{1,2}月\\d{1,2}日 \\d{2}:\\d{2} ").matches(checkNotNull(listDateTime)))
    }

    @Test
    fun stampToggleRemovesOnlyTheExactPreviousInsertion() {
        val first = EditorStampToggleState().toggle(
            text = "hello",
            selection = TextRange(5),
            action = EditorStampAction.TIMESTAMP,
            insertion = "12:34 ",
        )
        assertEquals("hello12:34 ", first.first.text)

        val second = first.second.toggle(
            text = first.first.text,
            selection = first.first.selection,
            action = EditorStampAction.TIMESTAMP,
            insertion = "12:35 ",
        )
        assertEquals("hello", second.first.text)
        assertEquals(5, second.first.selection.start)
    }

    @Test
    fun stampToggleDoesNotDeleteAfterExternalEditing() {
        val first = EditorStampToggleState().toggle(
            text = "hello",
            selection = TextRange(5),
            action = EditorStampAction.DATE_STAMP,
            insertion = "2026-08-01",
        )
        val edited = first.second.clear()
            .toggle(
                text = first.first.text + "!",
                selection = TextRange(first.first.text.length + 1),
                action = EditorStampAction.DATE_STAMP,
                insertion = "2026-08-01",
            )
        assertTrue(edited.first.text.endsWith("2026-08-01"))
    }

    @Test
    fun toolbarFitsCompleteSlotsAndDistributesRemainingSpace() {
        assertEquals(7, EditorToolbarLayoutPolicy.fitCount(360.dp, 48.dp, 10))
        assertEquals(3, EditorToolbarLayoutPolicy.fitCount(360.dp, 48.dp, 3))
        assertEquals(60.dp, EditorToolbarLayoutPolicy.slotWidth(360.dp, 48.dp, 6))
        assertEquals(7, EditorToolbarLayoutPolicy.fitCount(285.dp, 40.dp, 7))
        assertEquals(7, EditorToolbarLayoutPolicy.fitCount(880.dp, 40.dp, 8, compact = true))
    }
}
