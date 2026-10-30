package com.aiconsole.kittlauncher2.ui.components

import com.aiconsole.kittlauncher2.obd.PidCatalog
import kotlin.math.roundToInt

/** How worried a live value should look. */
enum class ReadoutLevel { Unknown, Ok, Warn, Bad }

/** One live value shown as a chip under the hologram car; tapping it opens a detail card. */
data class CarReadout(val key: String, val label: String, val pid: String, val about: String)

/** What the chips show and how: pure, so the wording, units and warning levels are unit-tested. */
object CarReadouts {
    private const val KMH_TO_MPH = 0.621371
    private const val LITERS_PER_GALLON = 3.78541
    private const val KPA_TO_PSI = 0.145038

    val ALL = listOf(
        CarReadout("speed", "SPEED", PidCatalog.SPEED, "Road speed from the engine computer. The GPS speed on the status strip is the fallback when the adapter is offline."),
        CarReadout("load", "LOAD", PidCatalog.ENGINE_LOAD, "How hard the engine is working, as a share of the most air it could take in at this speed. Idle is low; long climbs and hard pulls run high."),
        CarReadout("throttle", "THROTTLE", PidCatalog.THROTTLE, "How far the throttle plate is open. It follows your right foot, but the computer can hold it back."),
        CarReadout("fuel", "FUEL", PidCatalog.FUEL_LEVEL, "Tank level as reported by the fuel sender. It reads a little differently on slopes and while the fuel sloshes."),
        CarReadout("intake", "INTAKE", PidCatalog.INTAKE_TEMP, "Temperature of the air going into the engine. Hot air means less power; it runs higher in traffic and on hot days."),
        CarReadout("flow", "BURN", PidCatalog.FUEL_RATE, "How much fuel the engine is burning right now. Not every car reports it.")
    )

    val PIDS: Set<String> = ALL.map { it.pid }.toSet()

    fun byKey(key: String): CarReadout? = ALL.firstOrNull { it.key == key }

    /** The number alone, or "--" while there's no fresh value. */
    fun number(key: String, value: Double?, metric: Boolean): String {
        if (value == null) return "--"
        return when (key) {
            "speed" -> (if (metric) value else value * KMH_TO_MPH).roundToInt().toString()
            "load", "throttle", "fuel" -> value.roundToInt().toString()
            "intake" -> (if (metric) value else value * 9 / 5 + 32).roundToInt().toString()
            "flow" -> if (metric) "%.1f".format(java.util.Locale.US, value) else "%.2f".format(java.util.Locale.US, value / LITERS_PER_GALLON)
            else -> "%.1f".format(java.util.Locale.US, value)
        }
    }

    /** The unit; a leading space marks a word unit (" mph") that a narrow chip moves into its label instead. */
    fun unit(key: String, metric: Boolean): String = when (key) {
        "speed" -> if (metric) " km/h" else " mph"
        "load", "throttle", "fuel" -> "%"
        "intake" -> if (metric) "°C" else "°F"
        "flow" -> if (metric) " L/h" else " gal/h"
        else -> ""
    }

    /** The number as the driver should see it, or "--" while there's no fresh value. */
    fun text(key: String, value: Double?, metric: Boolean): String =
        if (value == null) "--" else number(key, value, metric) + unit(key, metric)

    /** Chip label: the name, or just the unit where a word unit would crowd the name out (SPEED shows MPH, BURN shows GPH). */
    fun chipLabel(r: CarReadout, metric: Boolean): String = when (r.key) {
        "speed" -> if (metric) "KM/H" else "MPH"
        "flow" -> if (metric) "L/H" else "GPH"
        "throttle" -> "THRTL"
        else -> r.label
    }

    /** Chip value: the number, with symbol units (%, degrees) kept and word units left to the label. */
    fun chipValue(key: String, value: Double?, metric: Boolean): String {
        if (value == null) return "--"
        val u = unit(key, metric)
        return number(key, value, metric) + if (u.startsWith(" ")) "" else u
    }

