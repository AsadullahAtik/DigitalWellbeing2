package com.wellbeing.tracker.data.repo

import com.wellbeing.tracker.data.db.ContentCountEntity
import com.wellbeing.tracker.data.db.WellbeingDao
import com.wellbeing.tracker.domain.ContentKind
import com.wellbeing.tracker.domain.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentCountRepository @Inject constructor(private val dao: WellbeingDao) {
    private val mutex = Mutex()

    fun observeToday(): Flow<Map<ContentKind, Int>> = dao.observeCounts(DateUtil.today()).map { rows ->
        rows.mapNotNull { r -> ContentKind.fromKey(r.kind)?.let { it to r.count } }.toMap()
    }

    suspend fun today(): Map<ContentKind, Int> =
        dao.countsSince(DateUtil.today()).mapNotNull { r -> ContentKind.fromKey(r.kind)?.let { it to r.count } }.toMap()

    /** Returns the new count. The mutex keeps read-modify-write atomic when events arrive quickly. */
    suspend fun increment(kind: ContentKind): Int = mutex.withLock {
        val date = DateUtil.today()
        val next = (dao.getCount(date, kind.key)?.count ?: 0) + 1
        dao.upsertCount(ContentCountEntity(date, kind.key, next))
        next
    }

    suspend fun since(fromDate: String): List<ContentCountEntity> = dao.countsSince(fromDate)
}
