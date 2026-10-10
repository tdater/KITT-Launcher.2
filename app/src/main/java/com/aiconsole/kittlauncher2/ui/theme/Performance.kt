package com.aiconsole.kittlauncher2.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True in lite mode (see [com.aiconsole.kittlauncher2.data.DeviceProfile]): animations run at a
 * lower frame rate or stand still, so slow head units keep their CPU for maps, music and OBD.
 */
val LocalLiteMode = staticCompositionLocalOf { false }
