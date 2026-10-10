package com.aiconsole.kittlauncher2.obd

/** What a VIN says offline, before (or without) the NHTSA lookup. */
data class VinBasics(val vin: String, val modelYear: Int?, val checkDigitValid: Boolean?, val region: String)

/** Full decode from NHTSA vPIC. */
data class VehicleIdentity(
    val vin: String,
    val year: String?,
    val make: String?,
    val model: String?,
    val trim: String?,
    val engine: String?,
    val fuel: String?,
    val drive: String?,
    val body: String?
) {
    fun summary(): String = listOfNotNull(year, make, model, trim).joinToString(" ").ifEmpty { vin }
}

/** Plain-Kotlin VIN rules (ISO 3779 / 49 CFR 565). */
object VinDecoder {

    private const val YEAR_CODES = "ABCDEFGHJKLMNPRSTVWXY123456789" // 1980..2009, then repeats from 2010.

    fun isValid(vin: String): Boolean = vin.length == 17 && vin.uppercase().all { it in '0'..'9' || (it in 'A'..'Z' && it !in "IOQ") }

    fun basics(vin: String): VinBasics? {
        val v = vin.trim().uppercase()
        if (!isValid(v)) return null
        return VinBasics(v, modelYear(v), checkDigit(v), region(v[0]))
    }

    /**
     * Position 10 repeats every 30 years. For North American vehicles, a letter in position 7
     * means 2010 or later, a digit means 1980-2009.
     */
    fun modelYear(vin: String): Int? {
        val index = YEAR_CODES.indexOf(vin[9]).takeIf { it >= 0 } ?: return null
        val base = 1980 + index
        return if (vin[6].isLetter()) base + 30 else base
    }

    /** North American check digit (position 9); null for regions that don't use it. */
    fun checkDigit(vin: String): Boolean? {
        if (vin[0] !in "12345") return null
        val weights = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)
        val sum = vin.mapIndexed { i, c -> value(c) * weights[i] }.sum()
        val expected = (sum % 11).let { if (it == 10) 'X' else '0' + it }
        return vin[8] == expected
    }

    private fun value(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'H' -> c - 'A' + 1
        in 'J'..'N' -> c - 'J' + 1
        'P' -> 7
        'R' -> 9
        in 'S'..'Z' -> c - 'S' + 2
        else -> 0
    }

    private fun region(c: Char): String = when (c) {
        in '1'..'5' -> "North America"
        in '6'..'7' -> "Oceania"
        in '8'..'9' -> "South America"
        in 'A'..'H' -> "Africa"
        in 'J'..'R' -> "Asia"
        else -> "Europe"
    }

    fun nhtsaUrl(vin: String): String = "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/$vin?format=json"
}
