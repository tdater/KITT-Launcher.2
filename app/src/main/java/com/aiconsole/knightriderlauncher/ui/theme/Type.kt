package com.aiconsole.knightriderlauncher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Monospace for instrument readouts, wide-tracked sans for panel labels
val ReadoutFont = FontFamily.Monospace
val LabelFont = FontFamily.SansSerif

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = ReadoutFont,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = 2.sp
    ),
    displayMedium = TextStyle(
        fontFamily = ReadoutFont,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = 2.sp
    ),
    displaySmall = TextStyle(
        fontFamily = ReadoutFont,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = 1.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = LabelFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 3.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ReadoutFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = LabelFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 2.sp
    ),
    titleSmall = TextStyle(
        fontFamily = LabelFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ReadoutFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ReadoutFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = LabelFont,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = LabelFont,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = LabelFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.5.sp
    )
)

/**
 * Colorado OS: everything in a Courier-style monospace with tighter tracking, like the
 * TruckAPK console. Sizes match the KITT scale so layouts don't shift when switching.
 */
val ColoradoTypography = Typography.run {
    fun TextStyle.console(tracking: Float) = copy(fontFamily = FontFamily.Monospace, letterSpacing = tracking.sp)
    copy(
        displayLarge = displayLarge.console(1f),
        displayMedium = displayMedium.console(1f),
        displaySmall = displaySmall.console(0.8f),
        headlineMedium = headlineMedium.console(1f),
        headlineSmall = headlineSmall.console(0.5f),
        titleMedium = titleMedium.console(0.8f),
        titleSmall = titleSmall.console(0.6f),
        bodyLarge = bodyLarge.console(0f),
        bodyMedium = bodyMedium.console(0f),
        labelLarge = labelLarge.console(0.6f),
        labelMedium = labelMedium.console(0.4f),
        labelSmall = labelSmall.console(0.3f)
    )
}
