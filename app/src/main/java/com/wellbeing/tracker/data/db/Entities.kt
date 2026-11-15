package com.wellbeing.tracker.data.db

import androidx.room.Entity
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "content_counts", primaryKeys = ["date", "kind"])
data class ContentCountEntity(val date: String, val kind: String, val count: Int)

@Entity(tableName = "limits")
data class LimitEntity(
    @androidx.room.PrimaryKey val key: String,
    val type: String,
    val enabled: Boolean,
    val value: Int,
    val bonus: Int,
    /** Day the snooze bonus was granted; the bonus is ignored on any other day. */
    val bonusDate: String,
)

@Entity(tableName = "fired_alerts", primaryKeys = ["date", "limitKey", "level"])
data class FiredAlertEntity(val date: String, val limitKey: String, val level: String)

/** Small per-day counters: pause_saved, focus_min, reason:<key>. */
@Entity(tableName = "daily_stats", primaryKeys = ["date", "key"])
data class StatEntity(val date: String, val key: String, val count: Int)

@Dao
interface WellbeingDao {
    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun statsFor(date: String): List<StatEntity>

    @Query("SELECT * FROM daily_stats WHERE date = :date AND key = :key")
    suspend fun getStat(date: String, key: String): StatEntity?

    @Upsert
    suspend fun upsertStat(e: StatEntity)

    @Query("SELECT * FROM content_counts WHERE date = :date")
    fun observeCounts(date: String): Flow<List<ContentCountEntity>>

    @Query("SELECT * FROM content_counts WHERE date = :date AND kind = :kind")
    suspend fun getCount(date: String, kind: String): ContentCountEntity?

    @Query("SELECT * FROM content_counts WHERE date >= :fromDate ORDER BY date")
    suspend fun countsSince(fromDate: String): List<ContentCountEntity>

    @Upsert
    suspend fun upsertCount(e: ContentCountEntity)

    @Query("SELECT * FROM limits")
    fun observeLimits(): Flow<List<LimitEntity>>

    @Query("SELECT * FROM limits")
    suspend fun getLimits(): List<LimitEntity>

    @Upsert
    suspend fun upsertLimit(e: LimitEntity)

    @Query("SELECT * FROM fired_alerts WHERE date = :date")
    suspend fun firedAlerts(date: String): List<FiredAlertEntity>

    @Upsert
    suspend fun upsertFired(e: FiredAlertEntity)

    @Query("DELETE FROM fired_alerts WHERE date = :date AND limitKey = :key AND level = :level")
    suspend fun clearFired(date: String, key: String, level: String)

    @Query("DELETE FROM fired_alerts WHERE date < :date")
    suspend fun pruneFired(date: String)
}

@Database(
    entities = [ContentCountEntity::class, LimitEntity::class, FiredAlertEntity::class, StatEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WellbeingDatabase : RoomDatabase() {
    abstract fun dao(): WellbeingDao
}
