package com.aiconsole.knightriderlauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

// Always dark, never dynamic: the dash look must not change with the wallpaper.
private fun colorScheme(p: KittPalette): ColorScheme = darkColorScheme(
    primary = p.accent,
    onPrimary = p.black,
    primaryContainer = p.accentDim,
    onPrimaryContainer = p.white,
    secondary = p.amber,
    onSecondary = p.black,
    secondaryContainer = p.amberDim,
    onSecondaryContainer = p.amber,
    tertiary = p.green,
    onTertiary = p.black,
    tertiaryContainer = p.greenDim,
    onTertiaryContainer = p.green,
    background = p.black,
    onBackground = p.white,
    surface = p.charcoal,
    onSurface = p.white,
    surfaceVariant = p.graphite,
    onSurfaceVariant = p.grey,
    surfaceDim = p.black,
    surfaceBright = p.bezel,
    surfaceContainerLowest = p.black,
    surfaceContainerLow = p.charcoal,
    surfaceContainer = p.charcoal,
    surfaceContainerHigh = p.graphite,
    surfaceContainerHighest = p.bezel,
    outline = p.bezel,
    outlineVariant = p.grid,
    error = p.danger,
    onError = p.black
)

// Tight, near-square corners, like 80s instrument bezels
private val KittShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

// Colorado OS console: softer rounded panels and buttons
private val ColoradoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun KnightRiderLauncherTheme(content: @Composable () -> Unit) {
    val theme = ThemeState.theme
    val scheme = remember(theme) { colorScheme(theme.palette()) }
    MaterialTheme(
        colorScheme = scheme,
        typography = if (theme == UiTheme.Colorado) ColoradoTypography else Typography,
        shapes = if (theme == UiTheme.Colorado) ColoradoShapes else KittShapes,
        content = content
    )
}
