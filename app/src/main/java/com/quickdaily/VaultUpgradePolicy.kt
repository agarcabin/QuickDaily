package com.quickdaily

internal sealed interface VaultUpgradeResult {
    data class Ready(val path: String) : VaultUpgradeResult
    data class PermissionRequired(val path: String) : VaultUpgradeResult
    data object Reselect : VaultUpgradeResult
}

internal object VaultUpgradePolicy {
    fun check(candidate: String?, permissionGranted: Boolean, verify: (String) -> Boolean): VaultUpgradeResult {
        if (candidate.isNullOrBlank()) return VaultUpgradeResult.Reselect
        if (!permissionGranted) return VaultUpgradeResult.PermissionRequired(candidate)
        return try {
            if (verify(candidate)) VaultUpgradeResult.Ready(candidate) else VaultUpgradeResult.Reselect
        } catch (_: Exception) {
            VaultUpgradeResult.Reselect
        }
    }
}
