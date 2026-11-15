package com.wellbeing.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitEvaluatorTest {
    private fun limit(value: Int, enabled: Boolean = true, bonus: Int = 0) =
        Limit("content:tt_videos", LimitType.CONTENT_COUNT, enabled, value, bonus)

    @Test fun levelsFollowThresholds() {
        assertEquals(Level.OK, LimitStatus(limit(20), 15).level)
        assertEquals(Level.WARN, LimitStatus(limit(20), 16).level)
        assertEquals(Level.WARN, LimitStatus(limit(20), 19).level)
        assertEquals(Level.OVER, LimitStatus(limit(20), 20).level)
        assertEquals(Level.OVER, LimitStatus(limit(20), 27).level)
    }

    @Test fun warnFiresOnceThenStops() {
        val s = listOf(LimitStatus(limit(20), 16))
        val first = LimitEvaluator.pendingAlerts(s, emptySet())
        assertEquals(1, first.size)
        assertEquals(AlertLevel.WARN, first[0].level)
        val fired = setOf("content:tt_videos" to AlertLevel.WARN)
        assertTrue(LimitEvaluator.pendingAlerts(s, fired).isEmpty())
    }

    @Test fun overBeatsWarnEvenWhenWarnNotFired() {
        val out = LimitEvaluator.pendingAlerts(listOf(LimitStatus(limit(20), 21)), emptySet())
        assertEquals(1, out.size)
        assertEquals(AlertLevel.OVER, out[0].level)
        assertEquals(20, out[0].effective)
    }

    @Test fun disabledLimitNeverAlerts() {
        assertTrue(LimitEvaluator.pendingAlerts(listOf(LimitStatus(limit(20, enabled = false), 50)), emptySet()).isEmpty())
    }

    @Test fun snoozeBonusMovesTheLine() {
        assertEquals(Level.OVER, LimitStatus(limit(20), 20).level)
        assertEquals(Level.WARN, LimitStatus(limit(20, bonus = 3), 20).level)
        assertEquals(23, limit(20, bonus = 3).effective)
    }

    @Test fun appLimitsOverChecksTimeAndContent() {
        val timeKey = LimitKeys.app("tiktok")
        val contentKey = LimitKeys.content(ContentKind.TT_VIDEOS)
        val under = mapOf(
            timeKey to LimitStatus(Limit(timeKey, LimitType.APP_TIME, true, 30), 10),
            contentKey to LimitStatus(Limit(contentKey, LimitType.CONTENT_COUNT, true, 20), 5),
        )
        assertFalse(LimitEvaluator.appLimitsOver("tiktok", under))
        val over = under + (contentKey to LimitStatus(Limit(contentKey, LimitType.CONTENT_COUNT, true, 20), 20))
        assertTrue(LimitEvaluator.appLimitsOver("tiktok", over))
        assertFalse(LimitEvaluator.appLimitsOver("youtube", over))
    }
}

class BedtimeWindowTest {
    @Test fun wrapsPastMidnight() {
        val w = BedtimeWindow(true, 22 * 60 + 30, 6 * 60 + 30)
        assertTrue(w.contains(23 * 60))
        assertTrue(w.contains(2 * 60))
        assertTrue(w.contains(22 * 60 + 30))
        assertFalse(w.contains(6 * 60 + 30))
        assertFalse(w.contains(12 * 60))
    }

    @Test fun sameDayWindow() {
        val w = BedtimeWindow(true, 13 * 60, 15 * 60)
        assertTrue(w.contains(14 * 60))
        assertFalse(w.contains(15 * 60))
        assertFalse(w.contains(12 * 60))
    }

    @Test fun disabledOrEmptyNeverMatches() {
        assertFalse(BedtimeWindow(false, 22 * 60, 6 * 60).contains(23 * 60))
        assertFalse(BedtimeWindow(true, 600, 600).contains(600))
    }
}

class PolicyEngineTest {
    private fun input(
        app: TrackedApp = TrackedApps.TIKTOK,
        focus: Boolean = false,
        minute: Int = 12 * 60,
        strict: Boolean = false,
        over: Boolean = false,
        pause: Boolean = true,
        blocked: Set<String> = setOf("facebook", "tiktok", "youtube"),
    ) = PolicyInput(
        app = app,
        focusActive = focus,
        bedtime = BedtimeWindow(true, 22 * 60 + 30, 6 * 60 + 30),
        minuteOfDay = minute,
        blockedAppIds = blocked,
        strict = strict,
        appLimitsOver = over,
        pauseEnabled = pause,
        pauseSeconds = 10,
    )

