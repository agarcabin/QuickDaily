package com.quickdaily

/** Global display preferences shared by task-widget instances. */
object TaskWidgetDisplayPolicy {
    const val SHOW_COMPLETED_PREF_KEY = "task_show_completed"
    const val DEFAULT_SHOW_COMPLETED = false
    const val SHOW_FULL_CONTENT_PREF_KEY = "task_show_full_content"
    const val DEFAULT_SHOW_FULL_CONTENT = false
    const val GROUP_BY_DATE_PREF_KEY = "task_group_by_date"
    const val DEFAULT_GROUP_BY_DATE = true
}
