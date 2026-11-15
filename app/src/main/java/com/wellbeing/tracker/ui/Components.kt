package com.wellbeing.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellbeing.tracker.domain.Format
import com.wellbeing.tracker.domain.Labels
import com.wellbeing.tracker.domain.Level
import com.wellbeing.tracker.domain.LimitStatus
import com.wellbeing.tracker.domain.LimitType
import com.wellbeing.tracker.ui.theme.Ok
import com.wellbeing.tracker.ui.theme.Over
import com.wellbeing.tracker.ui.theme.Warn

fun levelColor(level: Level): Color = when (level) {
    Level.OK -> Ok
    Level.WARN -> Warn
    Level.OVER -> Over
}

fun valueText(type: LimitType, v: Int): String = if (type == LimitType.CONTENT_COUNT) "$v" else Format.minutes(v)

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun WCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun LevelBar(progress: Float, level: Level, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        color = levelColor(level),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
    )
}

/** One limit with its title, "current / limit" and a coloured bar. */
@Composable
fun LimitProgress(title: String, status: LimitStatus?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (status != null) {
                val t = status.limit.type
                val tail = if (status.limit.enabled) " / ${valueText(t, status.limit.effective)}" else ""
                Text(
                    valueText(t, status.current) + tail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.limit.enabled) levelColor(status.level) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (status != null && status.limit.enabled) LevelBar(status.ratio, status.level)
        if (status != null) {
            val l = status.limit
            val text = if (!l.enabled) "বন্ধ" else buildString {
                append(Labels.level(status.level))
                if (status.level == Level.OVER && l.type == LimitType.CONTENT_COUNT && status.current > l.effective) {
                    append(" · +${status.current - l.effective}")
                }
                if (l.bonus > 0) append(" · Snooze +${l.bonus}${if (l.type == LimitType.CONTENT_COUNT) "" else " মি"}")
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = if (l.enabled) levelColor(status.level) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
