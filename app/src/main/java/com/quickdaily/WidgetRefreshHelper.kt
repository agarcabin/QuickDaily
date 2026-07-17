package com.quickdaily

import android.content.Context

object WidgetRefreshHelper {
    fun refreshAll(context: Context) {
        try { QuickDailyReadWidget.refreshAllWidgets(context) } catch (_: Exception) { }
        try { TaskWidget.refreshAllWidgets(context) } catch (_: Exception) { }
    }
}
