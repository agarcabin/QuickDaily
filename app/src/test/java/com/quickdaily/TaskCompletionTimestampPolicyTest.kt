package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TaskCompletionTimestampPolicyTest {
    @Test
    fun disabledLeavesTaskUnchanged() {
        val line = "- [X] Buy milk"

        assertEquals(
            line,
            TaskCompletionTimestampPolicy.appendIfEnabled(line, enabled = false, date = "2026-07-30")
        )
    }

    @Test
    fun enabledAppendsIsoDateToTaskEnd() {
        assertEquals(
            "- [X] Buy milk ✅️ 2026-07-30",
            TaskCompletionTimestampPolicy.appendIfEnabled(
                line = "- [X] Buy milk",
                enabled = true,
                date = "2026-07-30"
            )
        )
    }

    @Test
    fun existingTimestampIsNotDuplicated() {
        val line = "- [X] Buy milk ✅️2026-07-29"

        assertEquals(
            line,
            TaskCompletionTimestampPolicy.appendIfEnabled(line, enabled = true, date = "2026-07-30")
        )
    }

    @Test
    fun trailingWhitespaceIsRemovedBeforeAppending() {
        assertEquals(
            "- [X] Buy milk ✅️ 2026-07-30",
            TaskCompletionTimestampPolicy.appendIfEnabled(
                line = "- [X] Buy milk   ",
                enabled = true,
                date = "2026-07-30"
            )
        )
    }

    @Test
    fun removeStripsOnlyTheTrailingTimestampAndKeepsTaskText() {
        assertEquals(
            "- [ ] Buy milk",
            TaskCompletionTimestampPolicy.removeIfPresent("- [ ] Buy milk ✅️2026-07-30"),
        )
    }

    @Test
    fun removeAcceptsTheExistingSpacedAndVariationSelectorFormats() {
        assertEquals(
            "  - [x] Child",
            TaskCompletionTimestampPolicy.removeIfPresent("  - [x] Child ✅️ 2026-07-30"),
        )
        assertEquals(
            "- [x] Task",
            TaskCompletionTimestampPolicy.removeIfPresent("- [x] Task ✅ 2026-07-30"),
        )
    }

    @Test
    fun removeLeavesLinesWithoutTimestampUnchanged() {
        val line = "\t- [x] No timestamp"

        assertEquals(line, TaskCompletionTimestampPolicy.removeIfPresent(line))
    }

    @Test
    fun customMomentTokensRenderInAnyOrder() {
        val timestamp = LocalDateTime.of(2026, 7, 3, 4, 5, 6)

        assertEquals(
            "2026-03-07 04:05:06",
            TaskCompletionTimestampPolicy.formatTimestamp("YYYY-DD-MM HH:mm:ss", timestamp),
        )
        assertEquals(
            "26/7/3 4:5:6",
            TaskCompletionTimestampPolicy.formatTimestamp("YY/M/D H:m:s", timestamp),
        )
    }

    @Test
    fun bracketedLiteralTextIsPreservedWithoutTreatingItAsAToken() {
        assertEquals(
            "完成于 2026 年 07 月 03 日",
            TaskCompletionTimestampPolicy.formatTimestamp(
                "[完成于 ]YYYY[ 年 ]MM[ 月 ]DD[ 日]",
                LocalDateTime.of(2026, 7, 3, 4, 5, 6),
            ),
        )
    }

    @Test
    fun emptyAndUnknownFormatsNormalizeToTheDefault() {
        assertEquals(TaskCompletionTimestampPolicy.DEFAULT_FORMAT, TaskCompletionTimestampPolicy.normalizeFormat(""))
        assertEquals(TaskCompletionTimestampPolicy.DEFAULT_FORMAT, TaskCompletionTimestampPolicy.normalizeFormat("YYYY-QQ-DD"))
        assertTrue(TaskCompletionTimestampPolicy.isValidFormat("YYYY-DD-MM"))
        assertFalse(TaskCompletionTimestampPolicy.isValidFormat("YYYY-QQ-DD"))
    }

    @Test
    fun customTimestampIsNotDuplicatedAndCanBeRemoved() {
        val timestamp = LocalDateTime.of(2026, 7, 3, 4, 5, 6)
        val line = TaskCompletionTimestampPolicy.appendIfEnabled(
            line = "- [X] Buy milk",
            enabled = true,
            format = "YYYY-DD-MM HH:mm",
            timestamp = timestamp,
        )

        assertEquals("- [X] Buy milk 2026-03-07 04:05", line)
        assertEquals(
            line,
            TaskCompletionTimestampPolicy.appendIfEnabled(
                line = line,
                enabled = true,
                format = "YYYY-DD-MM HH:mm",
                timestamp = timestamp,
            ),
        )
        assertEquals(
            "- [X] Buy milk",
            TaskCompletionTimestampPolicy.removeIfPresent(line, "YYYY-DD-MM HH:mm"),
        )
    }
}
