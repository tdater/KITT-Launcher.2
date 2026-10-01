package com.aiconsole.knightriderlauncher.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** An installed speech recognition service. */
data class RecognizerOption(val component: ComponentName, val label: String, val systemDefault: Boolean)

/** Which recognizer to bind; [SystemDefault] is whatever the head unit has set. */
sealed interface RecognizerTarget {
    data class Service(val component: ComponentName) : RecognizerTarget
    data object OnDevice : RecognizerTarget
    data object SystemDefault : RecognizerTarget

    /** Nothing safe to use: Auto never falls back to the manufacturer's default recognizer. */
    data object Unavailable : RecognizerTarget
}

/**
 * Chooses the speech recognizer. Head units often make the manufacturer's own voice assistant
 * the system default, and binding to it can pop that assistant's screen (e.g. the car/radio
 * app) over the launcher. So Auto prefers Google's recognizer, then Android's on-device one,
 * and uses the system default only when neither exists.
 */
object SpeechRecognizers {

    /** Setting value meaning "choose automatically". */
    const val AUTO = ""

    /** Setting value for Android's built-in on-device recognizer (Android 12+). */
    const val ON_DEVICE = "on-device"

    private val PREFERRED_PACKAGES = listOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.tts",
        "com.google.android.as"
    )

    fun installed(context: Context): List<RecognizerOption> {
        val pm = context.packageManager
        val default = systemDefault(context)
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val services: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0)
        }
        return services.map { info ->
            val component = ComponentName(info.serviceInfo.packageName, info.serviceInfo.name)
            val label = info.loadLabel(pm).toString().ifBlank { info.serviceInfo.packageName }
            RecognizerOption(component, "$label (${info.serviceInfo.packageName})", component == default)
        }.sortedBy { preferredRank(it.component.packageName) }
    }

    fun systemDefault(context: Context): ComponentName? =
        Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            ?.takeIf { it.isNotBlank() }
            ?.let(ComponentName::unflattenFromString)

    fun onDeviceAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun resolve(context: Context, choice: String): RecognizerTarget {
        when (choice) {
            AUTO -> Unit
            ON_DEVICE -> return if (onDeviceAvailable(context)) RecognizerTarget.OnDevice else RecognizerTarget.Unavailable
            else -> {
                val chosen = ComponentName.unflattenFromString(choice)
                if (chosen != null && installed(context).any { it.component == chosen }) return RecognizerTarget.Service(chosen)
            }
        }
        installed(context).firstOrNull { preferredRank(it.component.packageName) < PREFERRED_PACKAGES.size }
            ?.let { return RecognizerTarget.Service(it.component) }
        if (onDeviceAvailable(context)) return RecognizerTarget.OnDevice
        // The head unit's default is often the manufacturer's assistant, which can switch to its own
        // app (and on one unit looped with the launcher). Only used when the driver picks it.
        return RecognizerTarget.Unavailable
    }

    /** Human-readable name of what [choice] resolves to, for the settings screen. */
    fun describe(context: Context, choice: String): String = when (val target = resolve(context, choice)) {
        is RecognizerTarget.Service ->
            installed(context).firstOrNull { it.component == target.component }?.label ?: target.component.packageName
        RecognizerTarget.OnDevice -> "Android on-device recognizer"
        RecognizerTarget.SystemDefault ->
            "System default: " + (installed(context).firstOrNull { it.systemDefault }?.label ?: "unknown")
        RecognizerTarget.Unavailable -> "None suitable (Google speech services or Android 12+ on-device recognition needed)"
    }

    fun create(context: Context, target: RecognizerTarget): SpeechRecognizer = when (target) {
        is RecognizerTarget.Service -> SpeechRecognizer.createSpeechRecognizer(context, target.component)
        RecognizerTarget.OnDevice -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        RecognizerTarget.SystemDefault, RecognizerTarget.Unavailable -> SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun preferredRank(packageName: String): Int =
        PREFERRED_PACKAGES.indexOf(packageName).let { if (it < 0) PREFERRED_PACKAGES.size else it }
}
