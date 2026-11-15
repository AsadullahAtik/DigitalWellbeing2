package com.wellbeing.tracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts monitoring after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Permissions.usageAccess(context)) return
        try {
            MonitorService.start(context)
        } catch (_: Exception) {
            // Android 12+ may refuse a background start; the app start will retry.
        }
    }
}
