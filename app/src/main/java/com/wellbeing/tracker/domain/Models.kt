package com.wellbeing.tracker.domain

/** Social-media content the Accessibility counter can try to count (best-effort). */
enum class ContentKind(val key: String, val label: String, val unit: String, val appId: String) {
    FB_REELS("fb_reels", "Facebook Reels", "Reels", "facebook"),
    FB_POSTS("fb_posts", "Facebook Posts", "Post", "facebook"),
    TT_VIDEOS("tt_videos", "TikTok Videos", "ভিডিও", "tiktok"),
    YT_VIDEOS("yt_videos", "YouTube Videos", "ভিডিও", "youtube");

    companion object {
        fun fromKey(key: String): ContentKind? = entries.firstOrNull { it.key == key }
    }
}

data class TrackedApp(
    val id: String,
    val label: String,
    val packages: Set<String>,
    /** Apps with a time limit also get Pause Screen and strict blocking. */
    val timeLimited: Boolean,
) {
    val contentKinds: List<ContentKind> get() = ContentKind.entries.filter { it.appId == id }
}

object TrackedApps {
    val FACEBOOK = TrackedApp("facebook", "Facebook", setOf("com.facebook.katana", "com.facebook.lite"), true)
    val TIKTOK = TrackedApp("tiktok", "TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), true)
    val YOUTUBE = TrackedApp("youtube", "YouTube", setOf("com.google.android.youtube"), true)
    val CHROME = TrackedApp("chrome", "Chrome", setOf("com.android.chrome"), false)
    val WHATSAPP = TrackedApp("whatsapp", "WhatsApp", setOf("com.whatsapp", "com.whatsapp.w4b"), false)

    val all: List<TrackedApp> = listOf(FACEBOOK, TIKTOK, YOUTUBE, CHROME, WHATSAPP)
    val limited: List<TrackedApp> = all.filter { it.timeLimited }

    fun byPackage(pkg: String): TrackedApp? = all.firstOrNull { pkg in it.packages }
    fun byId(id: String): TrackedApp? = all.firstOrNull { it.id == id }
}

enum class LimitType { SCREEN_TIME, APP_TIME, CONTENT_COUNT }

object LimitKeys {
    const val SCREEN = "screen"

    fun app(appId: String): String = "app:$appId"
    fun content(kind: ContentKind): String = "content:${kind.key}"

    fun typeOf(key: String): LimitType = when {
        key == SCREEN -> LimitType.SCREEN_TIME
        key.startsWith("app:") -> LimitType.APP_TIME
        else -> LimitType.CONTENT_COUNT
    }

    fun appIdOf(key: String): String? = if (key.startsWith("app:")) key.removePrefix("app:") else null
    fun contentOf(key: String): ContentKind? =
        if (key.startsWith("content:")) ContentKind.fromKey(key.removePrefix("content:")) else null
}

/** [value] is minutes for time limits and a count for content limits. [bonus] comes from Snooze and resets daily. */
data class Limit(
    val key: String,
    val type: LimitType,
    val enabled: Boolean,
    val value: Int,
    val bonus: Int = 0,
) {
    val effective: Int get() = value + bonus
}

enum class Level { OK, WARN, OVER }

data class LimitStatus(val limit: Limit, val current: Int) {
    val ratio: Float get() = if (limit.effective <= 0) 0f else current.toFloat() / limit.effective
    val level: Level
        get() = when {
            ratio >= 1f -> Level.OVER
            ratio >= LimitEvaluator.WARN_RATIO -> Level.WARN
            else -> Level.OK
        }
}

enum class AlertLevel { WARN, OVER }

data class PendingAlert(val limitKey: String, val level: AlertLevel, val current: Int, val effective: Int)

data class BedtimeWindow(val enabled: Boolean, val fromMinute: Int, val toMinute: Int) {
    /** True when [minuteOfDay] falls inside the window. Windows may wrap past midnight (22:30 to 06:30). */
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled || fromMinute == toMinute) return false
        return if (fromMinute < toMinute) {
            minuteOfDay in fromMinute until toMinute
        } else {
            minuteOfDay >= fromMinute || minuteOfDay < toMinute
        }
    }
}

data class AppSettings(
    val notify: Boolean = true,
    val alarm: Boolean = true,
    val alarmSound: Boolean = true,
    val strict: Boolean = false,
    val pauseEnabled: Boolean = true,
    val pauseSeconds: Int = 10,
    val bedtime: BedtimeWindow = BedtimeWindow(enabled = true, fromMinute = 22 * 60 + 30, toMinute = 6 * 60 + 30),
    val blockedAppIds: Set<String> = setOf("facebook", "tiktok", "youtube"),
    val snoozeUsed: Int = 0,
    val snoozeDate: String = "",
    val focusEndsAt: Long = 0L,
    val focusPlannedMin: Int = 0,
) {
    fun snoozeLeft(today: String): Int =
        if (snoozeDate == today) (SnoozePolicy.DAILY_LIMIT - snoozeUsed).coerceAtLeast(0) else SnoozePolicy.DAILY_LIMIT

    fun focusActive(nowMillis: Long): Boolean = focusEndsAt > nowMillis
}

enum class BlockReason { FOCUS, BEDTIME, LIMIT }

sealed interface Decision {
    data object Allow : Decision
    data class Pause(val appId: String, val seconds: Int) : Decision
    data class Block(val appId: String, val reason: BlockReason) : Decision
}
