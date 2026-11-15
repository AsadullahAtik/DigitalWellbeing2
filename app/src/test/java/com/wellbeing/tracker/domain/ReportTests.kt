package com.wellbeing.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {
    @Test fun weeklyCountsOverDaysAndPicksPeak() {
        val days = listOf(1 to 100, 2 to 150, 3 to 90, 4 to 200, 5 to 130, 6 to 80, 7 to 125)
        val r = ReportBuilder.weekly(days, limit = 120, videosWeek = 55)
        assertEquals(7, r.values.size)
        assertEquals(6, r.todayIndex)
        assertEquals("55", r.tile2.second)
        assertTrue(r.tips[0].contains("7 দিনের মধ্যে 4 দিন"))
        assertTrue(r.tips[1].contains("বৃহস্পতিবার"))
        assertEquals("দৈনিক গড়", r.tile1.first)
        assertEquals(Format.minutes(125), r.tile1.second) // (100+150+90+200+130+80+125)/7 = 125
    }

    @Test fun dailyHandlesFutureParts() {
        val labels = listOf("সকাল", "দুপুর", "বিকাল")
        val r = ReportBuilder.daily(labels, listOf(31, 38, null), 1, limit = 120, screenToday = 69, videosToday = 12, pauseSaved = 2, topReason = "habit" to 3)
        assertEquals(1, r.todayIndex)
        assertTrue(r.tips.any { it.contains("দুপুর-এ") })
        assertTrue(r.tips.any { it.contains("আরও ${Format.minutes(51)} হাতে") })
        assertTrue(r.tips.any { it.contains("2 বার App না খুলে") })
        assertTrue(r.tips.any { it.contains("অভ্যাসবশত") && it.contains("3 বার") })
    }

    @Test fun dailyOverLimitTip() {
        val r = ReportBuilder.daily(listOf("সকাল"), listOf(130), 0, 120, 130, 0, 0, null)
        assertTrue(r.tips.any { it.contains("পার হয়ে গেছে") })
        assertEquals(1, r.tips.size.coerceAtMost(1))
    }

    @Test fun monthlyTrend() {
        val down = ReportBuilder.monthly(listOf(158, 149, 141, 130), 120, 500)
        assertEquals("চলতি", down.labels.last())
        assertTrue(down.tips[0].contains("কমছে"))
        assertTrue(down.tips[1].contains(Format.minutes(10)))
        val up = ReportBuilder.monthly(listOf(100, 110, 120, 90), 120, 0)
        assertTrue(up.tips[0].contains("বাড়ছে"))
        assertTrue(up.tips[1].contains("সীমার মধ্যেই"))
    }

    @Test fun labelsAndReasons() {
        assertEquals("সীমা শেষ", Labels.level(Level.OVER))
        assertEquals("বিশ্রাম", PauseReasons.label("rest"))
        assertEquals("zzz", PauseReasons.label("zzz"))
        assertEquals("রবি", ReportBuilder.dayLabel(7))
    }
}
