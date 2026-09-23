package com.aiconsole.knightriderlauncher.obd

/** Physical quantity of a PID; values are stored in metric base units and converted for display. */
enum class Quantity(val metricUnit: String, val imperialUnit: String) {
    SPEED("km/h", "mph"),
    TEMPERATURE("°C", "°F"),
    PRESSURE("kPa", "psi"),
    PERCENT("%", "%"),
    RPM("rpm", "rpm"),
    VOLTAGE("V", "V"),
    AIRFLOW("g/s", "g/s"),
    FUEL_RATE("L/h", "gal/h"),
    DISTANCE("km", "mi"),
    TIME("s", "s"),
    ANGLE("°", "°"),
    RATIO("λ", "λ"),
    TORQUE("N·m", "lb·ft"),
    CUSTOM("", "");

    fun toDisplay(value: Double, metric: Boolean): Double = if (metric) value else when (this) {
        SPEED, DISTANCE -> value * 0.621371
        TEMPERATURE -> value * 9.0 / 5.0 + 32.0
        PRESSURE -> value * 0.145038
        FUEL_RATE -> value * 0.264172
        TORQUE -> value * 0.737562
        else -> value
    }

    fun fromDisplay(value: Double, metric: Boolean): Double = if (metric) value else when (this) {
        SPEED, DISTANCE -> value / 0.621371
        TEMPERATURE -> (value - 32.0) * 5.0 / 9.0
        PRESSURE -> value / 0.145038
        FUEL_RATE -> value / 0.264172
        TORQUE -> value / 0.737562
        else -> value
    }
}

enum class PidSource { STANDARD, MANUFACTURER, CUSTOM, ADAPTER }

/**
 * One readable value. [command] is the raw request ("010C", "221940", "ATRV"); [formula]
 * maps response data bytes A, B, C... to a value in the [quantity]'s metric unit.
 */
data class PidDefinition(
    val id: String,
    val name: String,
    val shortName: String,
    val command: String,
    val formula: String,
    val quantity: Quantity,
    val min: Double,
    val max: Double,
    /** Data bytes returned after the echoed PID; 0 = take everything. */
    val bytes: Int = 0,
    /** CAN header (ATSH) to address a specific ECU; null = engine ECU. */
    val header: String? = null,
    val source: PidSource = PidSource.STANDARD,
    val customUnit: String = "",
    /** Fast-changing values are polled every cycle, slow ones every few cycles. */
    val fast: Boolean = false,
    val note: String? = null
) {
    /** Standard Mode 01 PID on the engine ECU; these can be batched into multi-PID requests. */
    val mode01Pid: Int? get() =
        if (header == null && command.length == 4 && command.startsWith("01")) command.substring(2).toIntOrNull(16) else null

    fun unit(metric: Boolean): String =
        if (quantity == Quantity.CUSTOM) customUnit else if (metric) quantity.metricUnit else quantity.imperialUnit

    fun displayValue(base: Double, metric: Boolean) = quantity.toDisplay(base, metric)
    fun displayMin(metric: Boolean) = quantity.toDisplay(min, metric)
    fun displayMax(metric: Boolean) = quantity.toDisplay(max, metric)
}

/** SAE J1979 Mode 01 PIDs plus the adapter's own voltage reading. */
object PidCatalog {

    const val RPM = "01_0C"
    const val SPEED = "01_0D"
    const val COOLANT = "01_05"
    const val THROTTLE = "01_11"
    const val ENGINE_LOAD = "01_04"
    const val FUEL_LEVEL = "01_2F"
    const val INTAKE_TEMP = "01_0F"
    const val MODULE_VOLTAGE = "01_42"
    const val ADAPTER_VOLTAGE = "elm_voltage"

    private fun std(
        pid: Int,
        name: String,
        short: String,
        formula: String,
        quantity: Quantity,
        min: Double,
        max: Double,
        bytes: Int = 1,
        fast: Boolean = false
    ) = PidDefinition(
        id = "01_%02X".format(java.util.Locale.US, pid),
        name = name,
        shortName = short,
        command = "01%02X".format(java.util.Locale.US, pid),
        formula = formula,
        quantity = quantity,
        min = min,
        max = max,
        bytes = bytes,
        fast = fast
    )

    val adapterVoltage = PidDefinition(
        id = ADAPTER_VOLTAGE,
        name = "Battery voltage (adapter)",
        shortName = "Battery",
        command = "ATRV",
        formula = "",
        quantity = Quantity.VOLTAGE,
        min = 8.0,
        max = 16.0,
        source = PidSource.ADAPTER
    )

