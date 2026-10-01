package com.quickdaily

import android.content.Context

/** Shared identity carried by RemoteViews collection rows and legacy factories. */
internal object WidgetFillInContract {
    const val EXTRA_KIND = "quickdaily_widget_kind"
    const val EXTRA_WIDGET_ID = "quickdaily_widget_id"
    const val EXTRA_GENERATION = "quickdaily_widget_generation"

    const val KIND_TASK = "task"
    const val KIND_READ = "read"
}

/** Monotonic per-widget generation used to prevent stale rows publishing after reconfiguration. */
internal object WidgetGeneration {
    private const val PREFS = "QuickDaily"
    private const val KEY_PREFIX = "widget_generation_"
    private val lock = Any()
    private val memory = mutableMapOf<String, Long>()

    fun next(context: Context, kind: String, widgetId: Int): Long = synchronized(lock) {
        val key = key(kind, widgetId)
        val current = maxOf(memory[key] ?: 0L, preferences(context).getLong(key, 0L))
        val generation = current + 1L
        memory[key] = generation
        preferences(context).edit().putLong(key, generation).apply()
        generation
    }

    fun current(context: Context, kind: String, widgetId: Int): Long = synchronized(lock) {
        val key = key(kind, widgetId)
        val value = maxOf(memory[key] ?: 0L, preferences(context).getLong(key, 0L))
        memory[key] = value
        value
    }

    fun isCurrent(context: Context, kind: String, widgetId: Int, generation: Long): Boolean =
        generation <= 0L || current(context, kind, widgetId) == generation

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(kind: String, widgetId: Int): String = "$KEY_PREFIX${kind}_$widgetId"
}
