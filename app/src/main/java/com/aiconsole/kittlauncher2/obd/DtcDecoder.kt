package com.aiconsole.kittlauncher2.obd

/** Decodes SAE J2012 trouble codes and gives short offline descriptions. */
object DtcDecoder {

    fun decode(a: Int, b: Int): String? {
        if (a == 0 && b == 0) return null
        val letter = "PCBU"[a shr 6]
        val first = (a shr 4) and 0x3
        val second = a and 0xF
        return String.format(java.util.Locale.US, "%c%d%X%02X", letter, first, second, b)
    }

    /** Best offline description: exact generic code if known, otherwise the code's subsystem. */
    fun describe(code: String): String {
        if (code.length != 5) return "Unknown code"
        COMMON[code]?.let { return it }
        misfire(code)?.let { return it }
        val system = when (code[0]) {
            'P' -> "Powertrain"
            'C' -> "Chassis"
            'B' -> "Body"
            'U' -> "Network"
            else -> "Unknown"
        }
        val scope = if (code[1] == '1' || code[1] == '3') "manufacturer-specific" else "generic"
        val area = if (code[0] == 'P') POWERTRAIN_AREA[code[2]] else null
        return listOfNotNull("$system ($scope)", area).joinToString(": ")
    }

    private fun misfire(code: String): String? {
        val cylinder = code.removePrefix("P03").toIntOrNull() ?: return null
        return if (code.startsWith("P03") && cylinder in 1..12) "Cylinder $cylinder misfire detected" else null
    }

    private val POWERTRAIN_AREA = mapOf(
        '0' to "fuel/air metering & auxiliary emissions",
        '1' to "fuel and air metering",
        '2' to "fuel and air metering (injector circuit)",
        '3' to "ignition system or misfire",
        '4' to "auxiliary emission controls",
        '5' to "vehicle speed, idle control & auxiliary inputs",
        '6' to "computer & output circuits",
        '7' to "transmission",
        '8' to "transmission",
        '9' to "transmission",
        'A' to "hybrid propulsion"
    )

    private val COMMON = mapOf(
        "P0011" to "Camshaft timing over-advanced, bank 1",
        "P0016" to "Crankshaft/camshaft position correlation, bank 1 sensor A",
        "P0087" to "Fuel rail/system pressure too low",
        "P0101" to "Mass airflow sensor range/performance",
        "P0113" to "Intake air temperature sensor high input",
        "P0118" to "Engine coolant temperature sensor high input",
        "P0128" to "Coolant thermostat below regulating temperature",
        "P0133" to "O2 sensor slow response, bank 1 sensor 1",
        "P0135" to "O2 sensor heater circuit, bank 1 sensor 1",
        "P0141" to "O2 sensor heater circuit, bank 1 sensor 2",
        "P0171" to "System too lean, bank 1",
        "P0172" to "System too rich, bank 1",
        "P0174" to "System too lean, bank 2",
        "P0175" to "System too rich, bank 2",
        "P0299" to "Turbo/supercharger underboost",
        "P0300" to "Random/multiple cylinder misfire detected",
        "P0325" to "Knock sensor 1 circuit, bank 1",
        "P0335" to "Crankshaft position sensor A circuit",
        "P0340" to "Camshaft position sensor circuit, bank 1",
        "P0401" to "EGR flow insufficient",
        "P0420" to "Catalyst efficiency below threshold, bank 1",
        "P0430" to "Catalyst efficiency below threshold, bank 2",
        "P0440" to "EVAP system malfunction",
        "P0442" to "EVAP system small leak detected",
        "P0446" to "EVAP vent control circuit",
        "P0455" to "EVAP system large leak detected",
        "P0456" to "EVAP system very small leak detected",
        "P0457" to "EVAP leak detected (fuel cap loose/off)",
        "P0500" to "Vehicle speed sensor A",
        "P0506" to "Idle speed lower than expected",
        "P0507" to "Idle speed higher than expected",
        "P0562" to "System voltage low",
        "P0700" to "Transmission control system malfunction"
    )
}
