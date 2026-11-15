package com.wellbeing.tracker.intercept

import android.content.Context
import android.content.Intent

/** Extras for [InterceptActivity], which shows the Pause, Block and Alarm screens. */
object InterceptContract {
    const val EXTRA_MODE = "mode"
    const val EXTRA_APP_ID = "app_id"
    const val EXTRA_REASON = "reason"
    const val EXTRA_LIMIT_KEY = "limit_key"
    const val EXTRA_SECONDS = "seconds"
    const val EXTRA_CURRENT = "current"
    const val EXTRA_EFFECTIVE = "effective"

    const val MODE_PAUSE = "pause"
    const val MODE_BLOCK = "block"
    const val MODE_ALARM = "alarm"

    fun intent(
        context: Context,
        mode: String,
        appId: String? = null,
        reason: String? = null,
        limitKey: String? = null,
        seconds: Int = 0,
        current: Int = 0,
        effective: Int = 0,
    ): Intent = Intent(context, InterceptActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        putExtra(EXTRA_MODE, mode)
        appId?.let { putExtra(EXTRA_APP_ID, it) }
        reason?.let { putExtra(EXTRA_REASON, it) }
        limitKey?.let { putExtra(EXTRA_LIMIT_KEY, it) }
        putExtra(EXTRA_SECONDS, seconds)
        putExtra(EXTRA_CURRENT, current)
        putExtra(EXTRA_EFFECTIVE, effective)
    }
}
