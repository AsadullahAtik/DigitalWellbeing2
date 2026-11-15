package com.wellbeing.tracker.domain

enum class ReportRange { DAILY, WEEKLY, MONTHLY }

data class ReportData(
    val unit: String,
    val limit: Int,
    val labels: List<String>,
    /** null means "not happened yet" (for example the evening of today). */
    val values: List<Int?>,
    val todayIndex: Int,
    val tile1: Pair<String, String>,
    val tile2: Pair<String, String>,
    val tips: List<String>,
)

object PauseReasons {
    val all: List<Pair<String, String>> = listOf(
        "kaj" to "কাজে লাগবে",
        "habit" to "অভ্যাসবশত",
        "bored" to "বিরক্ত লাগছে",
        "rest" to "বিশ্রাম",
    )

    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: key
}

object Labels {
    fun level(l: Level): String = when (l) {
        Level.OK -> "ঠিক আছে"
        Level.WARN -> "প্রায় শেষ"
        Level.OVER -> "সীমা শেষ"
    }
}

object ReportBuilder {
    private val bnDays = listOf("সোম", "মঙ্গল", "বুধ", "বৃহস্পতি", "শুক্র", "শনি", "রবি")

    /** [isoDow] is 1 for Monday to 7 for Sunday (java.time.DayOfWeek.value). */
    fun dayLabel(isoDow: Int): String = bnDays[(isoDow - 1).coerceIn(0, 6)]

    fun daily(
        partLabels: List<String>,
        partMinutes: List<Int?>,
        currentPart: Int,
        limit: Int,
        screenToday: Int,
        videosToday: Int,
        pauseSaved: Int,
        topReason: Pair<String, Int>?,
    ): ReportData {
        val tips = ArrayList<String>()
        val best = partMinutes.withIndex().filter { it.value != null && it.value!! > 0 }.maxByOrNull { it.value!! }
        if (best != null) tips += "সবচেয়ে বেশি ব্যবহার হয়েছে ${partLabels[best.index]}-এ (${Format.minutes(best.value!!)})।"
        tips += if (limit > screenToday) "আজকের সীমার মধ্যে আরও ${Format.minutes(limit - screenToday)} হাতে আছে।"
        else "আজকের সীমা পার হয়ে গেছে (${Format.minutes(screenToday - limit)} বেশি)।"
        if (pauseSaved > 0) tips += "আজ Pause Screen দেখে $pauseSaved বার App না খুলে ফিরেছো।"
        if (topReason != null) tips += "App খোলার সবচেয়ে বেশি কারণ: \"${PauseReasons.label(topReason.first)}\" (${topReason.second} বার)।"
        return ReportData(
            unit = "মিনিট", limit = limit, labels = partLabels, values = partMinutes, todayIndex = currentPart,
            tile1 = "আজকের Screen time" to Format.minutes(screenToday),
            tile2 = "ভিডিও ও Reels" to videosToday.toString(),
            tips = tips,
        )
    }

    /** [days] is oldest first and ends with today: (isoDow, minutes). */
    fun weekly(days: List<Pair<Int, Int>>, limit: Int, videosWeek: Int): ReportData {
        val vals = days.map { it.second }
        val avg = if (vals.isEmpty()) 0 else vals.sum() / vals.size
        val over = vals.count { it > limit }
        val tips = ArrayList<String>()
        tips += "এই সপ্তাহে ${vals.size} দিনের মধ্যে $over দিন Screen time সীমার ওপরে গেছে।"
        if (vals.isNotEmpty()) {
            val mx = vals.indices.maxByOrNull { vals[it] } ?: 0
            tips += "${dayLabel(days[mx].first)}বার সবচেয়ে বেশি ব্যবহার হয়েছে (${Format.minutes(vals[mx])})।"
        }
        tips += "ঘুমের এক ঘণ্টা আগে থেকে ফোন সরিয়ে রাখার চেষ্টা করো।"
        return ReportData(
            unit = "মিনিট", limit = limit, labels = days.map { dayLabel(it.first) }, values = vals,
            todayIndex = (vals.size - 1).coerceAtLeast(0),
            tile1 = "দৈনিক গড়" to Format.minutes(avg),
            tile2 = "মোট ভিডিও ও Reels" to videosWeek.toString(),
            tips = tips,
        )
    }

    /** [weekAverages] is four daily averages, oldest first; the last one is the current week. */
    fun monthly(weekAverages: List<Int>, limit: Int, videosTotal: Int): ReportData {
        val n = weekAverages.size
        val labels = List(n) { i -> if (i == n - 1) "চলতি" else "সপ্তাহ ${i + 1}" }
        val avg = if (n == 0) 0 else weekAverages.sum() / n
        val tips = ArrayList<String>()
        val earlier = weekAverages.dropLast(1)
        val list = earlier.joinToString(", ")
        tips += when {
            earlier.size >= 2 && earlier.zipWithNext().all { it.first > it.second } -> "আগের ${earlier.size} সপ্তাহ ধরে দৈনিক গড় কমছে: $list মিনিট।"
            earlier.size >= 2 && earlier.zipWithNext().all { it.first < it.second } -> "আগের ${earlier.size} সপ্তাহ ধরে দৈনিক গড় বাড়ছে: $list মিনিট।"
            else -> "সপ্তাহ ভেদে দৈনিক গড় ওঠানামা করছে: $list মিনিট।"
        }
        val cur = weekAverages.lastOrNull() ?: 0
        tips += if (cur > limit) "লক্ষ্য ${Format.minutes(limit)}-এ পৌঁছাতে এখনও গড়ে ${Format.minutes(cur - limit)} কমাতে হবে।"
        else "চলতি সপ্তাহের দৈনিক গড় সীমার মধ্যেই আছে।"
        return ReportData(
            unit = "দৈনিক গড়, মিনিট", limit = limit, labels = labels, values = weekAverages,
            todayIndex = (n - 1).coerceAtLeast(0),
            tile1 = "দৈনিক গড়" to Format.minutes(avg),
            tile2 = "মোট ভিডিও ও Reels" to videosTotal.toString(),
            tips = tips,
        )
    }
}
