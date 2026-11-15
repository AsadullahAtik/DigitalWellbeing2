package com.wellbeing.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wellbeing.tracker.domain.ReportData
import com.wellbeing.tracker.domain.ReportRange
import com.wellbeing.tracker.ui.theme.Over

private val CHART_HEIGHT = 140.dp

@Composable
fun ReportsScreen(report: ReportData?, range: ReportRange, onRange: (ReportRange) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("রিপোর্ট", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ReportRange.DAILY to "দিন", ReportRange.WEEKLY to "সপ্তাহ", ReportRange.MONTHLY to "মাস").forEach { (r, label) ->
                    FilterChip(selected = range == r, onClick = { onRange(r) }, label = { Text(label) })
                }
            }
        }
        if (report == null) {
            item { Text("লোড হচ্ছে…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(report.tile1, Modifier.weight(1f))
                Tile(report.tile2, Modifier.weight(1f))
            }
        }
        item {
            WCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Screen time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(report.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Chart(report)
            }
        }
        item {
            WCard {
                Text("পরামর্শ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                report.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun Tile(pair: Pair<String, String>, modifier: Modifier) {
    WCard(modifier) {
        Text(pair.first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(pair.second, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Chart(r: ReportData) {
    val max = (maxOf(r.values.filterNotNull().maxOrNull() ?: 0, r.limit) * 1.12f).coerceAtLeast(1f)
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(CHART_HEIGHT + 40.dp)) {
        // Limit line, drawn at the limit's height above the label row.
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .align(Alignment.BottomStart)
                .offset(y = -(20.dp + CHART_HEIGHT * (r.limit / max)))
                .background(Over)
        )
        Text(
            "সীমা ${r.limit}",
            style = MaterialTheme.typography.labelSmall,
            color = Over,
            modifier = Modifier.align(Alignment.BottomEnd).offset(y = -(22.dp + CHART_HEIGHT * (r.limit / max))),
        )
        Row(Modifier.fillMaxWidth().height(CHART_HEIGHT + 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            r.values.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (v != null) {
                        Text("$v", style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier.fillMaxWidth().height((CHART_HEIGHT * (v / max)).coerceAtLeast(3.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (v > r.limit) Over else if (i == r.todayIndex) primary else outline)
                        )
                    }
                    Text(
                        r.labels[i],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.height(20.dp),
                    )
                }
            }
        }
    }
}
