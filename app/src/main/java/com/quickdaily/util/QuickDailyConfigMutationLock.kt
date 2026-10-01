package com.quickdaily.util

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Serializes preference snapshots so concurrent settings/vault writes cannot overwrite each other. */
object QuickDailyConfigMutationLock {
    private val lock = ReentrantLock()

    fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
