package com.quickdaily

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/** Refreshes app-owned external surfaces after the preference is committed. */
object LocaleRefreshCoordinator {
    const val ACTION_LOCALE_REFRESH = "com.quickdaily.ACTION_LOCALE_REFRESH"
    const val EXTRA_LOCALE_TAG = "locale_tag"

    fun dispatch(context: Context, tag: String) {
        val appContext = context.applicationContext
        appContext.sendBroadcast(
            Intent(ACTION_LOCALE_REFRESH).apply {
                setPackage(appContext.packageName)
                putExtra(EXTRA_LOCALE_TAG, tag)
            },
        )
        QuickNoteWidget.updateAllWidgets(appContext)
        TaskWidget.refreshAllWidgets(appContext, immediate = true)
        QuickDailyReadWidget.refreshAllWidgets(appContext, immediate = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                TileService.requestListeningState(
                    appContext,
                    ComponentName(appContext, QuickTileService::class.java),
                )
            }
        }
    }
}
