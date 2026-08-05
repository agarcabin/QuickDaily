package com.quickdaily

internal enum class LauncherIconMode {
    BLUE,
}

internal object LauncherIconPolicy {
    fun mode(useMonet: Boolean, sdkInt: Int): LauncherIconMode = LauncherIconMode.BLUE
}
