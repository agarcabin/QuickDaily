package com.quickdaily

import com.quickdaily.ui.SettingsConfigReadPolicy
import com.quickdaily.ui.SettingsConfigReadRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsConfigReadPolicyTest {
    @Test
    fun delayedVaultAIsIgnoredAfterFastVaultBBecomesCurrent() {
        val aReadStarted = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        val requestA = SettingsConfigReadRequest(
            generation = 1L,
            vaultPath = "/vault-A",
            customUri = "",
            useCustomConfig = false,
        )
        val requestB = SettingsConfigReadRequest(
            generation = 2L,
            vaultPath = "/vault-B",
            customUri = "",
            useCustomConfig = false,
        )

        val delayedA = Thread {
            aReadStarted.countDown()
            releaseA.await(1, TimeUnit.SECONDS)
        }
        delayedA.start()
        assertTrue(aReadStarted.await(1, TimeUnit.SECONDS))

        assertTrue(
            SettingsConfigReadPolicy.canApply(
                request = requestB,
                currentGeneration = requestB.generation,
                currentVaultPath = requestB.vaultPath,
                currentCustomUri = requestB.customUri,
                currentUseCustomConfig = requestB.useCustomConfig,
            ),
        )

        releaseA.countDown()
        delayedA.join(1_000)
        assertFalse(
            SettingsConfigReadPolicy.canApply(
                request = requestA,
                currentGeneration = requestB.generation,
                currentVaultPath = requestB.vaultPath,
                currentCustomUri = requestB.customUri,
                currentUseCustomConfig = requestB.useCustomConfig,
            ),
        )
    }

    @Test
    fun clearedCustomConfigInvalidatesThePreviousCustomRead() {
        val previous = SettingsConfigReadRequest(
            generation = 4L,
            vaultPath = "/vault",
            customUri = "content://config-A",
            useCustomConfig = true,
        )

        assertFalse(
            SettingsConfigReadPolicy.canApply(
                request = previous,
                currentGeneration = 5L,
                currentVaultPath = "/vault",
                currentCustomUri = "",
                currentUseCustomConfig = false,
            ),
        )
    }
}