    fun level(key: String, value: Double?): ReadoutLevel {
        if (value == null) return ReadoutLevel.Unknown
        return when (key) {
            "load" -> if (value > 95) ReadoutLevel.Bad else if (value > 85) ReadoutLevel.Warn else ReadoutLevel.Ok
            "throttle" -> if (value > 90) ReadoutLevel.Warn else ReadoutLevel.Ok
            "fuel" -> if (value < 10) ReadoutLevel.Bad else if (value < 25) ReadoutLevel.Warn else ReadoutLevel.Ok
            "intake" -> if (value > 70) ReadoutLevel.Bad else if (value > 55) ReadoutLevel.Warn else ReadoutLevel.Ok
            else -> ReadoutLevel.Ok
        }
    }

    /**
     * Builds the four bottom callout boxes (slots 4..7, anchored to wheels w0..w3).
     * Shows the four tyre pressures when any TPMS sensor reports (or before OBD connects);
     * automatically falls back to live Mode 01 engine/drivetrain readings (LOAD, THROTTLE, INTAKE,
     * BURN/RPM) when OBD is streaming Mode 01 data on a vehicle whose TPMS is not on standard CAN.
     */
    fun bottomCallouts(
        tirePrefix: String,
        tiresKpa: List<Double?>,
        metric: Boolean,
        load: Double?,
        throttle: Double?,
        intakeC: Double?,
        burnLph: Double?,
        rpm: Double? = null,
        lastSlots: Map<String, Int> = emptyMap()
    ): List<Callout> {
        val hasAnyTpms = tiresKpa.any { it != null }
        val hasMode01 = listOf(load, throttle, intakeC, burnLph, rpm).any { it != null }
        if (hasAnyTpms || !hasMode01) {
            val corners = listOf("FL", "FR", "RL", "RR")
            return (0 until 4).map { i ->
                val kpa = tiresKpa.getOrNull(i)
                val value = if (kpa == null) "--" else if (metric) "${kpa.roundToInt()} kPa" else "${(kpa * KPA_TO_PSI).roundToInt()} PSI"
                val psi = kpa?.times(KPA_TO_PSI)
                val sub = when {
                    psi == null -> null
                    psi < 26.0 -> "LOW"
                    psi > 40.0 -> "HIGH"
                    else -> "OK"
                }
                val lvl = when {
                    psi == null -> ReadoutLevel.Unknown
                    psi in 26.0..40.0 -> ReadoutLevel.Ok
                    else -> ReadoutLevel.Bad
                }
                val key = "tire$i"
                Callout(key, "$tirePrefix ${corners[i]}", value, sub, lvl, lastSlots[key] ?: (4 + i), "w$i")
            }
        }
        val fourthTitle: String
        val fourthValue: String
        val fourthSub: String?
        val fourthLevel: ReadoutLevel
        if (burnLph != null || rpm == null) {
            fourthTitle = "BURN"
            fourthValue = text("flow", burnLph, metric)
            fourthSub = if (burnLph != null) "FUEL RATE" else null
            fourthLevel = if (burnLph != null) ReadoutLevel.Ok else ReadoutLevel.Unknown
        } else {
            fourthTitle = "RPM"
            fourthValue = "%,d rpm".format(java.util.Locale.US, rpm.roundToInt())
            fourthSub = "ENGINE"
            fourthLevel = ReadoutLevel.Ok
        }
        return listOf(
            Callout("tire0", "LOAD", text("load", load, metric), if (load != null) "ENGINE" else null, level("load", load), lastSlots["tire0"] ?: 4, "w0"),
            Callout("tire1", "THROTTLE", text("throttle", throttle, metric), if (throttle != null) "PEDAL" else null, level("throttle", throttle), lastSlots["tire1"] ?: 5, "w1"),
            Callout("tire2", "INTAKE", text("intake", intakeC, metric), if (intakeC != null) "AIR TEMP" else null, level("intake", intakeC), lastSlots["tire2"] ?: 6, "w2"),
            Callout("tire3", fourthTitle, fourthValue, fourthSub, fourthLevel, lastSlots["tire3"] ?: 7, "w3")
        )
    }
}

/** Running minimum, maximum and average of one reading since the launcher started. */
class ReadoutStats {
    var min = Double.NaN
        private set
    var max = Double.NaN
        private set
    private var sum = 0.0
    var count = 0
        private set

    val average: Double get() = if (count == 0) Double.NaN else sum / count

    fun add(value: Double) {
        if (value.isNaN()) return
        min = if (count == 0) value else minOf(min, value)
        max = if (count == 0) value else maxOf(max, value)
        sum += value
        count++
    }
}
