package com.wellbeing.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicsTest {
    @Test fun burstOfScrollEventsCountsOnce() {
        val c = ItemCounter(900)
        assertTrue(c.onScroll(1_000))
        assertFalse(c.onScroll(1_200))
        assertFalse(c.onScroll(1_850))
        assertTrue(c.onScroll(1_900))
    }

    @Test fun scrollAccumulatorCarriesRemainder() {
        val a = ScrollAccumulator(900)
        assertEquals(0, a.add(500))
        assertEquals(1, a.add(500))   // 1000 total -> 1 item, 100 left
        assertEquals(2, a.add(1700))  // 1800 total -> 2 items
        assertEquals(1, a.add(-900))  // direction ignored
    }

    @Test fun surfaceDetection() {
        assertTrue(ContentHeuristics.onShortVideoSurface("youtube", listOf("com.google.android.youtube:id/reel_player_page_container")))
        assertFalse(ContentHeuristics.onShortVideoSurface("youtube", listOf("com.google.android.youtube:id/watch_list")))
        assertFalse(ContentHeuristics.onShortVideoSurface("chrome", listOf("reel")))
        assertEquals(ContentKind.TT_VIDEOS, ContentHeuristics.kindForVideo("tiktok"))
    }
}
