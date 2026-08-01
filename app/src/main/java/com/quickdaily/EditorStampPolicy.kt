package com.quickdaily

import com.quickdaily.util.DateUtil

object EditorStampPolicy {
    /** Toolbar timestamp: fixed 24-hour `HH:mm` format, followed by a space. */
    fun toolbarTimestampInsertion(): String = "${DateUtil.nowTimeStr()} "

    fun timestampInsertion(format: String): String? = when (format) {
        "none" -> null
        "time_only" -> "${DateUtil.nowTimeStr()} "
        "time_only_seconds" -> "${DateUtil.nowTimeSecondsStr()} "
        "list" -> "- "
        "ordered" -> "1. "
        "list_time" -> "- ${DateUtil.nowTimeStr()} "
        "list_time_seconds" -> "- ${DateUtil.nowTimeSecondsStr()} "
        else -> "${DateUtil.nowTimeStr()} "
    }

    fun dateInsertion(): String = DateUtil.todayStr("YYYY-MM-DD")
}
