package com.quickdaily.util

import android.content.Context

object RecentTags {
    private const val KEY = "recent_tags"
    private const val MAX = 20
    private val tagRegex = Regex("(?:^|\\s)#([\\p{L}\\p{N}_/-]+)")

    fun get(context: Context): List<String> =
        context.getSharedPreferences("QuickDaily", 0).getString(KEY, "")
            .orEmpty().split('|').filter { it.isNotBlank() }

    fun recordFromText(context: Context, text: String) {
        val used = tagRegex.findAll(text).map { it.groupValues[1] }.toList()
        if (used.isEmpty()) return
        val merged = (used.asReversed() + get(context)).distinct().take(MAX)
        context.getSharedPreferences("QuickDaily", 0).edit().putString(KEY, merged.joinToString("|")).apply()
    }

    fun record(context: Context, tag: String) = recordFromText(context, "#$tag")
}
