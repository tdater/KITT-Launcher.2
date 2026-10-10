package com.aiconsole.kittlauncher2.obd

/** One fill-up. Metric base units; [full] means the tank was filled to the top. */
data class FillUp(val timestamp: Long, val odometerKm: Double, val liters: Double, val totalCost: Double?, val full: Boolean)

/** Measured economy between two full fill-ups. */
data class FillEconomy(val endedAt: Long, val distanceKm: Double, val liters: Double) {
    val litersPer100Km: Double get() = liters / distanceKm * 100
}

/**
 * Plain-Kotlin fuel log. Real economy comes from full-to-full: the fuel added from one full
 * fill-up to the next (including partial fills between) over the distance driven.
 */
object FuelLog {

    fun decode(text: String): List<FillUp> = text.lines().mapNotNull { line ->
        val p = line.split('|')
        if (p.size != 5) return@mapNotNull null
        FillUp(
            timestamp = p[0].toLongOrNull() ?: return@mapNotNull null,
            odometerKm = p[1].toDoubleOrNull() ?: return@mapNotNull null,
            liters = p[2].toDoubleOrNull() ?: return@mapNotNull null,
            totalCost = p[3].toDoubleOrNull(),
            full = p[4] == "1"
        )
    }.sortedBy { it.odometerKm }

    fun encode(fills: List<FillUp>): String =
        fills.sortedBy { it.odometerKm }.joinToString("\n") { "${it.timestamp}|${it.odometerKm}|${it.liters}|${it.totalCost ?: ""}|${if (it.full) 1 else 0}" }

    fun economy(fills: List<FillUp>): List<FillEconomy> {
        val sorted = fills.sortedBy { it.odometerKm }
        val out = mutableListOf<FillEconomy>()
        var lastFull: FillUp? = null
        var liters = 0.0
        for (fill in sorted) {
            val start = lastFull
            if (start != null) liters += fill.liters
            if (fill.full) {
                if (start != null) {
                    val distance = fill.odometerKm - start.odometerKm
                    if (distance > 1) out += FillEconomy(fill.timestamp, distance, liters)
                }
                lastFull = fill
                liters = 0.0
            }
        }
        return out
    }

    /** Distance-weighted average L/100 km over all measured intervals. */
    fun average(economy: List<FillEconomy>): Double? {
        val km = economy.sumOf { it.distanceKm }
        return if (km > 0) economy.sumOf { it.liters } / km * 100 else null
    }

    /** Cost per km across fill-ups that recorded a price, over the distance they cover. */
    fun costPerKm(fills: List<FillUp>): Double? {
        val sorted = fills.sortedBy { it.odometerKm }
        if (sorted.size < 2) return null
        val cost = sorted.drop(1).mapNotNull { it.totalCost }.sum()
        val km = sorted.last().odometerKm - sorted.first().odometerKm
        return if (km > 0 && cost > 0) cost / km else null
    }
}
