package com.quickdaily

import android.content.Context

object WidgetRefreshHelper {
    fun refreshAll(context: Context) {
        try { WidgetRefreshCoordinator.refreshAll(context) } catch (_: Exception) { }
    }
}
