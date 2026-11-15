package com.wellbeing.tracker.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object DateUtil {
    fun today(): String = LocalDate.now().toString()

    fun startOfDayMillis(date: LocalDate = LocalDate.now()): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun minuteOfDayNow(): Int {
        val t = LocalTime.now()
        return t.hour * 60 + t.minute
    }
}

object DayParts {
    data class Part(val label: String, val startHour: Int, val endHour: Int)

    val parts: List<Part> = listOf(
        Part("ভোর", 0, 6),
        Part("সকাল", 6, 12),
        Part("দুপুর", 12, 16),
        Part("বিকাল", 16, 19),
        Part("সন্ধ্যা", 19, 22),
        Part("রাত", 22, 24),
    )

    /** Minutes of screen-on time inside [part], given screen-on [intervals] (epoch millis) for the day starting at [dayStart]. */
    fun minutes(intervals: List<Pair<Long, Long>>, dayStart: Long, part: Part): Int {
        val from = dayStart + part.startHour * 3_600_000L
        val to = dayStart + part.endHour * 3_600_000L
        var total = 0L
        for ((s, e) in intervals) {
            val overlap = minOf(e, to) - maxOf(s, from)
            if (overlap > 0) total += overlap
        }
        return (total / 60_000L).toInt()
    }
}
