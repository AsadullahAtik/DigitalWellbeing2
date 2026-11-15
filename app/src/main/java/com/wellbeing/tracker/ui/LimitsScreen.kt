package com.wellbeing.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wellbeing.tracker.domain.AlertTexts
import com.wellbeing.tracker.domain.AppSettings
import com.wellbeing.tracker.domain.Limit
import com.wellbeing.tracker.domain.LimitType
import com.wellbeing.tracker.engine.EngineState

private fun stepFor(type: LimitType) = if (type == LimitType.SCREEN_TIME) 15 else 5
private fun minFor(type: LimitType) = if (type == LimitType.SCREEN_TIME) 15 else 5

@Composable
fun LimitsScreen(state: EngineState?, settings: AppSettings, onChange: (Limit, Int, Boolean) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("সীমা", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Text(
                if (settings.strict) "Strict Mode চালু: সীমা শুধু কমানো যাবে।"
                else "৮০% এ নোটিফিকেশন, ১০০% এ অ্যালার্ম আসবে।",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            WCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Snooze", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "দিনে ২ বার, প্রতিবার +5 মিনিট বা +3টি। Strict Mode-এ নেই।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "বাকি ${settings.snoozeLeft(com.wellbeing.tracker.domain.DateUtil.today())}/2",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (state == null) return@LazyColumn
        listOf(LimitType.SCREEN_TIME, LimitType.APP_TIME, LimitType.CONTENT_COUNT).forEach { type ->
            val group = state.limits.filter { it.type == type }
            if (group.isNotEmpty()) {
                item {
                    SectionTitle(
                        when (type) {
                            LimitType.SCREEN_TIME -> "সারাদিনের স্ক্রিন টাইম"
                            LimitType.APP_TIME -> "অ্যাপের সময় (নির্ভরযোগ্য)"
                            LimitType.CONTENT_COUNT -> "কনটেন্ট সংখ্যা (আনুমানিক)"
                        }
                    )
                }
                group.forEach { limit ->
                    item(key = limit.key) {
                        WCard {
                            LimitProgress(AlertTexts.label(limit.key), state.statuses[limit.key])
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalIconButton(onClick = {
                                        onChange(limit, (limit.value - stepFor(type)).coerceAtLeast(minFor(type)), limit.enabled)
                                    }) { Text("−") }
                                    Text(valueText(type, limit.value), fontWeight = FontWeight.SemiBold)
                                    FilledTonalIconButton(onClick = { onChange(limit, limit.value + stepFor(type), limit.enabled) }) { Text("+") }
                                }
                                androidx.compose.material3.Switch(checked = limit.enabled, onCheckedChange = { onChange(limit, limit.value, it) })
                            }
                        }
                    }
                }
            }
        }
    }
}
