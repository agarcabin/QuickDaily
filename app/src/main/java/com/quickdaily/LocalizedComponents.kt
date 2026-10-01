package com.quickdaily

import android.app.Activity
import android.content.Context
import android.widget.RemoteViewsService
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleService

/** Base classes keep locale wrapping consistent across every Android entry point. */
abstract class LocalizedComponentActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.localizedContext(newBase))
    }
}

abstract class LocalizedActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.localizedContext(newBase))
    }
}

abstract class LocalizedLifecycleService : LifecycleService() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.localizedContext(newBase))
    }
}

abstract class LocalizedRemoteViewsService : RemoteViewsService() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.localizedContext(newBase))
    }
}
