package com.wellbeing.tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wellbeing.tracker.data.repo.ContentCountRepository
import com.wellbeing.tracker.data.repo.LimitRepository
import com.wellbeing.tracker.data.repo.SettingsRepository
import com.wellbeing.tracker.data.repo.UsageRepository
import com.wellbeing.tracker.domain.AppSettings
import com.wellbeing.tracker.domain.BedtimeWindow
import com.wellbeing.tracker.domain.ContentKind
import com.wellbeing.tracker.domain.DateUtil
import com.wellbeing.tracker.domain.DayParts
import com.wellbeing.tracker.domain.Limit
import com.wellbeing.tracker.domain.LimitKeys
import com.wellbeing.tracker.domain.ReportBuilder
import com.wellbeing.tracker.domain.ReportData
import com.wellbeing.tracker.domain.ReportRange
import com.wellbeing.tracker.domain.StrictRules
import com.wellbeing.tracker.engine.EngineState
import com.wellbeing.tracker.engine.WellbeingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val engine: WellbeingEngine,
    private val limits: LimitRepository,
    private val counts: ContentCountRepository,
    private val settingsRepo: SettingsRepository,
    private val usage: UsageRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _state = MutableStateFlow<EngineState?>(null)
    val state: StateFlow<EngineState?> = _state

    private val _range = MutableStateFlow(ReportRange.DAILY)
    val range: StateFlow<ReportRange> = _range

    private val _report = MutableStateFlow<ReportData?>(null)
    val report: StateFlow<ReportData?> = _report

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** Finished days never change, so their minutes are computed once. */
    private val pastDayMinutes = HashMap<LocalDate, Int>()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            var tick = 0
            while (isActive) {
                refresh(withReport = tick % 12 == 0)
                tick++
                delay(5_000)
            }
        }
    }

    fun refresh(withReport: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                engine.finalizeFocus()
                val st = engine.loadState()
                _state.value = st
                if (withReport) _report.value = buildReport(_range.value, st)
            } catch (_: Exception) {
            }
        }
    }

    fun setRange(r: ReportRange) {
        _range.value = r
        refresh(withReport = true)
    }

    private fun screenMinutesOf(from: Long, to: Long): Int {
        val snap = usage.snapshot(from, to)
        return if (snap.screenIntervals.isEmpty()) snap.appMinutes.values.sum() else snap.screenMinutes
    }

    /** Oldest first, ending with today: (ISO weekday, minutes). */
    private fun lastDays(n: Int, todayMinutes: Int): List<Pair<Int, Int>> {
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val minutes = if (back == 0) todayMinutes else pastDayMinutes.getOrPut(date) {
                screenMinutesOf(DateUtil.startOfDayMillis(date), DateUtil.startOfDayMillis(date.plusDays(1)))
            }
            date.dayOfWeek.value to minutes
        }
    }

    private suspend fun videosSince(daysBack: Int): Int {
        val from = LocalDate.now().minusDays(daysBack.toLong()).toString()
        return counts.since(from).filter { ContentKind.fromKey(it.kind) != ContentKind.FB_POSTS }.sumOf { it.count }
    }

    private suspend fun buildReport(range: ReportRange, st: EngineState): ReportData {
        val limit = st.limits.firstOrNull { it.key == LimitKeys.SCREEN }?.effective ?: 120
        val todayMinutes = st.statuses[LimitKeys.SCREEN]?.current ?: st.usage.screenMinutes
        return when (range) {
            ReportRange.DAILY -> {
                val start = DateUtil.startOfDayMillis()
                val nowHour = java.time.LocalTime.now().hour
                val parts = DayParts.parts
                val minutes = parts.map { p ->
                    if (p.startHour > nowHour) null else DayParts.minutes(st.usage.screenIntervals, start, p)
                }
                val current = parts.indexOfLast { it.startHour <= nowHour }.coerceAtLeast(0)
                val videos = st.counts.filterKeys { it != ContentKind.FB_POSTS }.values.sum()
                ReportBuilder.daily(parts.map { it.label }, minutes, current, limit, todayMinutes, videos, st.stats.pauseSaved, st.stats.topReason)
            }
            ReportRange.WEEKLY -> ReportBuilder.weekly(lastDays(7, todayMinutes), limit, videosSince(6))
            ReportRange.MONTHLY -> {
                val days = lastDays(28, todayMinutes).map { it.second }
                val weekAverages = days.chunked(7).map { it.sum() / it.size }
                ReportBuilder.monthly(weekAverages, limit, videosSince(27))
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun setLimit(old: Limit, newValue: Int, newEnabled: Boolean) {
        val strict = settings.value.strict
        if (!StrictRules.canChange(strict, old.value, newValue, old.enabled, newEnabled)) {
            _message.value = "Strict Mode চালু আছে, তাই সীমা বাড়ানো বা বন্ধ করা যাবে না।"
            return
        }
        viewModelScope.launch {
            limits.save(old.copy(value = newValue, enabled = newEnabled))
            refresh()
        }
    }

    fun setNotify(v: Boolean) = launch { settingsRepo.setNotify(v) }
    fun setAlarm(v: Boolean) = launch { settingsRepo.setAlarm(v) }
    fun setAlarmSound(v: Boolean) = launch { settingsRepo.setAlarmSound(v) }
    fun setPauseEnabled(v: Boolean) = launch { settingsRepo.setPauseEnabled(v) }
    fun setPauseSeconds(v: Int) = launch { settingsRepo.setPauseSeconds(v.coerceIn(5, 30)) }
    fun setStrict(v: Boolean) = launch { settingsRepo.setStrict(v) }
    fun setBedtime(w: BedtimeWindow) = launch { settingsRepo.setBedtime(w) }
    fun setBlockedApps(ids: Set<String>) = launch { settingsRepo.setBlockedApps(ids) }

    fun startFocus(minutes: Int) = launch { settingsRepo.startFocus(minutes, System.currentTimeMillis()) }

    /** Ending early does not count toward today's focus minutes (same as the prototype). */
    fun endFocus() = launch { settingsRepo.endFocus() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