    val standard: List<PidDefinition> = listOf(
        std(0x04, "Calculated engine load", "Load", "A*100/255", Quantity.PERCENT, 0.0, 100.0, fast = true),
        std(0x05, "Engine coolant temperature", "Coolant", "A-40", Quantity.TEMPERATURE, -40.0, 130.0),
        std(0x06, "Short term fuel trim, bank 1", "STFT B1", "(A-128)*100/128", Quantity.PERCENT, -25.0, 25.0, fast = true),
        std(0x07, "Long term fuel trim, bank 1", "LTFT B1", "(A-128)*100/128", Quantity.PERCENT, -25.0, 25.0),
        std(0x08, "Short term fuel trim, bank 2", "STFT B2", "(A-128)*100/128", Quantity.PERCENT, -25.0, 25.0, fast = true),
        std(0x09, "Long term fuel trim, bank 2", "LTFT B2", "(A-128)*100/128", Quantity.PERCENT, -25.0, 25.0),
        std(0x0A, "Fuel pressure", "Fuel P", "A*3", Quantity.PRESSURE, 0.0, 765.0),
        std(0x0B, "Intake manifold pressure", "MAP", "A", Quantity.PRESSURE, 0.0, 255.0, fast = true),
        std(0x0C, "Engine RPM", "RPM", "((A*256)+B)/4", Quantity.RPM, 0.0, 8000.0, bytes = 2, fast = true),
        std(0x0D, "Vehicle speed", "Speed", "A", Quantity.SPEED, 0.0, 240.0, fast = true),
        std(0x0E, "Timing advance", "Timing", "A/2-64", Quantity.ANGLE, -20.0, 50.0, fast = true),
        std(0x0F, "Intake air temperature", "IAT", "A-40", Quantity.TEMPERATURE, -40.0, 100.0),
        std(0x10, "Mass air flow", "MAF", "((A*256)+B)/100", Quantity.AIRFLOW, 0.0, 300.0, bytes = 2, fast = true),
        std(0x11, "Throttle position", "Throttle", "A*100/255", Quantity.PERCENT, 0.0, 100.0, fast = true),
        std(0x14, "O2 sensor voltage, bank 1 sensor 1", "O2 B1S1", "A/200", Quantity.VOLTAGE, 0.0, 1.275, bytes = 2, fast = true),
        std(0x15, "O2 sensor voltage, bank 1 sensor 2", "O2 B1S2", "A/200", Quantity.VOLTAGE, 0.0, 1.275, bytes = 2),
        std(0x1F, "Run time since engine start", "Run time", "(A*256)+B", Quantity.TIME, 0.0, 65535.0, bytes = 2),
        std(0x21, "Distance traveled with MIL on", "MIL dist", "(A*256)+B", Quantity.DISTANCE, 0.0, 65535.0, bytes = 2),
        std(0x23, "Fuel rail pressure (gauge)", "Rail P", "((A*256)+B)*10", Quantity.PRESSURE, 0.0, 200000.0, bytes = 2),
        std(0x2C, "Commanded EGR", "EGR", "A*100/255", Quantity.PERCENT, 0.0, 100.0),
        std(0x2F, "Fuel tank level", "Fuel", "A*100/255", Quantity.PERCENT, 0.0, 100.0),
        std(0x31, "Distance since codes cleared", "Since clr", "(A*256)+B", Quantity.DISTANCE, 0.0, 65535.0, bytes = 2),
        std(0x33, "Barometric pressure", "Baro", "A", Quantity.PRESSURE, 50.0, 110.0),
        std(0x3C, "Catalyst temperature, bank 1 sensor 1", "Cat B1S1", "((A*256)+B)/10-40", Quantity.TEMPERATURE, 0.0, 1000.0, bytes = 2),
        std(0x42, "Control module voltage", "ECU volts", "((A*256)+B)/1000", Quantity.VOLTAGE, 8.0, 16.0, bytes = 2),
        std(0x43, "Absolute load", "Abs load", "((A*256)+B)*100/255", Quantity.PERCENT, 0.0, 200.0, bytes = 2, fast = true),
        std(0x44, "Commanded air-fuel equivalence ratio", "Lambda", "((A*256)+B)/32768", Quantity.RATIO, 0.5, 1.5, bytes = 2, fast = true),
        std(0x45, "Relative throttle position", "Rel thr", "A*100/255", Quantity.PERCENT, 0.0, 100.0, fast = true),
        std(0x46, "Ambient air temperature", "Ambient", "A-40", Quantity.TEMPERATURE, -40.0, 60.0),
        std(0x47, "Absolute throttle position B", "Thr B", "A*100/255", Quantity.PERCENT, 0.0, 100.0, fast = true),
        std(0x49, "Accelerator pedal position D", "Pedal D", "A*100/255", Quantity.PERCENT, 0.0, 100.0, fast = true),
        std(0x4A, "Accelerator pedal position E", "Pedal E", "A*100/255", Quantity.PERCENT, 0.0, 100.0, fast = true),
        std(0x4C, "Commanded throttle actuator", "Thr cmd", "A*100/255", Quantity.PERCENT, 0.0, 100.0, fast = true),
        std(0x52, "Ethanol fuel percentage", "Ethanol", "A*100/255", Quantity.PERCENT, 0.0, 100.0),
        std(0x5A, "Relative accelerator pedal position", "Pedal", "A*100/255", Quantity.PERCENT, 0.0, 100.0, fast = true),
        std(0x5B, "Hybrid battery pack remaining life", "HV batt", "A*100/255", Quantity.PERCENT, 0.0, 100.0),
        std(0x5C, "Engine oil temperature", "Oil temp", "A-40", Quantity.TEMPERATURE, -40.0, 160.0),
        std(0x5E, "Engine fuel rate", "Fuel rate", "((A*256)+B)/20", Quantity.FUEL_RATE, 0.0, 60.0, bytes = 2, fast = true),
        std(0x61, "Driver's demand engine torque", "Demand Tq", "A-125", Quantity.PERCENT, -125.0, 130.0, fast = true),
        std(0x62, "Actual engine torque", "Actual Tq", "A-125", Quantity.PERCENT, -125.0, 130.0, fast = true),
        std(0x63, "Engine reference torque", "Ref Tq", "(A*256)+B", Quantity.TORQUE, 0.0, 2000.0, bytes = 2)
    )

    /** Data length of every standard Mode 01 PID, for splitting multi-PID responses. */
    val mode01ByteCounts: Map<Int, Int> = buildMap {
        standard.forEach { def -> def.mode01Pid?.let { put(it, def.bytes) } }
        // "PIDs supported" and status PIDs, so they never break a multi-PID walk.
        listOf(0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0, 0x01).forEach { put(it, 4) }
    }
}
