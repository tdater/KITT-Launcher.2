package com.aiconsole.knightriderlauncher.data

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.WindowInsets
import android.view.WindowManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** A slot's position as fractions (0..1) of the usable screen area. */
data class SlotFraction(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

enum class MultiViewLayout(val label: String, val slots: List<SlotFraction>) {
    Stacked(
        "2 · Stacked",
        listOf(SlotFraction(0f, 0f, 1f, 0.5f), SlotFraction(0f, 0.5f, 1f, 1f))
    ),
    SideBySide(
        "2 · Side by side",
        listOf(SlotFraction(0f, 0f, 0.5f, 1f), SlotFraction(0.5f, 0f, 1f, 1f))
    ),
    OnePlusTwo(
        "1 + 2",
        listOf(
            SlotFraction(0f, 0f, 1f, 0.5f),
            SlotFraction(0f, 0.5f, 0.5f, 1f),
            SlotFraction(0.5f, 0.5f, 1f, 1f)
        )
    ),
    Quad(
        "2 × 2 Quad",
        listOf(
            SlotFraction(0f, 0f, 0.5f, 0.5f),
            SlotFraction(0.5f, 0f, 1f, 0.5f),
            SlotFraction(0f, 0.5f, 0.5f, 1f),
            SlotFraction(0.5f, 0.5f, 1f, 1f)
        )
    );

    /** Layouts with more than two windows need freeform support. */
    val needsFreeform: Boolean get() = slots.size > 2
}

object MultiWindowLauncher {

    // Hidden WindowConfiguration.WINDOWING_MODE_FREEFORM
    private const val WINDOWING_MODE_FREEFORM = 5
    private const val LAUNCH_STAGGER_MS = 350L
    private const val WINDOW_GAP_DP = 4

    fun supportsFreeform(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            // Developer option "Enable freeform windows" sets this without declaring the feature.
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) != 0

    /** Usable screen area in pixels, excluding status and navigation bars. Needs an Activity context. */
    fun screenBounds(context: Context): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            return Rect(metrics.bounds).apply {
                left += insets.left
                top += insets.top
                right -= insets.right
                bottom -= insets.bottom
            }
        }
        val dm = context.resources.displayMetrics
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    /**
     * Opens [apps] in the slots of [layout], one after another, tiled inside [area] (pixels).
     * With freeform support each app gets exact window bounds; otherwise the second app is
     * opened adjacent (split-screen) and [area] is not honoured.
     */
    suspend fun launch(context: Context, layout: MultiViewLayout, apps: List<LauncherApp>, area: Rect) {
        val freeform = supportsFreeform(context)
        val gap = (WINDOW_GAP_DP * context.resources.displayMetrics.density).roundToInt()

        apps.take(layout.slots.size).forEachIndexed { index, app ->
            val intent = InstalledApps.launchIntent(app)
            if (freeform) {
                val options = ActivityOptions.makeBasic()
                    .setLaunchBounds(layout.slots[index].toRect(area, gap))
                requestFreeformMode(options)
                start(context, intent, options)
            } else {
                if (index > 0) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                start(context, intent, null)
            }
            // Give the window manager time to place each window before the next one opens.
            delay(LAUNCH_STAGGER_MS)
        }
    }

    private fun SlotFraction.toRect(screen: Rect, gap: Int): Rect {
        val half = gap / 2
        return Rect(
            screen.left + (screen.width() * left).roundToInt() + half,
            screen.top + (screen.height() * top).roundToInt() + half,
            screen.left + (screen.width() * right).roundToInt() - half,
            screen.top + (screen.height() * bottom).roundToInt() - half
        )
    }

    /**
     * Launch bounds alone are ignored on some Android 9+ builds unless the launch also asks
     * for freeform windowing mode, which is only reachable by reflection. Best effort only.
     */
    private fun requestFreeformMode(options: ActivityOptions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, WINDOWING_MODE_FREEFORM)
        } catch (_: Exception) {
            // Not available on this build; the bounds still apply where freeform is the default.
        }
    }

    private fun start(context: Context, intent: Intent, options: ActivityOptions?) {
        try {
            context.startActivity(intent, options?.toBundle())
        } catch (_: ActivityNotFoundException) {
            // App was uninstalled since the list was loaded.
        }
    }
}
