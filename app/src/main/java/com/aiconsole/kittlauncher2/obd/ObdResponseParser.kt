package com.aiconsole.kittlauncher2.obd

/**
 * Parses ELM327 text responses (headers off, spaces on or off) into raw OBD messages.
 * Handles ISO-TP multi-frame output ("014" length line followed by "0:", "1:" frames)
 * and multiple ECUs answering the same request (one message each).
 */
object ObdResponseParser {

    private val ERRORS = listOf(
        "NO DATA", "?", "ERROR", "UNABLE TO CONNECT", "STOPPED", "CAN ERROR", "BUS ERROR", "BUFFER FULL",
        "FB ERROR", "DATA ERROR", "BUS BUSY", "ACT ALERT", "LV RESET", "<RX ERROR", "BUS INIT: ...ERROR"
    )
    private val LENGTH_LINE = Regex("^[0-9A-F]{3}$")
    private val FRAME_LINE = Regex("^[0-9A-F]:([0-9A-F]*)$")
    private val HEX_LINE = Regex("^[0-9A-F]+$")

    /** True when the adapter/ECU reported a failure instead of data. */
    fun errorOf(raw: String): String? {
        val lines = raw.uppercase().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return "NO DATA"
        return lines.firstNotNullOfOrNull { line -> ERRORS.firstOrNull { line == it || line.startsWith(it) } }
            ?.takeIf { messages(raw).isEmpty() }
    }

    fun messages(raw: String): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var current: MutableList<Byte>? = null
        var expected = 0

        fun flush() {
            current?.let { bytes ->
                val size = if (expected > 0) minOf(expected, bytes.size) else bytes.size
                if (size > 0) result += bytes.subList(0, size).toByteArray()
            }
            current = null
            expected = 0
        }

