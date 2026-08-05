package com.quickdaily

/** A compact, path-free snapshot of the newly added feature configuration. */
internal data class LogFeatureStats(
    val readWidgetCount: Int = 0,
    val readWidgetTodayCount: Int = 0,
    val readWidgetCustomCount: Int = 0,
    val taskWidgetCount: Int = 0,
    val taskWidgetTodayCount: Int = 0,
    val taskWidgetWeekCount: Int = 0,
    val taskWidgetMonthCount: Int = 0,
    val taskWidgetCustomCount: Int = 0,
    val customPageCount: Int = 0,
    val floatingSaveOnClose: Boolean = FloatingNoteEntryPolicy.DEFAULT_SAVE_ON_CLOSE,
    val floatingKeepDraftOnClose: Boolean = FloatingNoteEntryPolicy.DEFAULT_KEEP_DRAFT_ON_CLOSE,
    val floatingOpacityPercent: Int = FloatingNoteAppearance.DEFAULT_OPACITY_PERCENT,
    val toolbarVisibleCount: Int = 0,
    val toolbarOrder: List<String> = emptyList(),
)

internal object LogFeatureStatsPolicy {
    fun encode(stats: LogFeatureStats): String = listOf(
        "readWidgetCount=${stats.readWidgetCount}",
        "readWidgetTodayCount=${stats.readWidgetTodayCount}",
        "readWidgetCustomCount=${stats.readWidgetCustomCount}",
        "taskWidgetCount=${stats.taskWidgetCount}",
        "taskWidgetTodayCount=${stats.taskWidgetTodayCount}",
        "taskWidgetWeekCount=${stats.taskWidgetWeekCount}",
        "taskWidgetMonthCount=${stats.taskWidgetMonthCount}",
        "taskWidgetCustomCount=${stats.taskWidgetCustomCount}",
        "customPageCount=${stats.customPageCount}",
        "floatingSaveOnClose=${stats.floatingSaveOnClose}",
        "floatingKeepDraftOnClose=${stats.floatingKeepDraftOnClose}",
        "floatingOpacityPercent=${stats.floatingOpacityPercent}",
        "toolbarVisibleCount=${stats.toolbarVisibleCount}",
        "toolbarOrder=${stats.toolbarOrder.joinToString(",")}",
    ).joinToString(" ")
}
