package com.quickdaily.util

import org.junit.Assert.*
import org.junit.Test

class LocalVaultPathPolicyTest {
    @Test fun primarySystemFolderBecomesAReadableAbsolutePath() {
        assertEquals("/storage/emulated/0/Documents/我的仓库",
            LocalVaultPathPolicy.fromTreeId("com.android.externalstorage.documents", "primary:Documents/我的仓库"))
    }

    @Test fun providerIdsAreNotInventedAsPhysicalPaths() {
        assertNull(LocalVaultPathPolicy.fromTreeId("com.android.externalstorage.documents", "home:Vault"))
        assertNull(LocalVaultPathPolicy.fromTreeId("cloud.provider", "primary:Documents/Vault"))
        assertNull(LocalVaultPathPolicy.fromTreeId("downloads", "42"))
    }

    @Test fun absoluteInputIsNormalisedAndVirtualOrRelativeInputIsRejected() {
        assertEquals("/storage/emulated/0/Documents/Vault", LocalVaultPathPolicy.normalize(" /storage/emulated/0/Documents/Vault/ "))
        assertNull(LocalVaultPathPolicy.normalize("Documents/Vault"))
        assertNull(LocalVaultPathPolicy.normalize("saf://opaque"))
        assertNull(LocalVaultPathPolicy.normalize("/storage/home/Vault"))
        assertNull(LocalVaultPathPolicy.normalize("/storage/emulated/0/../secret"))
        assertNull(LocalVaultPathPolicy.normalize("/"))
    }
}
