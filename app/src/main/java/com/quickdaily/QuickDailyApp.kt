package com.quickdaily

import android.app.Application
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
        FileUtil.recoverPendingSaves()
    }
}
