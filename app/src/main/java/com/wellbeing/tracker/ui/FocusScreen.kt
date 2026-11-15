package com.wellbeing.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wellbeing.tracker.domain.AppSettings
import com.wellbeing.tracker.domain.BedtimeWindow
import com.wellbeing.tracker.domain.DateUtil
import com.wellbeing.tracker.domain.Format
import com.wellbeing.tracker.domain.TrackedApps
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(
    settings: AppSettings,
    onStart: (Int) -> Unit,
    onEnd: () -> Unit,
    onBlocked: (Set<String>) -> Unit,
    onBedtime: (BedtimeWindow) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    var minutes by remember { mutableIntStateOf(25) }
    var askEnd by remember { mutableStateOf(false) }
    val active = settings.focusActive(now)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("ফোকাস ও ঘুম", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            WCard {
                if (active) {
                    val total = (settings.focusPlannedMin * 60).coerceAtLeast(1)
                    val left = ((settings.focusEndsAt - now) / 1000).toInt().coerceAtLeast(0)
                    FocusRing(1f - left.toFloat() / total, Format.mmss(left), "${settings.focusPlannedMin} মিনিটের সেশন")
                    Text("ব্লক করা App এখন বন্ধ। ফোন রেখে কাজে মন দাও।", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { askEnd = true }, modifier = Modifier.fillMaxWidth()) { Text("আগে শেষ করো") }
                } else {
                    FocusRing(0f, "$minutes:00", "মিনিট")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 45, 60).forEach { m ->
                            FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m মিনিট") })
                        }
                    }
                    Button(onClick = { onStart(minutes) }, modifier = Modifier.fillMaxWidth()) { Text("ফোকাস শুরু করো") }
                }
            }
        }
        item { SectionTitle("যেসব App বন্ধ থাকবে") }
        item {
            WCard {
                Text("ফোকাস সেশন ও ঘুমের সময়, দুই ক্ষেত্রেই এই তালিকা কাজ করবে।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackedApps.limited.forEach { app ->
                        val on = app.id in settings.blockedAppIds
                        FilterChip(
                            selected = on,
                            onClick = { onBlocked(if (on) settings.blockedAppIds - app.id else settings.blockedAppIds + app.id) },
                            label = { Text(app.label) },
                        )
                    }
                }
            }
        }
        item { SectionTitle("ঘুমের সময়") }
        item {
            val b = settings.bedtime
            val status = when {
                !b.enabled -> "বন্ধ আছে।"
                b.contains(DateUtil.minuteOfDayNow()) -> "এখন ঘুমের সময়। ব্লক করা App বন্ধ।"
                else -> "আজ রাত ${Format.clock(b.fromMinute)}-এ চালু হবে।"
            }
            WCard {
                SwitchRow("ঘুমের সময় চালু", "প্রতিদিন নির্দিষ্ট সময়ে ব্লক করা App বন্ধ", b.enabled) { onBedtime(b.copy(enabled = it)) }
                TimeStepper("শুরু", b.fromMinute) { onBedtime(b.copy(fromMinute = it)) }
                TimeStepper("শেষ", b.toMinute) { onBedtime(b.copy(toMinute = it)) }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (askEnd) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            title = { Text("ফোকাস আগে শেষ করবে?") },
            text = { Text("এই সেশন আজকের ফোকাসে যোগ হবে না।") },
            confirmButton = { TextButton(onClick = { askEnd = false; onEnd() }) { Text("শেষ করো") } },
            dismissButton = { TextButton(onClick = { askEnd = false }) { Text("চালিয়ে যাই") } },
        )
    }
}

@Composable
private fun FocusRing(progress: Float, big: String, small: String) {
    val ring = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                val inset = 6.dp.toPx()
                val s = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = s, style = stroke)
                drawArc(ring, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft = Offset(inset, inset), size = s, style = stroke)
            }
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(big, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(small, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TimeStepper(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = { onChange(((minuteOfDay - 15) % 1440 + 1440) % 1440) }) { Text("−") }
            Text(Format.clock(minuteOfDay), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
            FilledTonalIconButton(onClick = { onChange((minuteOfDay + 15) % 1440) }) { Text("+") }
        }
    }
}