        raw.uppercase().lineSequence()
            .map { it.replace(" ", "").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("SEARCHING") && !it.startsWith("BUSINIT") }
            .forEach { line ->
                val frame = FRAME_LINE.matchEntire(line)
                when {
                    LENGTH_LINE.matches(line) -> {
                        flush()
                        expected = line.toInt(16)
                        current = mutableListOf()
                    }
                    frame != null -> {
                        if (current == null) current = mutableListOf()
                        current!!.addAll(hexBytes(frame.groupValues[1]).toList())
                    }
                    HEX_LINE.matches(line) && line.length % 2 == 0 -> {
                        flush()
                        result += hexBytes(line)
                    }
                    HEX_LINE.matches(line) && line.length >= 7 && line.length % 2 == 1 -> {
                        // A 3-digit CAN response header (e.g. 641, 72E, 7E8) prefixed to even hex data bytes.
                        flush()
                        result += hexBytes(line.substring(3))
                    }
                    // Anything else (prompts, "OK", noise) is ignored.
                }
            }
        flush()
        return result
    }

    /**
     * Splits Mode 01 responses (single or multi-PID) into per-PID data using known byte counts.
     * Unknown PIDs stop the walk, since their length can't be inferred.
     */
    fun mode01Values(messages: List<ByteArray>, byteCounts: Map<Int, Int>): Map<Int, ByteArray> {
        val out = mutableMapOf<Int, ByteArray>()
        for (message in messages) {
            if (message.isEmpty() || message[0].u() != 0x41) continue
            var i = 1
            while (i < message.size) {
                val pid = message[i].u()
                val length = byteCounts[pid] ?: break
                val end = if (length == 0) message.size else i + 1 + length
                if (end > message.size) break
                out.putIfAbsent(pid, message.copyOfRange(i + 1, end))
                i = end
            }
        }
        return out
    }

    /**
     * Data following the positive-response echo for any service, e.g. request `221940`
     * expects `62 19 40 <data>`. Returns null for negative responses (`7F`) or no match.
     */
    fun serviceData(messages: List<ByteArray>, request: ByteArray): ByteArray? {
        if (request.isEmpty()) return null
        val positive = (request[0].u() + 0x40).toByte()
        for (message in messages) {
            for (offset in 0..1) {
                if (offset == 1 && (message.isEmpty() || message[0].u() !in 1..7)) continue
                if (message.size <= offset + request.size) continue
                if (message[offset] != positive) continue
                var matches = true
                for (k in 1 until request.size) if (message[offset + k] != request[k]) matches = false
                if (matches) return message.copyOfRange(offset + request.size, message.size)
            }
        }
        return null
    }

    /** Mode 01 "PIDs supported" bitmap (request 0100, 0120, ...), OR-ed across all ECUs. */
    fun supportedPids(messages: List<ByteArray>, base: Int): Set<Int> {
        val out = mutableSetOf<Int>()
        for (message in messages) {
            if (message.size < 6 || message[0].u() != 0x41 || message[1].u() != base) continue
            for (byteIndex in 0 until 4) {
                val b = message[2 + byteIndex].u()
                for (bit in 0 until 8) {
                    if (b and (0x80 shr bit) != 0) out += base + byteIndex * 8 + bit + 1
                }
            }
        }
        return out
    }

    /**
     * Mode 02 freeze-frame data: request `02 <pid> <frame>` answers `42 <pid> <frame> <data>`.
     * Returns the data bytes for [pid] in [frame], or null when the ECU has none.
     */
    fun freezeFrameData(messages: List<ByteArray>, pid: Int, frame: Int = 0): ByteArray? {
        for (message in messages) {
            if (message.size < 4 || message[0].u() != 0x42 || message[1].u() != pid || message[2].u() != frame) continue
            return message.copyOfRange(3, message.size)
        }
        return null
    }

    /** Mode 02 "PIDs supported" bitmap (request 020000, 022000, ...), OR-ed across all ECUs. */
    fun freezeFrameSupported(messages: List<ByteArray>, base: Int): Set<Int> {
        val out = mutableSetOf<Int>()
        for (message in messages) {
            val data = freezeFrameData(listOf(message), base) ?: continue
            if (data.size < 4) continue
            for (byteIndex in 0 until 4) {
                val b = data[byteIndex].u()
                for (bit in 0 until 8) {
                    if (b and (0x80 shr bit) != 0) out += base + byteIndex * 8 + bit + 1
                }
            }
        }
        return out
    }

    /** The trouble code that caused the freeze frame (Mode 02 PID 02), or null for none. */
    fun freezeFrameDtc(messages: List<ByteArray>): String? {
        val data = freezeFrameData(messages, 0x02) ?: return null
        if (data.size < 2) return null
        return DtcDecoder.decode(data[0].u(), data[1].u())
    }

    /** Fault codes from Mode 03/07/0A responses. CAN responses carry a count byte first. */
    fun dtcs(messages: List<ByteArray>, responseService: Int, isCan: Boolean): List<String> {
        val codes = LinkedHashSet<String>()
        for (message in messages) {
            if (message.isEmpty() || message[0].u() != responseService) continue
            var data = message.copyOfRange(1, message.size)
            if (isCan && data.isNotEmpty()) {
                val count = data[0].u()
                data = data.copyOfRange(1, minOf(data.size, 1 + count * 2))
            }
            var i = 0
            while (i + 1 < data.size) {
                DtcDecoder.decode(data[i].u(), data[i + 1].u())?.let(codes::add)
                i += 2
            }
        }
        return codes.toList()
    }

    /** VIN from Mode 09 PID 02, for both CAN (one message) and older protocols (several). */
    fun vin(messages: List<ByteArray>): String? {
        val chars = messages
            .filter { it.size > 3 && it[0].u() == 0x49 && it[1].u() == 0x02 }
            .flatMap { it.copyOfRange(3, it.size).toList() }
            .map { it.u() }
            .filter { it in 0x30..0x5A }
            .map { it.toChar() }
            .joinToString("")
        return chars.takeLast(17).takeIf { it.length == 17 }
    }

    /** Battery voltage from `ATRV`, e.g. "12.6V". */
    fun voltage(raw: String): Double? =
        Regex("(\\d{1,2}(?:\\.\\d+)?)\\s*V", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toDoubleOrNull()

    fun hexBytes(hex: String): ByteArray {
        val clean = hex.filter { it.isLetterOrDigit() }
        return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun Byte.u(): Int = toInt() and 0xFF
}
