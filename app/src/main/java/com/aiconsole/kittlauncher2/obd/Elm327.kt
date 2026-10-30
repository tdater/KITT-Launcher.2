package com.aiconsole.kittlauncher2.obd

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

open class ElmException(message: String) : IOException(message)

/** No '>' prompt in time. The link has been resynced, so the next command can still be sent. */
class ElmTimeoutException(command: String) : ElmException("Adapter timed out on $command")

data class ElmInfo(val version: String, val protocolNumber: Int, val protocolName: String) {
    /** ISO 15765-4 CAN (6-9) or user CAN (A-C): enables headers, multi-PID and Mode 0A. */
    val isCan: Boolean get() = protocolNumber in 6..12
}

/**
 * ELM327 command layer. Every exchange is "command + CR" and ends at the '>' prompt.
 * Callers group related commands (header switch + request) with [exclusive] so the
 * polling loop and on-demand requests (codes, PID tests) never interleave on the wire.
 */
class Elm327(private val transport: ObdTransport) {

    private val lock = Mutex()
    private val buffer = StringBuilder()
    private var currentHeader: String? = null
    private var currentReceiveHeader: String? = null

    /** Header used for normal Mode 01 polling; "7E0" (engine, physical) once CAN is up. */
    var defaultHeader: String? = null
        internal set

    inner class Link internal constructor() {
        suspend fun send(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String = sendRaw(command, timeoutMs)

        suspend fun useHeader(header: String, receiveHeader: String? = receiveHeaderFor(header)) {
            if (header != currentHeader) {
                sendRaw("ATSH$header", DEFAULT_TIMEOUT_MS)
                currentHeader = header
            }
            if (receiveHeader != currentReceiveHeader) {
                if (receiveHeader != null) {
                    sendRaw("ATCRA$receiveHeader", DEFAULT_TIMEOUT_MS)
                } else {
                    sendRaw("ATAR", DEFAULT_TIMEOUT_MS)
                }
                currentReceiveHeader = receiveHeader
            }
        }

        suspend fun useDefaultHeader() {
            defaultHeader?.let { useHeader(it, null) }
        }

        fun setDefaultHeader(header: String) {
            defaultHeader = header
        }

        /** The header in use, for logs ("auto" before any ATSH). */
        fun currentHeaderLabel(): String = currentHeader ?: "auto"
    }

    private val link = Link()

    suspend fun <T> exclusive(block: suspend Link.() -> T): T = lock.withLock { link.block() }

    /** Resets the adapter, applies fast settings, and lets it find the car's protocol. */
    suspend fun initialize(protocol: String): ElmInfo = exclusive {
        // Many clones drop bytes sent the instant the link opens, and some greet with a stale
        // prompt: settle, throw away whatever arrived, and give the reset a second chance.
        delay(LINK_SETTLE_MS)
        while (transport.incoming.tryReceive().isSuccess) Unit
        buffer.setLength(0)
        try {
            send("ATZ", 5_000) // Reset: prints the "ELM327 vX.X" banner.
        } catch (_: ElmException) {
            send("ATZ", 5_000)
        }
        currentHeader = null
        currentReceiveHeader = null
        val version = send("ATI").lines().lastOrNull { it.isNotBlank() }?.trim() ?: "ELM327"
        // Echo off, linefeeds off, spaces off, headers off, adaptive timing, protocol.
        for (command in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP$protocol")) {
            send(command)
        }
        // Triggers the protocol search on first use. With the ignition off the search can run
        // past the timeout (slow K-line inits) instead of ending in UNABLE TO CONNECT.
        val probe = try {
            send("0100", 15_000)
        } catch (_: ElmTimeoutException) {
            throw ElmException("The car's computer didn't answer the protocol search. Turn the ignition on and try again.")
        }
        if (ObdResponseParser.errorOf(probe) != null) {
            throw ElmException("The car's computer didn't answer. Turn the ignition on and try again.")
        }
        val number = send("ATDPN").trim().removePrefix("A").toIntOrNull(16) ?: 0
        val name = send("ATDP").trim().removePrefix("AUTO, ")
        ElmInfo(version, number, name)
    }

    private suspend fun sendRaw(command: String, timeoutMs: Long): String {
        transport.write("$command\r".toByteArray(Charsets.US_ASCII))
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            val prompt = buffer.indexOf(">")
            if (prompt >= 0) {
                val raw = buffer.substring(0, prompt)
                buffer.delete(0, prompt + 1)
                return clean(raw, command)
            }
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000
            if (remainingMs <= 0) {
                resync()
                throw ElmTimeoutException(command)
            }
            val chunk = receive(remainingMs) ?: continue
            buffer.append(String(chunk, Charsets.US_ASCII))
        }
    }

    /**
     * After a timeout the ELM may still be busy on the bus, and its late reply would be read as
     * the answer to the next command. Wait briefly for that late prompt; if none comes, interrupt
     * with a CR (a busy ELM prints "STOPPED" and a prompt). A CR to an idle ELM repeats the last
     * command, so it's only sent when the adapter still hasn't finished.
     */
    private suspend fun resync() {
        buffer.setLength(0)
        if (!awaitPrompt(RESYNC_TIMEOUT_MS)) {
            transport.write("\r".toByteArray(Charsets.US_ASCII))
            awaitPrompt(RESYNC_TIMEOUT_MS)
        }
        buffer.setLength(0)
    }

    private suspend fun awaitPrompt(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000
            if (remainingMs <= 0) return false
            val chunk = receive(remainingMs) ?: return false
            if (String(chunk, Charsets.US_ASCII).contains('>')) {
                buffer.setLength(0)
                return true
            }
        }
    }

    private suspend fun receive(timeoutMs: Long): ByteArray? = try {
        withTimeoutOrNull(timeoutMs) { transport.incoming.receive() }
    } catch (_: ClosedReceiveChannelException) {
        throw IOException("Adapter connection lost")
    }

    private fun clean(raw: String, command: String): String =
        raw.replace("\u0000", "")
            .split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(command, ignoreCase = true) }
            .joinToString("\n")

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 2_000L
        private const val LINK_SETTLE_MS = 300L
        private const val RESYNC_TIMEOUT_MS = 1_000L

        /**
         * Expected 11-bit CAN response ID (ATCRA) for non-emissions module headers.
         * Standard 7E0..7E7 and broadcast 7DF use null (ATAR default 7E8..7EF filter).
         * GM GMLAN body/chassis headers 240..25F reply on header + 0x400 (e.g. 241 -> 641);
         * other 11-bit physical UDS module headers 700..7DF reply on header + 8 (e.g. 726 -> 72E).
         */
        fun receiveHeaderFor(header: String?): String? {
            val clean = header?.trim()?.uppercase() ?: return null
            if (clean.length != 3 || clean == "7DF") return null
            val id = clean.toIntOrNull(16) ?: return null
            return when {
                id in 0x7E0..0x7E7 -> null
                id in 0x240..0x25F -> "%03X".format(java.util.Locale.US, id + 0x400)
                id in 0x700..0x7DF -> "%03X".format(java.util.Locale.US, id + 8)
                else -> null
            }
        }
    }
}
