package com.quickdaily

import com.quickdaily.util.VaultFileMetadata
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskWidgetFolderPolicyTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 8, 31)
    private val nowMillis = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun missingCreationTimeFallsBackToModificationTimeAndReportsIt() {
        val fallback = metadata(
            name = "fallback.md",
            creationTime = null,
            lastModified = at(12, 0),
        )
        val reported = mutableListOf<String>()

        val selected = TaskWidgetFolderPolicy.selectAndSort(
            files = listOf(fallback),
            window = TaskWidgetFolderTimeWindow.TODAY,
            nowMillis = nowMillis,
            onFallback = { reported += it.displayName },
        )

        assertEquals(listOf("fallback.md"), selected.map { it.displayName })
        assertFalse(selected.single().usedCreationTime)
        assertEquals(listOf("fallback.md"), reported)
    }

    @Test
    fun weekAndMonthUseRollingWindowsAndNewestFilesFirst() {
        val files = listOf(
            metadata("month-edge.md", creationTime = atDate(today.minusDays(29), 9, 0), lastModified = 0L),
            metadata("week-edge.md", creationTime = atDate(today.minusDays(6), 10, 0), lastModified = 0L),
            metadata("today.md", creationTime = at(11, 0), lastModified = 0L),
            metadata("old-week.md", creationTime = atDate(today.minusDays(7), 8, 0), lastModified = 0L),
            metadata("old-month.md", creationTime = atDate(today.minusDays(30), 8, 0), lastModified = 0L),
        )

        val week = TaskWidgetFolderPolicy.selectAndSort(
            files,
            TaskWidgetFolderTimeWindow.WEEK,
            nowMillis,
        )
        val month = TaskWidgetFolderPolicy.selectAndSort(
            files,
            TaskWidgetFolderTimeWindow.MONTH,
            nowMillis,
        )

        assertEquals(listOf("today.md", "week-edge.md"), week.map { it.displayName })
        assertEquals(
            listOf("today.md", "week-edge.md", "old-week.md", "month-edge.md"),
            month.map { it.displayName },
        )
        assertTrue(week.first().effectiveTime > week.last().effectiveTime)
    }

    @Test
    fun allWindowKeepsFilesWithUnknownEffectiveTime() {
        val unknown = metadata("unknown.md", creationTime = null, lastModified = 0L)

        val selected = TaskWidgetFolderPolicy.selectAndSort(
            files = listOf(unknown),
            window = TaskWidgetFolderTimeWindow.ALL,
            nowMillis = nowMillis,
        )

        assertEquals(listOf("unknown.md"), selected.map { it.displayName })
        assertEquals(0L, selected.single().effectiveTime)
    }

    @Test
    fun equalTimesUseStableRelativePathTieBreakers() {
        val sameTime = at(10, 0)
        val selected = TaskWidgetFolderPolicy.selectAndSort(
            files = listOf(
                metadata("zeta.md", creationTime = sameTime, lastModified = 0L),
                metadata("Alpha.md", creationTime = sameTime, lastModified = 0L),
                metadata("alpha.md", creationTime = sameTime, lastModified = 0L),
            ),
            window = TaskWidgetFolderTimeWindow.TODAY,
            nowMillis = nowMillis,
        )

        assertEquals(listOf("Alpha.md", "alpha.md", "zeta.md"), selected.map { it.displayName })
    }

    private fun metadata(
        name: String,
        creationTime: Long?,
        lastModified: Long,
    ) = VaultFileMetadata(
        sourcePath = "/vault/$name",
        relativePath = name,
        displayName = name,
        isDirectory = false,
        creationTime = creationTime,
        lastModified = lastModified,
    )

    private fun at(hour: Int, minute: Int): Long = atDate(today, hour, minute)

    private fun atDate(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}
