package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.data.LauncherApp
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed

/** An app icon, optionally re-coloured to the theme accent (monochrome, keeping shading). */
@Composable
fun AppIcon(app: LauncherApp, modifier: Modifier = Modifier) {
    val settings = (LocalContext.current.applicationContext as KittApplication).settings
    val themed by settings.themedIcons.state.collectAsState()
    val accent = ScannerRed
    val filter = remember(themed, accent) { if (themed) themedFilter(accent) else null }
    Image(bitmap = app.icon, contentDescription = null, modifier = modifier, colorFilter = filter)
}

/** Luminance of each pixel mapped onto [accent], brightened a little so dark icons stay visible. */
private fun themedFilter(accent: Color): ColorFilter {
    val lum = floatArrayOf(0.299f, 0.587f, 0.114f)
    fun row(channel: Float) = floatArrayOf(lum[0] * channel * 1.3f, lum[1] * channel * 1.3f, lum[2] * channel * 1.3f, 0f, 20f * channel)
    return ColorFilter.colorMatrix(
        ColorMatrix(row(accent.red) + row(accent.green) + row(accent.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
    )
}
