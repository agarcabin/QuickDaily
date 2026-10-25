package com.quickdaily

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.quickdaily.util.FileUtil
import com.quickdaily.util.StorageContextHolder

class QuickDailyApp : Application() {
    companion object {
        lateinit var instance: QuickDailyApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        StorageContextHolder.init(this)
        ObsidianConfigSync.initialize(this)
        ObsidianConfigSync.refreshAsync(this)
        // A stopped SAF provider must never block Application.onCreate or all entry points freeze.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val previous = com.quickdaily.util.VaultStoragePrefs.current(this@QuickDailyApp)
                val local = com.quickdaily.util.VaultStoragePrefs.localMigrationCandidate(this@QuickDailyApp, previous.rootPath)
                if (local != null) com.quickdaily.util.QuickDailyConfigMutationLock.withLock {
                    if (com.quickdaily.util.VaultStoragePrefs.current(this@QuickDailyApp).rootPath == previous.rootPath &&
                        !com.quickdaily.util.VaultStoragePrefs.isExplicitSaf(this@QuickDailyApp)) {
                        com.quickdaily.util.VaultStoragePrefs.saveFile(this@QuickDailyApp, local)
                        WidgetRefreshHelper.refreshAll(this@QuickDailyApp)
                    }
                }
            }.onFailure { BetaLogger.logException("Storage", "local_migration_failed", it) }
            runCatching { FileUtil.recoverPendingSaves() }
                .onFailure { BetaLogger.logException("Recovery", "startup_inspection_failed", it) }
        }
    }
}
