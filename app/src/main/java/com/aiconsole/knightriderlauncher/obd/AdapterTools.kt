package com.aiconsole.knightriderlauncher.obd

/**
 * Guards the raw command console: adapter (AT) commands and read-only diagnostic services only.
 * Anything that clears codes, writes to or resets an ECU, or changes adapter EEPROM is refused,
 * so the console can't be used to modify the vehicle.
 */
object ConsoleSafety {

    /** Read-only OBD / UDS services: live data, freeze frame, codes, O2 tests, monitors, vehicle info, read-by-id. */
    private val READ_ONLY_SERVICES = setOf(0x01, 0x02, 0x03, 0x05, 0x06, 0x07, 0x09, 0x0A, 0x19, 0x22)

    /** AT commands that write adapter memory or flood the link. */
    private val BLOCKED_AT = listOf("PP", "@3", "MA", "MR", "MT", "BD", "WS", "FE")

    /** Null when the command may be sent; otherwise why not. */
    fun check(input: String): String? {
        val cmd = input.trim().replace(" ", "").uppercase()
        if (cmd.isEmpty()) return "Empty command."
        if (cmd.startsWith("AT")) {
            val rest = cmd.removePrefix("AT")
            return if (BLOCKED_AT.any { rest.startsWith(it) }) "AT$rest writes adapter settings or floods the link; not allowed here." else null
        }
        if (cmd.startsWith("ST")) {
            return if (cmd == "STI" || cmd == "STDI") null else "Only STI/STDI are allowed from STN commands."
        }
        if (!cmd.matches(Regex("[0-9A-F]+")) || cmd.length % 2 != 0) return "Enter AT commands or hex bytes, e.g. 010C."
        val service = cmd.substring(0, 2).toInt(16)
        return if (service in READ_ONLY_SERVICES) null
        else "Service %02X can clear codes or change the vehicle; only read-only services are allowed.".format(service)
    }
}

data class AdapterAssessment(val chip: String, val verdict: String)

/** What the adapter's identification strings say about the chip. */
object CloneCheck {
    fun assess(ati: String, deviceDescription: String?, stnId: String?): AdapterAssessment {
        val version = Regex("""v(\d+\.\d+[a-z]?)""", RegexOption.IGNORE_CASE).find(ati)?.groupValues?.get(1)
        val stn = stnId?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("?") }
        return when {
            stn != null -> AdapterAssessment(stn, "STN chip (OBDLink-class): genuine, fast and fully featured.")
            version == "1.5" -> AdapterAssessment(ati.trim(), "Clone: ELM Electronics never released a v1.5. Usually fine for basic data, may miss some commands.")
            version == "2.1" -> AdapterAssessment(ati.trim(), "Most v2.1 adapters are clones. Expect some slow or unsupported commands.")
            version != null -> AdapterAssessment(ati.trim(), "Reports ELM327 v$version. Can't tell a clone from the ID alone.")
            else -> AdapterAssessment(ati.trim().ifEmpty { "Unknown" }, "Didn't identify as an ELM327.")
        }.let { a -> deviceDescription?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("?") }?.let { a.copy(chip = "${a.chip} · $it") } ?: a }
    }
}

/** The layers a connection passes through, so failures name the right one. */
enum class LinkLayer(val label: String) {
    Transport("Transport"),
    Adapter("Adapter"),
    ElmInit("ELM init"),
    Ecu("ECU"),
    Decode("Decode")
}

data class LinkEvent(val timestamp: Long, val layer: LinkLayer, val message: String, val ok: Boolean)

/** Rolling connection log (most recent last). Thread-safe. */
class ConnectionLog(private val capacity: Int = 200) {
    private val events = ArrayDeque<LinkEvent>()

    @Synchronized
    fun add(event: LinkEvent) {
        events.addLast(event)
        while (events.size > capacity) events.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<LinkEvent> = events.toList()
}
