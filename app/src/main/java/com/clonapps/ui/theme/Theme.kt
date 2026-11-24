package com.clonapps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.clonapps.core.store.ThemeMode

/** Warna diambil dari logo ClonApps: latar navy, kotak biru-cyan, kotak ungu. */
object Brand {
    val Navy = Color(0xFF030A2E)
    val NavyLight = Color(0xFF0B1B5C)
    val Blue = Color(0xFF1F6BFF)
    val Cyan = Color(0xFF1FE0FF)
    val Purple = Color(0xFF7C3AED)
    val Violet = Color(0xFFB08CFF)
}

private val Light = lightColorScheme(
    primary = Color(0xFF1A56DB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A55),
    secondary = Color(0xFF00788F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F4FF),
    onSecondaryContainer = Color(0xFF002A33),
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEBDDFF),
    onTertiaryContainer = Color(0xFF250059),
    background = Color(0xFFF5F7FF),
    onBackground = Color(0xFF0B1440),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1440),
    surfaceVariant = Color(0xFFE3E8FA),
    onSurfaceVariant = Color(0xFF3F4770),
    outline = Color(0xFF6B739C),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB1FF),
    onPrimary = Color(0xFF00225F),
    primaryContainer = Color(0xFF1F4FC4),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF5BE6FF),
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF005061),
    onSecondaryContainer = Color(0xFFC9F4FF),
    tertiary = Color(0xFFC6A8FF),
    onTertiary = Color(0xFF3B0D85),
    tertiaryContainer = Color(0xFF5521B5),
    onTertiaryContainer = Color(0xFFEBDDFF),
    background = Brand.Navy,
    onBackground = Color(0xFFE6EAFF),
    surface = Color(0xFF071238),
    onSurface = Color(0xFFE6EAFF),
    surfaceVariant = Color(0xFF172250),
    onSurfaceVariant = Color(0xFFC0C8EC),
    outline = Color(0xFF8A93BE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun ClonAppsTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
