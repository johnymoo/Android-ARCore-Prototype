package com.johnymoo.arverify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ScanForgeColors = darkColorScheme(
    primary = SfTeal,
    onPrimary = SfTealOn,
    background = SfBg,
    onBackground = SfOnBg,
    surface = SfSurface,
    onSurface = SfOnBg,
    surfaceVariant = SfSurfaceVariant,
    onSurfaceVariant = SfMuted,
    error = SfError,
)

@Composable
fun ScanForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScanForgeColors,
        typography = ScanForgeTypography,
        shapes = ScanForgeShapes,
        content = content,
    )
}
