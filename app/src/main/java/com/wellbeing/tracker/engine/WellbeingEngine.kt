package com.wellbeing.tracker.engine

import com.wellbeing.tracker.data.repo.ContentCountRepository
import com.wellbeing.tracker.data.repo.LimitRepository
import com.wellbeing.tracker.data.repo.SettingsRepository
import com.wellbeing.tracker.data.repo.StatsRepository
import com.wellbeing.tracker.data.repo.TodayStats
import com.wellbeing.tracker.data.repo.UsageRepository
import com.wellbeing.tracker.data.repo.UsageSnapshot
import com.wellbeing.tracker.domain.ContentKind
import com.wellbeing.tracker.domain.DateUtil
import com.wellbeing.tracker.domain.Decision
import com.wellbeing.tracker.domain.Limit
import com.wellbeing.tracker.domain.LimitEvaluator
import com.wellbeing.tracker.domain.LimitKeys
import com.wellbeing.tracker.domain.LimitStatus
import com.wellbeing.tracker.domain.LimitType
import com.wellbeing.tracker.domain.PolicyEngine
import com.wellbeing.tracker.domain.PolicyInput
import com.wellbeing.tracker.domain.TrackedApp
import javax.inject.Inject
import javax.inject.Singleton

data class EngineState(
    val usage: UsageSnapshot,
    val counts: Map<ContentKind, Int>,
    val limits: List<Limit>,
    val statuses: Map<String, LimitStatus>,
    val stats: TodayStats = TodayStats(),
)

@Singleton
class WellbeingEngine @Inject constructor(
    private val limits: LimitRepository,
    private val counts: ContentCountRepository,
    private val settings: SettingsRepository,
    private val usage: UsageRepository,
    private val dispatcher: AlertDispatcher,
    private val stats: StatsRepository,
) {
    suspend fun loadState(): EngineState {
        val snapshot = usage.snapshot()
        val c = counts.today()
        val l = limits.all()
        return EngineState(snapshot, c, l, statusesOf(l, snapshot, c), stats.today())
    }

    fun statusesOf(l: List<Limit>, snap: UsageSnapshot, c: Map<ContentKind, Int>): Map<String, LimitStatus> =
        l.associate { limit -> limit.key to LimitStatus(limit, currentValue(limit, snap, c)) }

    private fun currentValue(limit: Limit, snap: UsageSnapshot, c: Map<ContentKind, Int>): Int =
        when (limit.type) {
            // Without screen events (Android 8) fall back to the sum of tracked app time.
            LimitType.SCREEN_TIME -> if (snap.screenIntervals.isEmpty()) snap.appMinutes.values.sum() else snap.screenMinutes
            LimitType.APP_TIME -> snap.appMinutes[LimitKeys.appIdOf(limit.key)] ?: 0
            LimitType.CONTENT_COUNT -> LimitKeys.contentOf(limit.key)?.let { c[it] } ?: 0
        }

    /** Fires any warning or limit-reached alert that is due. Safe to call often. */
    suspend fun evaluate() {
        finalizeFocus()
        val state = loadState()
        val fired = limits.firedToday()
        LimitEvaluator.pendingAlerts(state.statuses.values, fired).forEach { alert ->
            limits.markFired(alert.limitKey, alert.level)
            dispatcher.dispatch(alert)
        }
    }

    /** Counts a focus session that ran to its end (once) and tells the user. */
    suspend fun finalizeFocus() {
        val minutes = settings.takeExpiredFocus(System.currentTimeMillis())
        if (minutes > 0) {
            stats.addFocusMinutes(minutes)
            dispatcher.info("ফোকাস শেষ", "$minutes মিনিট ফোন ছাড়া কাজ করেছো।")
        }
    }

    /** What to do when [app] comes to the foreground. */
    suspend fun decideFor(app: TrackedApp): Decision {
        val s = settings.current()
        val state = loadState()
        return PolicyEngine.decide(
            PolicyInput(
                app = app,
                focusActive = s.focusActive(System.currentTimeMillis()),
                bedtime = s.bedtime,
                minuteOfDay = DateUtil.minuteOfDayNow(),
                blockedAppIds = s.blockedAppIds,
                strict = s.strict,
                appLimitsOver = LimitEvaluator.appLimitsOver(app.id, state.statuses),
                pauseEnabled = s.pauseEnabled,
                pauseSeconds = s.pauseSeconds,
            )
        )
    }

    /** Used by the Accessibility counter: bump a count and check limits right away. */
    suspend fun onContentItem(kind: ContentKind, by: Int = 1) {
        repeat(by) { counts.increment(kind) }
        evaluate()
    }
}
