package com.wellbeing.tracker.data.repo

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.wellbeing.tracker.domain.DateUtil
import com.wellbeing.tracker.domain.TrackedApps
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class UsageSnapshot(
    val screenMinutes: Int,
    val appMinutes: Map<String, Int>,
    val unlocks: Int,
    val screenIntervals: List<Pair<Long, Long>>,
)

/**
 * Reads UsageEvents for today. This is the reliable source for time and unlocks;
 * it needs the Usage Access permission.
 */
@Singleton
class UsageRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val usm get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun snapshot(from: Long = DateUtil.startOfDayMillis(), to: Long = System.currentTimeMillis()): UsageSnapshot {
        val events = try {
            usm.queryEvents(from, to)
        } catch (_: SecurityException) {
            return UsageSnapshot(0, emptyMap(), 0, emptyList())
        } ?: return UsageSnapshot(0, emptyMap(), 0, emptyList())

        val e = UsageEvents.Event()
        val appMillis = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()   // package -> resume time
        val screenIntervals = ArrayList<Pair<Long, Long>>()
        var screenOnAt = -1L
        var unlocks = 0
        var sawScreenEvents = false

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val t = e.timeStamp
            when (e.eventType) {
                EVENT_RESUMED -> resumedAt[pkg] = t
                EVENT_PAUSED, EVENT_STOPPED -> {
                    val start = resumedAt.remove(pkg)
                    if (start != null && t > start) {
                        TrackedApps.byPackage(pkg)?.let { app -> appMillis[app.id] = (appMillis[app.id] ?: 0L) + (t - start) }
                    }
                }
                EVENT_SCREEN_INTERACTIVE -> { sawScreenEvents = true; screenOnAt = t }
                EVENT_SCREEN_NON_INTERACTIVE -> {
                    sawScreenEvents = true
                    if (screenOnAt >= 0 && t > screenOnAt) screenIntervals += screenOnAt to t
                    screenOnAt = -1L
                }
                EVENT_KEYGUARD_HIDDEN -> unlocks++
            }
        }
        // Apps still in the foreground right now count up to [to].
        for ((pkg, start) in resumedAt) {
            TrackedApps.byPackage(pkg)?.let { app -> appMillis[app.id] = (appMillis[app.id] ?: 0L) + (to - start).coerceAtLeast(0) }
        }
        if (screenOnAt >= 0) screenIntervals += screenOnAt to to
        // Screen events begin at API 28. If none were seen, fall back to the first event of the day.
        if (!sawScreenEvents) screenIntervals.clear()

        val screenMillis = screenIntervals.sumOf { it.second - it.first }
        return UsageSnapshot(
            screenMinutes = (screenMillis / 60_000L).toInt(),
            appMinutes = appMillis.mapValues { (it.value / 60_000L).toInt() },
            unlocks = unlocks,
            screenIntervals = screenIntervals,
        )
    }

    /** Package name of the app in the foreground right now, or null. */
    fun foregroundPackage(lookbackMillis: Long = 10_000L): String? {
        val now = System.currentTimeMillis()
        val events = try { usm.queryEvents(now - lookbackMillis, now) } catch (_: SecurityException) { return null } ?: return null
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == EVENT_RESUMED) last = e.packageName
        }
        return last
    }

    /** Latest ACTIVITY_RESUMED in the lookback window as (package, timestamp), or null. */
    fun lastResume(lookbackMillis: Long = 10_000L): Pair<String, Long>? {
        val now = System.currentTimeMillis()
        val events = try { usm.queryEvents(now - lookbackMillis, now) } catch (_: SecurityException) { return null } ?: return null
        val e = UsageEvents.Event()
        var last: Pair<String, Long>? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == EVENT_RESUMED && e.packageName != null) last = e.packageName to e.timeStamp
        }
        return last
    }

    private companion object {
        // Literal values so minSdk 26 compiles; these events only occur on API 28+.
        // 1 and 2 are MOVE_TO_FOREGROUND/BACKGROUND on API < 29, ACTIVITY_RESUMED/PAUSED after; 23 is ACTIVITY_STOPPED.
        const val EVENT_RESUMED = 1
        const val EVENT_PAUSED = 2
        const val EVENT_STOPPED = 23
        const val EVENT_SCREEN_INTERACTIVE = 15
        const val EVENT_SCREEN_NON_INTERACTIVE = 16
        const val EVENT_KEYGUARD_HIDDEN = 18
    }
}
