package com.quickdaily

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class VaultUpgradePolicyTest {
    @Test fun unknownOrBrokenOldPathRequiresReselection() {
        assertEquals(VaultUpgradeResult.Reselect, VaultUpgradePolicy.check(null, true) { error("must not probe an unknown path") })
        assertEquals(VaultUpgradeResult.Reselect, VaultUpgradePolicy.check("", false) { error("must not probe") })
    }

    @Test fun permissionIsRequestedBeforeTryingToSwitchThePath() {
        assertEquals(VaultUpgradeResult.PermissionRequired("/vault"),
            VaultUpgradePolicy.check("/vault", false) { error("must wait for permission") })
    }

    @Test fun permissionAloneIsNotEnoughToSwitchAnUnreadableVault() {
        assertEquals(VaultUpgradeResult.Reselect, VaultUpgradePolicy.check("/vault", true) { false })
    }

    @Test fun successfulProbeAllowsTheExactChosenDirectory() {
        assertEquals(VaultUpgradeResult.Ready("/vault/原仓库"), VaultUpgradePolicy.check("/vault/原仓库", true) {
            assertEquals("/vault/原仓库", it)
            true
        })
    }

    @Test fun probeFailureDoesNotBecomeAReadyConnection() {
        assertEquals(VaultUpgradeResult.Reselect, VaultUpgradePolicy.check("/vault", true) { throw IOException("unavailable") })
    }
}
