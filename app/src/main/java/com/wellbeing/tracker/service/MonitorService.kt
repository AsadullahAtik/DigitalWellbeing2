package com.wellbeing.tracker.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wellbeing.tracker.R
import com.wellbeing.tracker.data.repo.UsageRepository
import com.wellbeing.tracker.domain.Decision
import com.wellbeing.tracker.domain.TrackedApps
import com.wellbeing.tracker.engine.Channels
import com.wellbeing.tracker.engine.WellbeingEngine
import com.wellbeing.tracker.intercept.InterceptContract
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Foreground service: polls the foreground app (for Pause/Block) and checks limits. */
@AndroidEntryPoint
class MonitorService : Service() {
    @Inject lateinit var engine: WellbeingEngine
    @Inject lateinit var usage: UsageRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Channels.ensure(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        var lastSeen: Pair<String, Long>? = null
        var ticks = 0
        while (scope.isActive) {
            try {
                if (Permissions.usageAccess(this)) {
                    val resume = usage.lastResume()
                    if (resume != null && resume != lastSeen) {
                        lastSeen = resume
                        onForeground(resume.first)
                    }
                    // Limits are re-checked about every 10 seconds, and right after any app switch.
                    if (ticks % 5 == 0) {
                        engine.evaluate()
                        // A block can start while the app is already open (Focus started, Bedtime began).
                        val current = lastSeen?.first
                        val screenOn = (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
                        if (screenOn && current != null && current != packageName) recheckBlock(current)
                    }
                }
            } catch (_: Exception) {
                // One bad poll must not stop monitoring.
            }
            ticks++
            delay(POLL_MS)
        }
    }

    private suspend fun recheckBlock(pkg: String) {
        val app = TrackedApps.byPackage(pkg) ?: return
        if (!Permissions.overlay(this)) return
        val d = engine.decideFor(app)
        if (d is Decision.Block) {
            startActivity(InterceptContract.intent(this, InterceptContract.MODE_BLOCK, appId = d.appId, reason = d.reason.name))
        }
    }

    private suspend fun onForeground(pkg: String) {
        if (pkg == packageName) return
        val app = TrackedApps.byPackage(pkg) ?: return
        if (!Permissions.overlay(this)) return
        when (val d = engine.decideFor(app)) {
            is Decision.Allow -> Unit
            // The grace period only skips Pause. Focus, Bedtime and Strict blocks always apply.
            is Decision.Pause -> if (!PolicyGate.isAllowed(app.id)) startActivity(
                InterceptContract.intent(this, InterceptContract.MODE_PAUSE, appId = d.appId, seconds = d.seconds)
            )
            is Decision.Block -> startActivity(
                InterceptContract.intent(this, InterceptContract.MODE_BLOCK, appId = d.appId, reason = d.reason.name)
            )
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, Channels.MONITOR)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Digital Wellbeing চালু আছে")
            .setContentText("আজকের ব্যবহার ট্র্যাক হচ্ছে")
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val POLL_MS = 2_000L

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }
    }
}
