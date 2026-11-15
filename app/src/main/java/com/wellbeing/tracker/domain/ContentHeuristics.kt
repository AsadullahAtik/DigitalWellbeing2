package com.wellbeing.tracker.domain

/**
 * Best-effort content counting. These marker strings are guesses about each app's
 * view IDs and change with app updates, so they must be calibrated on a real device.
 * They are kept in one place on purpose.
 */
object ContentHeuristics {
    /** Minimum gap between two counted items; scroll events fire many times per swipe. */
    const val MIN_GAP_MS = 900L

    /** Pixels of feed scroll that count as one Facebook post. */
    const val POST_SCROLL_PX = 900

    private val reelMarkers = mapOf(
        "facebook" to listOf("reel", "video_pager", "short_form"),
        "youtube" to listOf("reel_player", "shorts", "reel_recycler"),
        "tiktok" to listOf("viewpager", "aweme", "feed_pager"),
    )

    /** True when [ids] (view id names seen on screen) suggest a full-screen vertical video surface. */
    fun onShortVideoSurface(appId: String, ids: Collection<String>): Boolean {
        val markers = reelMarkers[appId] ?: return false
        return ids.any { id -> markers.any { id.contains(it, ignoreCase = true) } }
    }

    fun kindForVideo(appId: String): ContentKind? = when (appId) {
        "facebook" -> ContentKind.FB_REELS
        "tiktok" -> ContentKind.TT_VIDEOS
        "youtube" -> ContentKind.YT_VIDEOS
        else -> null
    }
}

/** Debounces scroll events into item counts. Pure so it can be unit tested. */
class ItemCounter(private val minGapMs: Long = ContentHeuristics.MIN_GAP_MS) {
    private var lastAt = Long.MIN_VALUE / 2

    /** Returns true if this event should count as a new item. */
    fun onScroll(nowMs: Long): Boolean {
        if (nowMs - lastAt < minGapMs) return false
        lastAt = nowMs
        return true
    }

    fun reset() { lastAt = Long.MIN_VALUE / 2 }
}

/** Turns accumulated scroll distance into whole items, carrying the remainder. */
class ScrollAccumulator(private val pxPerItem: Int = ContentHeuristics.POST_SCROLL_PX) {
    private var acc = 0

    fun add(deltaPx: Int): Int {
        acc += kotlin.math.abs(deltaPx)
        val items = acc / pxPerItem
        acc %= pxPerItem
        return items
    }
}
