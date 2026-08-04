package com.quickdaily

import android.content.Context
import androidx.lifecycle.Lifecycle

internal object EditorLifecycleRefreshPolicy {
    fun shouldRefreshOn(event: Lifecycle.Event): Boolean = event == Lifecycle.Event.ON_STOP
}

object WidgetRefreshHelper {
    fun refreshAll(context: Context) {
        try { WidgetRefreshCoordinator.refreshAll(context) } catch (_: Exception) { }
    }
}
