package com.aiconsole.knightriderlauncher.obd

/** One on-board monitor test result (Mode 06, CAN format). */
data class MonitorTest(
    val mid: Int,
    val tid: Int,
    val monitor: String,
    val test: String,
    /** Scaled value/limits when the unit is known, else raw counts. */
    val value: Double,
    val min: Double,
    val max: Double,
    val unit: String,
    val passed: Boolean
)

/**
 * Mode 06 on CAN (ISO 15765-4): after the 0x46 service byte, each record is 9 bytes:
 * OBDMID, TID, unit/scaling id, test value (2), min limit (2), max limit (2).
 * Pass/fail compares the raw numbers, so it's right even for units not decoded here.
 */
object Mode06 {

    fun supportedMids(messages: List<ByteArray>, base: Int): Set<Int> =
        ObdResponseParser.serviceData(messages, byteArrayOf(0x06, base.toByte()))
            ?.let { bitmap(it, base) }.orEmpty()

    private fun bitmap(data: ByteArray, base: Int): Set<Int> {
        if (data.size < 4) return emptySet()
        val out = mutableSetOf<Int>()
        for (i in 0 until 32) {
            val byte = data[i / 8].toInt() and 0xFF
            if (byte and (0x80 shr (i % 8)) != 0) out += base + i + 1
        }
        return out
    }

    /** Parses every 9-byte record after the 0x46 service byte of each ECU reply. */
    fun results(messages: List<ByteArray>): List<MonitorTest> = messages.flatMap { msg ->
        if (msg.isEmpty() || (msg[0].toInt() and 0xFF) != 0x46) return@flatMap emptyList()
        val body = msg.copyOfRange(1, msg.size)
        (0 until body.size / 9).mapNotNull { r -> record(body, r * 9) }
    }

    private fun record(b: ByteArray, o: Int): MonitorTest? {
        fun u8(i: Int) = b[o + i].toInt() and 0xFF
        val mid = u8(0)
        if (mid % 0x20 == 0) return null // Supported-MID bitmaps, not tests.
        val tid = u8(1)
        val uas = u8(2)
        val signed = uas >= 0x80
        fun word(i: Int): Int {
            val v = (u8(i) shl 8) or u8(i + 1)
            return if (signed && v >= 0x8000) v - 0x10000 else v
        }
        val value = word(3)
        val min = word(5)
        val max = word(7)
        val (scale, offset, unit) = scaling(uas and 0x7F)
        return MonitorTest(
            mid = mid,
            tid = tid,
            monitor = monitorName(mid),
            test = testName(mid, tid),
            value = value * scale + offset,
            min = min * scale + offset,
            max = max * scale + offset,
            unit = unit,
            passed = value in min..max
        )
    }

    /** The unit/scaling ids decoded here (SAE J1979 Appendix E); others are shown as raw counts. */
    private fun scaling(id: Int): Triple<Double, Double, String> = when (id) {
        0x0A -> Triple(0.122, 0.0, "mV")
        0x0B -> Triple(0.001, 0.0, "V")
        0x0C -> Triple(0.01, 0.0, "V")
        0x10 -> Triple(1.0, 0.0, "ms")
        0x16 -> Triple(0.1, -40.0, "°C")
        0x24 -> Triple(1.0, 0.0, "counts")
        else -> Triple(1.0, 0.0, "")
    }

    fun monitorName(mid: Int): String = when (mid) {
        in 0x01..0x10 -> "O2 sensor B${(mid - 1) / 4 + 1}S${(mid - 1) % 4 + 1}"
        in 0x21..0x24 -> "Catalyst bank ${mid - 0x20}"
        in 0x31..0x34 -> "EGR bank ${mid - 0x30}"
        in 0x35..0x38 -> "VVT bank ${mid - 0x34}"
        0x39 -> "EVAP (cap off)"
        0x3A -> "EVAP 0.090\" leak"
        0x3B -> "EVAP 0.040\" leak"
        0x3C -> "EVAP 0.020\" leak"
        0x3D -> "Purge flow"
        in 0x41..0x50 -> "O2 heater B${(mid - 0x41) / 4 + 1}S${(mid - 0x41) % 4 + 1}"
        in 0x61..0x64 -> "Heated catalyst bank ${mid - 0x60}"
        in 0x71..0x74 -> "Secondary air ${mid - 0x70}"
        in 0x81..0x84 -> "Fuel system bank ${mid - 0x80}"
        0xA1 -> "Misfire (general)"
        in 0xA2..0xAD -> "Misfire cylinder ${mid - 0xA1}"
        else -> "Monitor %02X".format(mid)
    }

    private fun testName(mid: Int, tid: Int): String = when {
        mid in 0xA1..0xAD && tid == 0x0B -> "Misfires, avg of last 10 drives"
        mid in 0xA1..0xAD && tid == 0x0C -> "Misfires, this/last drive"
        else -> "Test %02X".format(tid)
    }

    /** Per-cylinder misfire counts for the current/last drive (TID 0x0C), cylinder number to count. */
    fun misfiresByCylinder(results: List<MonitorTest>): Map<Int, Int> =
        results.filter { it.mid in 0xA2..0xAD && it.tid == 0x0C }.associate { (it.mid - 0xA1) to it.value.toInt() }
}

/** A control module that answered on the CAN bus. */
data class VehicleModule(val responseId: String, val name: String) {
    /** Physical request header: CAN response ids are the request id + 8 for 7E0-7E7. */
    val requestHeader: String?
        get() = responseId.toIntOrNull(16)?.takeIf { it in 0x7E8..0x7EF }?.let { "%03X".format(it - 8) }
}

object ModuleScan {

    /** Response ids in a reply read with headers on (ATH1), e.g. "7E8 06 41 00 BE 3F A8 13". */
    fun responders(raw: String): List<VehicleModule> = raw.lines()
        .map { it.trim().replace(" ", "").uppercase() }
        .mapNotNull { line ->
            when {
                Regex("^7E[8-F][0-9A-F]{4,}$").matches(line) -> line.take(3)
                // 29-bit: 18DAF1xx, the last byte is the sender.
                Regex("^18DAF1[0-9A-F]{2}[0-9A-F]{4,}$").matches(line) -> line.take(8)
                else -> null
            }
        }
        .distinct()
        .sorted()
        .map { VehicleModule(it, nameOf(it)) }

    fun nameOf(responseId: String): String = when (responseId) {
        "7E8", "18DAF110" -> "Engine (ECM)"
        "7E9", "18DAF118" -> "Transmission (TCM)"
        else -> "Module $responseId"
    }
}
