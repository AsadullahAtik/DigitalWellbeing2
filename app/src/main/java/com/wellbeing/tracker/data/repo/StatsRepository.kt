package com.wellbeing.tracker.data.repo

import com.wellbeing.tracker.data.db.StatEntity
import com.wellbeing.tracker.data.db.WellbeingDao
import com.wellbeing.tracker.domain.DateUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class TodayStats(
    val pauseSaved: Int = 0,
    val focusMinutes: Int = 0,
    val reasons: Map<String, Int> = emptyMap(),
) {
    /** The reason chosen most often when opening an app after Pause, with its count. */
    val topReason: Pair<String, Int>? get() = reasons.maxByOrNull { it.value }?.let { it.key to it.value }
}

@Singleton
class StatsRepository @Inject constructor(private val dao: WellbeingDao) {
    private val mutex = Mutex()

    private suspend fun add(key: String, by: Int) = mutex.withLock {
        val date = DateUtil.today()
        val now = dao.getStat(date, key)?.count ?: 0
        dao.upsertStat(StatEntity(date, key, now + by))
    }

    suspend fun addPauseSaved() = add(PAUSE_SAVED, 1)
    suspend fun addFocusMinutes(minutes: Int) = add(FOCUS_MIN, minutes)
    suspend fun addReason(reasonKey: String) = add(REASON + reasonKey, 1)

    suspend fun today(): TodayStats {
        val rows = dao.statsFor(DateUtil.today())
        return TodayStats(
            pauseSaved = rows.firstOrNull { it.key == PAUSE_SAVED }?.count ?: 0,
            focusMinutes = rows.firstOrNull { it.key == FOCUS_MIN }?.count ?: 0,
            reasons = rows.filter { it.key.startsWith(REASON) }.associate { it.key.removePrefix(REASON) to it.count },
        )
    }

    private companion object {
        const val PAUSE_SAVED = "pause_saved"
        const val FOCUS_MIN = "focus_min"
        const val REASON = "reason:"
    }
}
