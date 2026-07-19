package com.quickdaily

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the launcher callback only after a pin shortcut/widget request succeeds. */
class ShortcutPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PIN_SUCCEEDED) {
            BetaLogger.log("Shortcut", "launcher confirmed pin request")
        }
    }

    companion object {
        const val ACTION_PIN_SUCCEEDED = "com.quickdaily.PIN_SUCCEEDED"
    }
}
