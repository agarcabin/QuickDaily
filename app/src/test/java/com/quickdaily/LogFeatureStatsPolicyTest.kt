package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFeatureStatsPolicyTest {
    @Test
    fun encodeIncludesNewFeatureStatisticsWithStableKeys() {
        val encoded = LogFeatureStatsPolicy.encode(
            LogFeatureStats(
                readWidgetCount = 3,
                readWidgetTodayCount = 2,
                readWidgetCustomCount = 1,
                taskWidgetCount = 4,
                taskWidgetTodayCount = 1,
                taskWidgetWeekCount = 1,
                taskWidgetMonthCount = 1,
                taskWidgetCustomCount = 1,
                customPageCount = 5,
                floatingSaveOnClose = false,
                floatingKeepDraftOnClose = true,
                floatingOpacityPercent = 83,
                toolbarVisibleCount = 6,
                toolbarOrder = listOf("image", "task", "heading"),
            ),
        )

        assertEquals(
            "readWidgetCount=3 readWidgetTodayCount=2 readWidgetCustomCount=1 " +
                "taskWidgetCount=4 taskWidgetTodayCount=1 taskWidgetWeekCount=1 " +
                "taskWidgetMonthCount=1 taskWidgetCustomCount=1 customPageCount=5 " +
                "floatingSaveOnClose=false floatingKeepDraftOnClose=true floatingOpacityPercent=83 " +
                "toolbarVisibleCount=6 toolbarOrder=image,task,heading",
            encoded,
        )
    }

    @Test
    fun emptyStatisticsRemainExplicit() {
        val encoded = LogFeatureStatsPolicy.encode(LogFeatureStats())

        assertTrue(encoded.contains("readWidgetCount=0"))
        assertTrue(encoded.contains("taskWidgetCount=0"))
        assertTrue(encoded.contains("toolbarOrder="))
    }
}
