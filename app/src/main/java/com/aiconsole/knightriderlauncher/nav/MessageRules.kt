package com.aiconsole.knightriderlauncher.nav

/** How much of an incoming message KITT reads out. */
enum class MessageReadout(val label: String) {
    Off("Off"),
    SenderOnly("Who it's from"),
    Full("Whole message")
}

/** Plain-Kotlin rules for announcing chat/SMS notifications. */
object MessageRules {

    /** Messaging apps whose notifications count even if they don't set CATEGORY_MESSAGE. */
    val MESSAGING_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.whatsapp",
        "com.facebook.orca",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.discord",
        "com.google.android.apps.googlevoice",
        "com.textra",
        "com.verizon.messaging.vzmsgs"
    )

    private const val MAX_SPOKEN = 220

    /** What KITT says, or null when nothing should be read. */
    fun announcement(mode: MessageReadout, app: String, sender: String?, text: String?): String? {
        if (mode == MessageReadout.Off) return null
        val who = sender?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val body = text?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        return when (mode) {
            MessageReadout.SenderOnly -> "New $app message from $who."
            MessageReadout.Full -> if (body.isEmpty()) {
                "New $app message from $who."
            } else {
                val clipped = if (body.length > MAX_SPOKEN) body.take(MAX_SPOKEN).substringBeforeLast(' ') + "…" else body
                "$who says: $clipped"
            }
            MessageReadout.Off -> null
        }
    }

    /** Apps repost the same notification when it updates; only announce new content. */
    fun dedupeKey(packageName: String, sender: String?, text: String?): String = "$packageName|$sender|$text"
}