    @Test fun focusBlocksListedApps() {
        assertEquals(Decision.Block("tiktok", BlockReason.FOCUS), PolicyEngine.decide(input(focus = true)))
    }

    @Test fun focusIgnoresUnlistedApps() {
        val d = PolicyEngine.decide(input(app = TrackedApps.CHROME, focus = true))
        assertEquals(Decision.Allow, d)
    }

    @Test fun bedtimeBlocksAtNight() {
        assertEquals(Decision.Block("tiktok", BlockReason.BEDTIME), PolicyEngine.decide(input(minute = 23 * 60)))
    }

    @Test fun strictBlocksOnlyWhenOver() {
        assertEquals(Decision.Block("tiktok", BlockReason.LIMIT), PolicyEngine.decide(input(strict = true, over = true)))
        assertEquals(Decision.Pause("tiktok", 10), PolicyEngine.decide(input(strict = true, over = false)))
        assertEquals(Decision.Pause("tiktok", 10), PolicyEngine.decide(input(strict = false, over = true)))
    }

    @Test fun pauseNeedsSettingAndTimeLimitedApp() {
        assertEquals(Decision.Allow, PolicyEngine.decide(input(pause = false)))
        assertEquals(Decision.Allow, PolicyEngine.decide(input(app = TrackedApps.WHATSAPP, blocked = emptySet())))
    }

    @Test fun focusOutranksBedtimeAndLimit() {
        val d = PolicyEngine.decide(input(focus = true, minute = 23 * 60, strict = true, over = true))
        assertEquals(Decision.Block("tiktok", BlockReason.FOCUS), d)
    }
}

class RulesTest {
    @Test fun strictOnlyAllowsTighterLimits() {
        assertTrue(StrictRules.canChange(true, 30, 25, true, true))
        assertFalse(StrictRules.canChange(true, 30, 35, true, true))
        assertFalse(StrictRules.canChange(true, 30, 30, true, false))
        assertTrue(StrictRules.canChange(false, 30, 60, true, false))
    }

    @Test fun snoozeRules() {
        assertTrue(SnoozePolicy.canSnooze(strict = false, left = 1))
        assertFalse(SnoozePolicy.canSnooze(strict = false, left = 0))
        assertFalse(SnoozePolicy.canSnooze(strict = true, left = 2))
        assertEquals(3, SnoozePolicy.bonusFor(LimitType.CONTENT_COUNT))
        assertEquals(5, SnoozePolicy.bonusFor(LimitType.APP_TIME))
    }

    @Test fun snoozeLeftResetsOnNewDay() {
        val s = AppSettings(snoozeUsed = 2, snoozeDate = "2026-11-13")
        assertEquals(0, s.snoozeLeft("2026-11-13"))
        assertEquals(2, s.snoozeLeft("2026-11-14"))
    }
}

class DayPartsAndFormatTest {
    @Test fun overlapMinutesAreClippedToThePart() {
        val dayStart = 0L
        val h = 3_600_000L
        // screen on 05:30 to 06:30 -> 30 min in "ভোর", 30 min in "সকাল"
        val intervals = listOf((5 * h + h / 2) to (6 * h + h / 2))
        assertEquals(30, DayParts.minutes(intervals, dayStart, DayParts.parts[0]))
        assertEquals(30, DayParts.minutes(intervals, dayStart, DayParts.parts[1]))
        assertEquals(0, DayParts.minutes(intervals, dayStart, DayParts.parts[2]))
    }

    @Test fun formatting() {
        assertEquals("45 মি", Format.minutes(45))
        assertEquals("1 ঘ 34 মি", Format.minutes(94))
        assertEquals("22:30", Format.clock(22 * 60 + 30))
        assertEquals("06:30", Format.clock(-1050))
        assertEquals("05:07", Format.mmss(307))
    }

    @Test fun alertTextsMentionTheRightThing() {
        val t = AlertTexts.over(LimitKeys.content(ContentKind.TT_VIDEOS), 20, 20)
        assertTrue(t.title.contains("TikTok"))
        assertTrue(t.body.contains("20"))
        assertTrue(AlertTexts.warn(LimitKeys.SCREEN, 100, 120).contains("Screen time"))
    }
}
