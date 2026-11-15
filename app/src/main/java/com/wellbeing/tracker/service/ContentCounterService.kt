package com.wellbeing.tracker.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wellbeing.tracker.domain.ContentHeuristics
import com.wellbeing.tracker.domain.ContentKind
import com.wellbeing.tracker.domain.ItemCounter
import com.wellbeing.tracker.domain.ScrollAccumulator
import com.wellbeing.tracker.domain.TrackedApps
import com.wellbeing.tracker.engine.WellbeingEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Best-effort counter for Reels, Shorts, TikTok videos and Facebook posts.
 * It only reads view ID names, never text the user typed or message content,
 * and stores nothing except the counts.
 */
@AndroidEntryPoint
class ContentCounterService : AccessibilityService() {
    @Inject lateinit var engine: WellbeingEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val videoCounters = HashMap<String, ItemCounter>()
    private val feedScroll = ScrollAccumulator()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val app = TrackedApps.byPackage(event.packageName?.toString() ?: return) ?: return

        val ids = collectViewIds(rootInActiveWindow)
        if (ContentHeuristics.onShortVideoSurface(app.id, ids)) {
            val kind = ContentHeuristics.kindForVideo(app.id) ?: return
            val counter = videoCounters.getOrPut(app.id) { ItemCounter() }
            if (counter.onScroll(System.currentTimeMillis())) bump(kind, 1)
        } else if (app.id == "facebook") {
            val delta = if (android.os.Build.VERSION.SDK_INT >= 28) event.scrollDeltaY else 0
            val posts = feedScroll.add(delta)
            if (posts > 0) bump(ContentKind.FB_POSTS, posts)
        }
    }

    private fun bump(kind: ContentKind, by: Int) {
        scope.launch { engine.onContentItem(kind, by) }
    }

    /** View ID names only, capped so a huge screen cannot cost much. */
    private fun collectViewIds(root: AccessibilityNodeInfo?): List<String> {
        root ?: return emptyList()
        val out = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            n.viewIdResourceName?.let { out += it }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return out
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val MAX_NODES = 150
    }
}
