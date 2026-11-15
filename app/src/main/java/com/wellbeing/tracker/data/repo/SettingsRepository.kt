package com.wellbeing.tracker.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wellbeing.tracker.domain.AppSettings
import com.wellbeing.tracker.domain.BedtimeWindow
import com.wellbeing.tracker.domain.DateUtil
import com.wellbeing.tracker.domain.SnoozePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val notify = booleanPreferencesKey("notify")
        val alarm = booleanPreferencesKey("alarm")
        val alarmSound = booleanPreferencesKey("alarm_sound")
        val strict = booleanPreferencesKey("strict")
        val pauseEnabled = booleanPreferencesKey("pause_enabled")
        val pauseSeconds = intPreferencesKey("pause_seconds")
        val bedEnabled = booleanPreferencesKey("bed_enabled")
        val bedFrom = intPreferencesKey("bed_from")
        val bedTo = intPreferencesKey("bed_to")
        val blocked = stringSetPreferencesKey("blocked_apps")
        val snoozeUsed = intPreferencesKey("snooze_used")
        val snoozeDate = stringPreferencesKey("snooze_date")
        val focusEnds = longPreferencesKey("focus_ends")
        val focusPlanned = intPreferencesKey("focus_planned")
    }

    private val defaults = AppSettings()

    val settings: Flow<AppSettings> = context.store.data.map { p ->
        AppSettings(
            notify = p[K.notify] ?: defaults.notify,
            alarm = p[K.alarm] ?: defaults.alarm,
            alarmSound = p[K.alarmSound] ?: defaults.alarmSound,
            strict = p[K.strict] ?: defaults.strict,
            pauseEnabled = p[K.pauseEnabled] ?: defaults.pauseEnabled,
            pauseSeconds = p[K.pauseSeconds] ?: defaults.pauseSeconds,
            bedtime = BedtimeWindow(
                enabled = p[K.bedEnabled] ?: defaults.bedtime.enabled,
                fromMinute = p[K.bedFrom] ?: defaults.bedtime.fromMinute,
                toMinute = p[K.bedTo] ?: defaults.bedtime.toMinute,
            ),
            blockedAppIds = p[K.blocked] ?: defaults.blockedAppIds,
            snoozeUsed = p[K.snoozeUsed] ?: 0,
            snoozeDate = p[K.snoozeDate] ?: "",
            focusEndsAt = p[K.focusEnds] ?: 0L,
            focusPlannedMin = p[K.focusPlanned] ?: 0,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.store.edit { block(it) }
    }

    suspend fun setNotify(v: Boolean) = edit { it[K.notify] = v }
    suspend fun setAlarm(v: Boolean) = edit { it[K.alarm] = v }
    suspend fun setAlarmSound(v: Boolean) = edit { it[K.alarmSound] = v }
    suspend fun setStrict(v: Boolean) = edit { it[K.strict] = v }
    suspend fun setPauseEnabled(v: Boolean) = edit { it[K.pauseEnabled] = v }
    suspend fun setPauseSeconds(v: Int) = edit { it[K.pauseSeconds] = v }

    suspend fun setBedtime(w: BedtimeWindow) = edit {
        it[K.bedEnabled] = w.enabled
        it[K.bedFrom] = w.fromMinute
        it[K.bedTo] = w.toMinute
    }

    suspend fun setBlockedApps(ids: Set<String>) = edit { it[K.blocked] = ids }

    suspend fun startFocus(minutes: Int, nowMillis: Long) = edit {
        it[K.focusEnds] = nowMillis + minutes * 60_000L
        it[K.focusPlanned] = minutes
    }

    suspend fun endFocus() = edit { it[K.focusEnds] = 0L }

    /**
     * If a focus session ran to its end, clears it and returns its planned minutes (once); otherwise 0.
     * Ending early with [endFocus] clears the end time first, so it never counts.
     */
    suspend fun takeExpiredFocus(nowMillis: Long): Int {
        val s = current()
        if (s.focusEndsAt == 0L || s.focusEndsAt > nowMillis) return 0
        edit { it[K.focusEnds] = 0L }
        return s.focusPlannedMin
    }

    /** Returns true when a snooze was available and has now been consumed. */
    suspend fun consumeSnooze(): Boolean {
        val today = DateUtil.today()
        val s = current()
        val left = s.snoozeLeft(today)
        if (!SnoozePolicy.canSnooze(s.strict, left)) return false
        val used = if (s.snoozeDate == today) s.snoozeUsed else 0
        edit {
            it[K.snoozeDate] = today
            it[K.snoozeUsed] = used + 1
        }
        return true
    }
}
