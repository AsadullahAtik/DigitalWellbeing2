package com.wellbeing.tracker.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wellbeing.tracker.R
import com.wellbeing.tracker.data.repo.SettingsRepository
import com.wellbeing.tracker.domain.AlertLevel
import com.wellbeing.tracker.domain.AlertTexts
import com.wellbeing.tracker.domain.PendingAlert
import com.wellbeing.tracker.intercept.InterceptContract
import com.wellbeing.tracker.service.AlarmPlayer
import com.wellbeing.tracker.service.Permissions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

object Channels {
    const val MONITOR = "monitor"
    const val WARN = "warn"
    const val ALARM = "alarm"

    fun ensure(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(MONITOR, "Tracking", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(WARN, "Limit warnings", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(ALARM, "Limit reached", NotificationManager.IMPORTANCE_HIGH))
    }
}

@Singleton
class AlertDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    suspend fun dispatch(alert: PendingAlert) {
        Channels.ensure(context)
        val s = settings.current()
        when (alert.level) {
            AlertLevel.WARN -> if (s.notify) {
                notify(alert.limitKey.hashCode(), Channels.WARN, "সীমার কাছে পৌঁছেছো",
                    AlertTexts.warn(alert.limitKey, alert.current, alert.effective))
            }
            AlertLevel.OVER -> {
                val t = AlertTexts.over(alert.limitKey, alert.current, alert.effective)
                if (s.notify) notify(alert.limitKey.hashCode() + 1, Channels.ALARM, t.title, t.body)
                if (s.alarm) {
                    if (s.alarmSound) AlarmPlayer.start(context)
                    if (Permissions.overlay(context)) {
                        context.startActivity(
                            InterceptContract.intent(
                                context, InterceptContract.MODE_ALARM, limitKey = alert.limitKey,
                                current = alert.current, effective = alert.effective,
                            )
                        )
                    }
                }
            }
        }
    }

    fun info(title: String, text: String) {
        Channels.ensure(context)
        notify(INFO_ID, Channels.WARN, title, text)
    }

    private fun notify(id: Int, channel: String, title: String, text: String) {
        if (!Permissions.notifications(context)) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(if (channel == Channels.ALARM) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val INFO_ID = 9001
    }
}
