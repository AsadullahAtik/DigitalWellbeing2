package com.wellbeing.tracker.data.repo

import com.wellbeing.tracker.data.db.FiredAlertEntity
import com.wellbeing.tracker.data.db.LimitEntity
import com.wellbeing.tracker.data.db.WellbeingDao
import com.wellbeing.tracker.domain.AlertLevel
import com.wellbeing.tracker.domain.DateUtil
import com.wellbeing.tracker.domain.DefaultLimits
import com.wellbeing.tracker.domain.Limit
import com.wellbeing.tracker.domain.LimitType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LimitRepository @Inject constructor(private val dao: WellbeingDao) {

    private fun LimitEntity.toDomain(today: String) = Limit(
        key = key,
        type = LimitType.valueOf(type),
        enabled = enabled,
        value = value,
        bonus = if (bonusDate == today) bonus else 0,
    )

    fun observe(): Flow<List<Limit>> = dao.observeLimits().map { list ->
        val today = DateUtil.today()
        list.map { it.toDomain(today) }
    }

    suspend fun all(): List<Limit> {
        seedIfEmpty()
        val today = DateUtil.today()
        return dao.getLimits().map { it.toDomain(today) }
    }

    suspend fun seedIfEmpty() {
        if (dao.getLimits().isNotEmpty()) return
        DefaultLimits.all().forEach {
            dao.upsertLimit(LimitEntity(it.key, it.type.name, it.enabled, it.value, 0, ""))
        }
    }

    suspend fun save(limit: Limit) {
        val existing = dao.getLimits().firstOrNull { it.key == limit.key }
        dao.upsertLimit(
            LimitEntity(
                key = limit.key,
                type = limit.type.name,
                enabled = limit.enabled,
                value = limit.value,
                bonus = existing?.bonus ?: 0,
                bonusDate = existing?.bonusDate ?: "",
            )
        )
    }

    /** Adds [amount] to today's snooze bonus and lets the OVER alert fire again later. */
    suspend fun addSnoozeBonus(key: String, amount: Int) {
        val today = DateUtil.today()
        val e = dao.getLimits().firstOrNull { it.key == key } ?: return
        val base = if (e.bonusDate == today) e.bonus else 0
        dao.upsertLimit(e.copy(bonus = base + amount, bonusDate = today))
        dao.clearFired(today, key, AlertLevel.OVER.name)
    }

    suspend fun firedToday(): Set<Pair<String, AlertLevel>> {
        val today = DateUtil.today()
        dao.pruneFired(today)
        return dao.firedAlerts(today).map { it.limitKey to AlertLevel.valueOf(it.level) }.toSet()
    }

    suspend fun markFired(key: String, level: AlertLevel) {
        dao.upsertFired(FiredAlertEntity(DateUtil.today(), key, level.name))
    }
}
