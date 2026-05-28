package com.quickdairy.util

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * 防抖工具 — 连续调用时只执行最后一次。
 * 用于实时保存：用户不停打字，只在停手 500ms 后写入文件。
 */
class Debounce(
    private val delayMs: Long = 500L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onFire: suspend () -> Unit
) {
    private var job: Job? = null

    fun trigger() {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            onFire()
        }
    }

    /** 立即执行并取消延时任务（预留：供外部在切后台等场景调用） */
    fun fireNow() {
        job?.cancel()
        scope.launch { onFire() }
    }

    fun cancel() {
        job?.cancel()
    }
}
