package com.wellbeing.tracker.domain

object LimitEvaluator {
    const val WARN_RATIO = 0.8f

    /**
     * Alerts that should fire now. [fired] holds (limitKey, level) pairs already sent today.
     * OVER wins over WARN, and each level fires once per day (until Snooze clears OVER).
     */
    fun pendingAlerts(
        statuses: Collection<LimitStatus>,
        fired: Set<Pair<String, AlertLevel>>,
    ): List<PendingAlert> = statuses.mapNotNull { s ->
        val limit = s.limit
        if (!limit.enabled || limit.effective <= 0) return@mapNotNull null
        val ratio = s.ratio
        when {
            ratio >= 1f && (limit.key to AlertLevel.OVER) !in fired ->
                PendingAlert(limit.key, AlertLevel.OVER, s.current, limit.effective)
            ratio >= WARN_RATIO && ratio < 1f && (limit.key to AlertLevel.WARN) !in fired ->
                PendingAlert(limit.key, AlertLevel.WARN, s.current, limit.effective)
            else -> null
        }
    }

    /** True when the app's own time limit or any of its content limits is over. */
    fun appLimitsOver(appId: String, statuses: Map<String, LimitStatus>): Boolean {
        val keys = listOf(LimitKeys.app(appId)) +
            ContentKind.entries.filter { it.appId == appId }.map { LimitKeys.content(it) }
        return keys.any { k ->
            val s = statuses[k]
            s != null && s.limit.enabled && s.level == Level.OVER
        }
    }
}

data class PolicyInput(
    val app: TrackedApp,
    val focusActive: Boolean,
    val bedtime: BedtimeWindow,
    val minuteOfDay: Int,
    val blockedAppIds: Set<String>,
    val strict: Boolean,
    val appLimitsOver: Boolean,
    val pauseEnabled: Boolean,
    val pauseSeconds: Int,
)

object PolicyEngine {
    /** Decides what happens when [PolicyInput.app] comes to the foreground. Order matters. */
    fun decide(i: PolicyInput): Decision {
        val blocked = i.app.id in i.blockedAppIds
        return when {
            i.focusActive && blocked -> Decision.Block(i.app.id, BlockReason.FOCUS)
            blocked && i.bedtime.contains(i.minuteOfDay) -> Decision.Block(i.app.id, BlockReason.BEDTIME)
            i.strict && i.appLimitsOver -> Decision.Block(i.app.id, BlockReason.LIMIT)
            i.pauseEnabled && i.app.timeLimited -> Decision.Pause(i.app.id, i.pauseSeconds)
            else -> Decision.Allow
        }
    }
}

object SnoozePolicy {
    const val DAILY_LIMIT = 2

    fun canSnooze(strict: Boolean, left: Int): Boolean = !strict && left > 0

    /** Minutes for time limits, items for content limits. */
    fun bonusFor(type: LimitType): Int = if (type == LimitType.CONTENT_COUNT) 3 else 5

    fun label(type: LimitType): String = if (type == LimitType.CONTENT_COUNT) "3টি" else "5 মিনিট"
}

object StrictRules {
    const val DISABLE_WAIT_SECONDS = 30

    /** In Strict Mode a limit can only get tighter, and cannot be switched off. */
    fun canChange(strict: Boolean, oldValue: Int, newValue: Int, wasEnabled: Boolean, nowEnabled: Boolean): Boolean {
        if (!strict) return true
        if (newValue > oldValue) return false
        if (wasEnabled && !nowEnabled) return false
        return true
    }
}

object DefaultLimits {
    fun all(): List<Limit> = listOf(
        Limit(LimitKeys.SCREEN, LimitType.SCREEN_TIME, true, 120),
        Limit(LimitKeys.app("facebook"), LimitType.APP_TIME, true, 60),
        Limit(LimitKeys.app("tiktok"), LimitType.APP_TIME, true, 30),
        Limit(LimitKeys.app("youtube"), LimitType.APP_TIME, true, 45),
        Limit(LimitKeys.content(ContentKind.FB_REELS), LimitType.CONTENT_COUNT, true, 30),
        Limit(LimitKeys.content(ContentKind.FB_POSTS), LimitType.CONTENT_COUNT, true, 50),
        Limit(LimitKeys.content(ContentKind.TT_VIDEOS), LimitType.CONTENT_COUNT, true, 20),
        Limit(LimitKeys.content(ContentKind.YT_VIDEOS), LimitType.CONTENT_COUNT, true, 15),
    )
}
