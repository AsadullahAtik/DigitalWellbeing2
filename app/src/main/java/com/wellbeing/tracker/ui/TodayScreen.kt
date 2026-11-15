package com.wellbeing.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wellbeing.tracker.domain.AppSettings
import com.wellbeing.tracker.domain.ContentKind
import com.wellbeing.tracker.domain.DateUtil
import com.wellbeing.tracker.domain.Format
import com.wellbeing.tracker.domain.Labels
import com.wellbeing.tracker.domain.Level
import com.wellbeing.tracker.domain.LimitKeys
import com.wellbeing.tracker.domain.TrackedApps
import com.wellbeing.tracker.engine.EngineState
import com.wellbeing.tracker.service.Permissions

@Composable
fun TodayScreen(state: EngineState?, settings: AppSettings) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("আজ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { SetupChecklist(showWhenDone = false) }
        if (state == null) {
            item { Text("লোড হচ্ছে…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@LazyColumn
        }
        item { ScreenTimeCard(state) }

        val now = System.currentTimeMillis()
        if (settings.focusActive(now)) {
            val left = ((settings.focusEndsAt - now) / 1000).toInt()
            item { Banner("ফোকাস চলছে · ${Format.mmss(left)} বাকি। ব্লক করা App বন্ধ।") }
        }
        if (settings.bedtime.contains(DateUtil.minuteOfDayNow())) {
            item { Banner("ঘুমের সময় চলছে (${Format.clock(settings.bedtime.fromMinute)} থেকে ${Format.clock(settings.bedtime.toMinute)})। ব্লক করা App বন্ধ।") }
        }
        if (settings.strict) item { Banner("Strict Mode চালু আছে।") }
        if (!Permissions.accessibility(context)) {
            item { Banner("Accessibility বন্ধ, তাই Reels ও Video গণনা থেমে আছে। App-এর সময়ের হিসাব ও সীমা ঠিকই কাজ করছে।") }
        }

        item { SectionTitle("অ্যাপ") }
        item {
            WCard {
                TrackedApps.all.sortedByDescending { state.usage.appMinutes[it.id] ?: 0 }.forEach { app ->
                    val status = state.statuses[LimitKeys.app(app.id)]
                    if (status != null) LimitProgress(app.label, status)
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(Format.minutes(state.usage.appMinutes[app.id] ?: 0), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SectionTitle("Reels ও ভিডিও (আনুমানিক)") }
        items(TrackedApps.limited.flatMap { it.contentKinds }) { kind ->
            WCard { LimitProgress(kind.label, state.statuses[LimitKeys.content(kind)]) }
        }
        item {
            Text(
                "কনটেন্টের গণনা Accessibility থেকে আসে, তাই কিছু বাদ পড়তে পারে। সময়ের হিসাব সবচেয়ে নির্ভরযোগ্য।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Banner(text: String) {
    WCard { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScreenTimeCard(state: EngineState) {
    val screen = state.statuses[LimitKeys.SCREEN]
    val minutes = screen?.current ?: state.usage.screenMinutes
    val level = screen?.level ?: Level.OK
    val ratio = screen?.ratio ?: 0f
    val ring = levelColor(level)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val videos = state.counts.filterKeys { it != ContentKind.FB_POSTS }.values.sum()
    WCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    val inset = 7.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
                    drawArc(ring, -90f, 360f * ratio.coerceIn(0f, 1f), false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
                }
                Text(Format.minutes(minutes), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("আজকের Screen time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (screen != null && screen.limit.enabled) {
                    Text("সীমা ${Format.minutes(screen.limit.effective)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Labels.level(level), color = ring, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell("${state.usage.unlocks}", "Unlock", Modifier.weight(1f))
            StatCell("$videos", "ভিডিও", Modifier.weight(1f))
            StatCell("${state.stats.pauseSaved}", "Pause-এ ফেরা", Modifier.weight(1f))
            StatCell("${state.stats.focusMinutes}", "ফোকাস মি", Modifier.weight(1f))
        }
    }
}
