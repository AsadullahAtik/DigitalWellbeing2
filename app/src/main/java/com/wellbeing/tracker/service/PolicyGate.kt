package com.wellbeing.tracker.service

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers apps the user already got through Pause for a while, so Pause does not
 * loop every time the app resumes. In memory only: a restart shows Pause again.
 */
object PolicyGate {
    const val GRACE_MS = 10 * 60_000L
    private val allowedUntil = ConcurrentHashMap<String, Long>()

    fun allow(appId: String, nowMs: Long = System.currentTimeMillis(), graceMs: Long = GRACE_MS) {
        allowedUntil[appId] = nowMs + graceMs
    }

    fun isAllowed(appId: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        (allowedUntil[appId] ?: 0L) > nowMs

    fun clear(appId: String) { allowedUntil.remove(appId) }
}
