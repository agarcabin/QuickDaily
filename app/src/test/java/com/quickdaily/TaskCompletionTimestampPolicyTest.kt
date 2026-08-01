package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
