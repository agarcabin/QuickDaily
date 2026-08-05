package com.quickdaily

internal object EditorReloadTargetPolicy {
    fun targetToReload(currentTarget: String): String = currentTarget.trim()
}
