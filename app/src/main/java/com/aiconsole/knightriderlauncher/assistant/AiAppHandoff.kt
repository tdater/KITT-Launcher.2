package com.aiconsole.knightriderlauncher.assistant

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/**
 * Hands a question to an installed AI app so the driver's own subscription (Gemini, Claude,
 * Perplexity) does the answering. Those subscriptions can't be used from third-party apps
 * directly; the official apps are the supported way.
 */
object AiAppHandoff {

    data class AiApp(val label: String, val packageName: String)

    val knownApps = listOf(
        AiApp("Gemini", "com.google.android.apps.bard"),
        AiApp("Claude", "com.anthropic.claude"),
        AiApp("Perplexity", "ai.perplexity.app.android")
    )

    fun installed(context: Context): List<AiApp> =
        knownApps.filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }

    /** Shares [text] straight into the app; if it doesn't accept shares, opens it with the text on the clipboard. */
    fun send(context: Context, app: AiApp, text: String) {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(share)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to plain launch.
        }
        if (text.isNotBlank()) {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("KITT question", text))
        }
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            try {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // Uninstalled in the meantime.
            }
        }
    }
}
