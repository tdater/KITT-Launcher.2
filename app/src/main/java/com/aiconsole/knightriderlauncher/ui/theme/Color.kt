package com.aiconsole.knightriderlauncher.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** The launcher's selectable looks. */
enum class UiTheme(val label: String) {
    Kitt("KITT"),
    Karr("KARR"),
    NightVision("Night Vision"),
    Colorado("Colorado OS")
}

/**
 * Every color the UI draws with, by role. [accent] is the brand color (KITT's scanner red),
 * [danger] is reserved for warnings and destructive actions so it stays red in every theme.
 */
data class KittPalette(
    val black: Color,
    val charcoal: Color,
    val graphite: Color,
    val bezel: Color,
    val grid: Color,
    val accent: Color,
    val accentDim: Color,
    val danger: Color,
    val amber: Color,
    val amberDim: Color,
    val green: Color,
    val greenDim: Color,
    val yellow: Color,
    val info: Color,
    val white: Color,
    val grey: Color
)

// The black-glass KITT dashboard: scanner red, dash amber, LED green.
val KittClassicPalette = KittPalette(
    black = Color(0xFF050505),
    charcoal = Color(0xFF111213),
    graphite = Color(0xFF1A1B1D),
    bezel = Color(0xFF2B2C2F),
    grid = Color(0xFF222326),
    accent = Color(0xFFFF1E1E),
    accentDim = Color(0xFF4A0808),
    danger = Color(0xFFFF1E1E),
    amber = Color(0xFFFFB000),
    amberDim = Color(0xFF3D2A00),
    green = Color(0xFF3DFF3D),
    greenDim = Color(0xFF0C330C),
    yellow = Color(0xFFFFE600),
    info = Color(0xFF00BFFF),
    white = Color(0xFFE8E8E6),
    grey = Color(0xFF85878B)
)

// Colorado OS (the TruckAPK console): terminal green on green-tinted black, blue units, amber system text.
val ColoradoPalette = KittPalette(
    black = Color(0xFF040906),
    charcoal = Color(0xFF070C0A),
    graphite = Color(0xFF101614),
    bezel = Color(0xFF32694A),
    grid = Color(0xFF244434),
    accent = Color(0xFF00FF58),
    accentDim = Color(0xFF0B3A1D),
    danger = Color(0xFFFF3B3B),
    amber = Color(0xFFFFBB33),
    amberDim = Color(0xFF3D2C08),
    green = Color(0xFF00FF58),
    greenDim = Color(0xFF0B3A1D),
    yellow = Color(0xFFFFE600),
    info = Color(0xFF00BFFF),
    white = Color(0xFFE7EEE9),
    grey = Color(0xFFA8B6AD)
)

// KARR, KITT's prototype twin: the same black glass with a gold scanner and amber-orange dash.
val KarrPalette = KittPalette(
    black = Color(0xFF060504),
    charcoal = Color(0xFF12100C),
    graphite = Color(0xFF1C1912),
    bezel = Color(0xFF3A3221),
    grid = Color(0xFF28231A),
    accent = Color(0xFFFFC21A),
    accentDim = Color(0xFF453300),
    danger = Color(0xFFFF2A1A),
    amber = Color(0xFFFF8A1A),
    amberDim = Color(0xFF402104),
    green = Color(0xFFE8D27A),
    greenDim = Color(0xFF3A3318),
    yellow = Color(0xFFFFE066),
    info = Color(0xFF9FC4E8),
    white = Color(0xFFEDE6D6),
    grey = Color(0xFF8E8676)
)

// Night Vision: only dim reds and red-oranges on true black, so the screen doesn't spoil the
// driver's dark adaptation. Warnings stay a brighter red to stand out.
val NightVisionPalette = KittPalette(
    black = Color(0xFF000000),
    charcoal = Color(0xFF070202),
    graphite = Color(0xFF0D0303),
    bezel = Color(0xFF2E0B0B),
    grid = Color(0xFF1C0606),
    accent = Color(0xFFB81414),
    accentDim = Color(0xFF330505),
    danger = Color(0xFFFF2E2E),
    amber = Color(0xFFB03A1C),
    amberDim = Color(0xFF2C0D06),
    green = Color(0xFFC0521E),
    greenDim = Color(0xFF301306),
    yellow = Color(0xFFC46A22),
    info = Color(0xFF9E3030),
    white = Color(0xFFC24848),
    grey = Color(0xFF7A2E2E)
)

fun UiTheme.palette(): KittPalette = when (this) {
    UiTheme.Kitt -> KittClassicPalette
    UiTheme.Karr -> KarrPalette
    UiTheme.NightVision -> NightVisionPalette
    UiTheme.Colorado -> ColoradoPalette
}

/**
 * The active theme. Snapshot state, so every composable and draw lambda that reads one of
 * the colors below redraws by itself when the driver switches themes.
 */
object ThemeState {
    var theme by mutableStateOf(UiTheme.Kitt)
    val palette: KittPalette get() = theme.palette()
}

// Surfaces
val KittBlack: Color get() = ThemeState.palette.black
val KittCharcoal: Color get() = ThemeState.palette.charcoal
val KittGraphite: Color get() = ThemeState.palette.graphite
val KittBezel: Color get() = ThemeState.palette.bezel
val KittGrid: Color get() = ThemeState.palette.grid

// Accents. ScannerRed is the brand accent (green in Colorado OS); AlertRed always means danger.
val ScannerRed: Color get() = ThemeState.palette.accent
val ScannerRedDim: Color get() = ThemeState.palette.accentDim
val AlertRed: Color get() = ThemeState.palette.danger
val DashAmber: Color get() = ThemeState.palette.amber
val DashAmberDim: Color get() = ThemeState.palette.amberDim
val LedGreen: Color get() = ThemeState.palette.green
val LedGreenDim: Color get() = ThemeState.palette.greenDim
val DashYellow: Color get() = ThemeState.palette.yellow
val DashBlue: Color get() = ThemeState.palette.info

// Text
val DashWhite: Color get() = ThemeState.palette.white
val DashGrey: Color get() = ThemeState.palette.grey
