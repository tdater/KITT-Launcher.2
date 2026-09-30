package com.aiconsole.knightriderlauncher.data

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import kotlinx.coroutines.delay
import org.lsposed.hiddenapibypass.HiddenApiBypass
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

    private const val TAG = "MultiWindowLauncher"
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

    /** Outcome of opening one window; [problem] is null when it opened as requested. */
    data class WindowLaunch(val app: LauncherApp, val problem: String?)

    /**
     * Opens [apps] in the slots of [layout], one after another, tiled inside [area] (screen pixels).
     * With freeform support each app gets exact window bounds; otherwise the second app is
     * opened adjacent (split-screen) and [area] is not honoured.
     *
     * Never throws for a refused launch: head-unit ROMs reject some options with a
     * SecurityException, and that must not take the launcher down with it.
     */
    suspend fun launch(context: Context, layout: MultiViewLayout, apps: List<LauncherApp>, area: Rect): List<WindowLaunch> {
        val freeform = supportsFreeform(context)
        val gap = (WINDOW_GAP_DP * context.resources.displayMetrics.density).roundToInt()

        return apps.take(layout.slots.size).mapIndexed { index, app ->
            val intent = InstalledApps.launchIntent(app)
            val problem = if (freeform) {
                launchFreeform(context, intent, layout.slots[index].toRect(area, gap))
            } else {
                if (index > 0) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                start(context, intent, null)
            }
            problem?.let { Log.w(TAG, "${app.label}: $it") }
            // Give the window manager time to place each window before the next one opens.
            delay(LAUNCH_STAGGER_MS)
            WindowLaunch(app, problem)
        }
    }

    /** Freeform launch at [bounds]; if the ROM refuses the windowing mode, retries with bounds alone. */
    private fun launchFreeform(context: Context, intent: Intent, bounds: Rect): String? {
        val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
        val modeProblem = requestFreeformMode(options)
        val problem = start(context, intent, options) ?: return modeProblem
        if (modeProblem != null) return "$modeProblem; $problem"
        // Some ROMs reject the windowing mode only at launch time.
        val retry = start(context, Intent(intent), ActivityOptions.makeBasic().setLaunchBounds(bounds))
            ?: return "freeform mode refused ($problem), opened with bounds only"
        return "$problem; bounds only: $retry"
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
     * On AOSP 10+ launch bounds alone are ignored on a fullscreen display: the launch must also
     * ask for freeform windowing mode. That setter is a hidden API that Android 9+ blocks for
     * plain reflection, so it goes through HiddenApiBypass. (Samsung honours bare bounds.)
     */
    private fun requestFreeformMode(options: ActivityOptions): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            HiddenApiBypass.invoke(
                ActivityOptions::class.java, options, "setLaunchWindowingMode", WINDOWING_MODE_FREEFORM
            )
            null
        } catch (e: Exception) {
            Log.w(TAG, "setLaunchWindowingMode(FREEFORM) unavailable", e)
            "freeform mode unavailable (${e.describe()})"
        } catch (e: LinkageError) {
            // HiddenApiBypass failing to initialise on an unusual ROM surfaces as an Error.
            Log.w(TAG, "HiddenApiBypass unavailable", e)
            "freeform mode unavailable (${e.describe()})"
        }
    }

    /** Starts [intent]; returns why it failed, or null on success. */
    private fun start(context: Context, intent: Intent, options: ActivityOptions?): String? = try {
        context.startActivity(intent, options?.toBundle())
        null
    } catch (_: ActivityNotFoundException) {
        "app is no longer installed"
    } catch (e: RuntimeException) {
        // SecurityException / IllegalArgumentException from the system refusing the options.
        Log.w(TAG, "startActivity refused", e)
        e.describe()
    }

    private fun Throwable.describe(): String = "${javaClass.simpleName}: ${message ?: "no details"}"
}
