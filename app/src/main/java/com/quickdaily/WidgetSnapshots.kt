package com.quickdaily

import java.util.concurrent.ConcurrentHashMap

/** Legacy factories only read completed snapshots; they never query a document provider. */
internal object WidgetSnapshots {
    data class Read(val generation: Long, val items: List<PreparedReadWidgetItem>, val message: UiText?)
    data class Tasks(val generation: Long, val items: List<PreparedTaskWidgetItem>, val message: UiText?)
    val read = ConcurrentHashMap<Int, Read>()
    val tasks = ConcurrentHashMap<Int, Tasks>()
}
