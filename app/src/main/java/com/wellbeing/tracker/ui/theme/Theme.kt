package com.wellbeing.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ok = Color(0xFF34D399)
val Warn = Color(0xFFF59E0B)
val Over = Color(0xFFEF4444)

private val Dark = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF052E22),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF111A2B),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2438),
    onSurfaceVariant = Color(0xFF9AA8BF),
    outline = Color(0xFF2A3650),
    error = Over,
)

private val Light = lightColorScheme(
    primary = Color(0xFF0F9D6E),
    onPrimary = Color.White,
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EEF6),
    onSurfaceVariant = Color(0xFF52607A),
    outline = Color(0xFFCBD5E4),
    error = Over,
)

@Composable
fun WellbeingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
