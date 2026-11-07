package com.aiconsole.kittlauncher2.obd

/** What the ECUs said to a Mode 04 (clear codes) request. Pure, unit-tested. */
sealed interface ClearReply {
    /** At least one module answered `44`: it accepted the clear. */
    data object Accepted : ClearReply

    /** A module answered `7F 04 <code>`: it refused, for the reason [nrc] (see [ClearRules.explain]). */
    data class Refused(val nrc: Int) : ClearReply

    /** Nothing usable came back (`NO DATA`, an adapter error, or an unrelated reply). */
    data object Silent : ClearReply
}

object ClearRules {
    const val NRC_BUSY = 0x21
    const val NRC_CONDITIONS = 0x22
    const val NRC_PENDING = 0x78

    /** Reads the reply to `04`: any `44` wins over a refusal from another module. */
    fun classify(raw: String): ClearReply {
        val messages = ObdResponseParser.messages(raw)
        if (messages.any { it.isNotEmpty() && it[0] == 0x44.toByte() }) return ClearReply.Accepted
        val refusal = messages.firstOrNull { it.size >= 3 && it[0] == 0x7F.toByte() && it[1] == 0x04.toByte() }
        return if (refusal != null) ClearReply.Refused(refusal[2].toInt() and 0xFF) else ClearReply.Silent
    }

    /** True when asking again a moment later can succeed. */
    fun retryable(reply: ClearReply): Boolean = reply is ClearReply.Refused && (reply.nrc == NRC_BUSY || reply.nrc == NRC_PENDING)

    fun explain(nrc: Int): String = when (nrc) {
        NRC_CONDITIONS -> "The ECU refused: conditions not correct. Turn the engine OFF with the ignition ON, then try again."
        NRC_BUSY, NRC_PENDING -> "The ECU was busy and didn't finish the clear. Try again in a few seconds."
        0x11, 0x12 -> "This module doesn't support clearing codes this way."
        0x31 -> "The ECU refused the request."
        else -> "The ECU refused (code %02X).".format(nrc)
    }

    /** The line shown to the driver after a clear, from what the ECUs said and what was left when the codes were re-read. */
    fun summary(reply: ClearReply, report: DtcReport): String = when {
        reply is ClearReply.Refused -> explain(reply.nrc)
        reply is ClearReply.Silent && report.all.isNotEmpty() ->
            "No module confirmed the clear, and ${report.all.size} code(s) are still there. Check the adapter link and try again with the engine off."
        report.all.isEmpty() -> "Codes cleared. None remain."
        report.stored.isEmpty() && report.pending.isEmpty() ->
            "Cleared, but ${report.permanent.size} permanent code(s) remain. Those can't be cleared by a scan tool: " +
                "they go away by themselves once the car passes its own self-test after the repair."
        else ->
            "The ECU accepted the clear, but ${report.all.size} code(s) came straight back. " +
                "The fault is still active, so it needs fixing before the code will stay cleared."
    }
}
