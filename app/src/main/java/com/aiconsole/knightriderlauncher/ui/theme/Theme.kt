package com.aiconsole.knightriderlauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Always dark, never dynamic: the dash look must not change with the wallpaper.
private val KittColorScheme = darkColorScheme(
    primary = ScannerRed,
    onPrimary = KittBlack,
    primaryContainer = ScannerRedDim,
    onPrimaryContainer = DashWhite,
    secondary = DashAmber,
    onSecondary = KittBlack,
    secondaryContainer = DashAmberDim,
    onSecondaryContainer = DashAmber,
    tertiary = LedGreen,
    onTertiary = KittBlack,
    tertiaryContainer = LedGreenDim,
    onTertiaryContainer = LedGreen,
    background = KittBlack,
    onBackground = DashWhite,
    surface = KittCharcoal,
    onSurface = DashWhite,
    surfaceVariant = KittGraphite,
    onSurfaceVariant = DashGrey,
    surfaceDim = KittBlack,
    surfaceBright = KittBezel,
    surfaceContainerLowest = KittBlack,
    surfaceContainerLow = KittCharcoal,
    surfaceContainer = KittCharcoal,
    surfaceContainerHigh = KittGraphite,
    surfaceContainerHighest = KittBezel,
    outline = KittBezel,
    outlineVariant = KittGrid,
    error = ScannerRed,
    onError = KittBlack
)

// Tight, near-square corners, like 80s instrument bezels
private val KittShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun KnightRiderLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KittColorScheme,
        typography = Typography,
        shapes = KittShapes,
        content = content
    )
}
