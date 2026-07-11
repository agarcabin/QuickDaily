package com.quickdaily

import android.app.Application

class QuickDailyApp : Application() {
    companion object {
        lateinit var instance: QuickDailyApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
