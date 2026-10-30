package com.aiconsole.kittlauncher2.obd

/** An addressable control module on the CAN bus (request header; ECU replies on header + 8). */
data class EcuDefinition(val name: String, val header: String)

/**
 * Manufacturer options: which ECUs to address beyond the engine, extended (Mode 21/22)
 * PIDs, and VIN world-manufacturer-identifier prefixes for auto-detection.
 */
data class ManufacturerProfile(
    val id: String,
    val name: String,
    val wmiPrefixes: List<String>,
    val ecus: List<EcuDefinition>,
    val pids: List<PidDefinition> = emptyList(),
    val notes: String = ""
)

object ManufacturerProfiles {

    const val AUTO = "auto"

    private val ENGINE = EcuDefinition("Engine (PCM/ECM)", "7E0")
    private val TRANS_7E1 = EcuDefinition("Transmission (TCM)", "7E1")

    private fun extended(
        id: String,
        name: String,
        short: String,
        command: String,
        header: String,
        formula: String,
        quantity: Quantity,
        min: Double,
        max: Double,
        note: String,
        receiveHeader: String? = Elm327.receiveHeaderFor(header),
        candidates: List<PidCandidate> = emptyList()
    ) = PidDefinition(
        id = id,
        name = name,
        shortName = short,
        command = command,
        formula = formula,
        quantity = quantity,
        min = min,
        max = max,
        bytes = FormulaEvaluator.bytesUsed(formula),
        header = header,
        source = PidSource.MANUFACTURER,
        note = note,
        receiveHeader = receiveHeader,
        candidates = candidates
    )

    private fun gmCandidates(bcmCmd: String, gmlanCmd: String, ecmCmd: String, fordCmd: String, globalBCmd: String = "222A05", fcaCmd: String = "223198"): List<PidCandidate> = listOf(
        PidCandidate(bcmCmd, "241", "tpms(A)", "641"),
        PidCandidate(globalBCmd, "241", "((A*256)+B)/16", "641"),
        PidCandidate(bcmCmd, "7E0", "tpms(A)"),
        PidCandidate(gmlanCmd, "241", "tpms(A)", "641"),
        PidCandidate(ecmCmd, "7E0", "tpms(A)"),
        PidCandidate(bcmCmd, "7DF", "tpms(A)"),
        PidCandidate(fordCmd, "726", "((A*256)+B)*0.344738", "72E"),
        PidCandidate(fcaCmd, "7E0", "((A*256)+B)/10")
    )

    private fun tirePressure(
        id: String,
        name: String,
        short: String,
        command: String,
        header: String = "241",
        formula: String = "tpms(A)",
        receiveHeader: String? = Elm327.receiveHeaderFor(header),
        candidates: List<PidCandidate> = emptyList(),
        note: String = "Community-sourced Mode 22 TPMS sensor reading."
    ) = extended(
        id = id,
        name = name,
        short = short,
        command = command,
        header = header,
        formula = formula,
        quantity = Quantity.PRESSURE,
        min = 0.0,
        max = 450.0,
        note = note,
        receiveHeader = receiveHeader,
        candidates = candidates
    )

    private val gmTirePids: List<PidDefinition> = listOf(
        tirePressure(
            PidCatalog.TIRE_LF, "Tire pressure, left front", "Tire LF", "225005",
            candidates = gmCandidates("225005", "2232A0", "22248E", "222813", "222A05", "223198")
        ),
        tirePressure(
            PidCatalog.TIRE_RF, "Tire pressure, right front", "Tire RF", "225006",
            candidates = gmCandidates("225006", "2232A1", "22248F", "222814", "222A06", "223199")
        ),
        tirePressure(
            PidCatalog.TIRE_RR, "Tire pressure, right rear", "Tire RR", "225007",
            candidates = gmCandidates("225007", "2232A3", "222491", "222815", "222A07", "22319B")
        ),
        tirePressure(
            PidCatalog.TIRE_LR, "Tire pressure, left rear", "Tire LR", "225008",
            candidates = gmCandidates("225008", "2232A2", "222490", "222816", "222A08", "22319A")
        )
    )

    private val fordTirePids: List<PidDefinition> = listOf(
        tirePressure(
            PidCatalog.TIRE_LF, "Tire pressure, left front", "Tire LF", "222813", "726", "((A*256)+B)*0.344738", "72E",
            candidates = listOf(PidCandidate("222813", "7E0", "((A*256)+B)*0.344738")) + gmCandidates("225005", "2232A0", "22248E", "222813", "222A05", "223198")
        ),
        tirePressure(
            PidCatalog.TIRE_RF, "Tire pressure, right front", "Tire RF", "222814", "726", "((A*256)+B)*0.344738", "72E",
            candidates = listOf(PidCandidate("222814", "7E0", "((A*256)+B)*0.344738")) + gmCandidates("225006", "2232A1", "22248F", "222814", "222A06", "223199")
        ),
        tirePressure(
            PidCatalog.TIRE_RR, "Tire pressure, right rear", "Tire RR", "222815", "726", "((A*256)+B)*0.344738", "72E",
            candidates = listOf(PidCandidate("222815", "7E0", "((A*256)+B)*0.344738")) + gmCandidates("225007", "2232A3", "222491", "222815", "222A07", "22319B")
        ),
        tirePressure(
            PidCatalog.TIRE_LR, "Tire pressure, left rear", "Tire LR", "222816", "726", "((A*256)+B)*0.344738", "72E",
            candidates = listOf(PidCandidate("222816", "7E0", "((A*256)+B)*0.344738")) + gmCandidates("225008", "2232A2", "222490", "222816", "222A08", "22319A")
        )
    )

    private fun mfrTirePids(header: String, lf: String, rf: String, rr: String, lr: String, formula: String = "tpms(A)", rx: String? = Elm327.receiveHeaderFor(header)): List<PidDefinition> = listOf(
        tirePressure(PidCatalog.TIRE_LF, "Tire pressure, left front", "Tire LF", lf, header, formula, rx, gmCandidates("225005", "2232A0", "22248E", "222813", "222A05", "223198")),
        tirePressure(PidCatalog.TIRE_RF, "Tire pressure, right front", "Tire RF", rf, header, formula, rx, gmCandidates("225006", "2232A1", "22248F", "222814", "222A06", "223199")),
        tirePressure(PidCatalog.TIRE_RR, "Tire pressure, right rear", "Tire RR", rr, header, formula, rx, gmCandidates("225007", "2232A3", "222491", "222815", "222A07", "22319B")),
        tirePressure(PidCatalog.TIRE_LR, "Tire pressure, left rear", "Tire LR", lr, header, formula, rx, gmCandidates("225008", "2232A2", "222490", "222816", "222A08", "22319A"))
    )

    val generic = ManufacturerProfile(
        id = "generic",
        name = "Generic OBD-II",
        wmiPrefixes = emptyList(),
        ecus = listOf(ENGINE, TRANS_7E1),
        pids = gmTirePids,
        notes = "Standard emissions data plus multi-module TPMS probing."
    )

    val all: List<ManufacturerProfile> = listOf(
        generic,
        ManufacturerProfile(
            id = "gm",
            name = "GM (Chevrolet, GMC, Buick, Cadillac)",
            wmiPrefixes = listOf("1G", "2G", "3G", "KL", "6G", "W06"),
            ecus = listOf(ENGINE, EcuDefinition("Transmission (TCM)", "7E2"), EcuDefinition("Body Control (BCM)", "241")),
            pids = listOf(
                extended(
                    "gm_trans_temp", "Transmission fluid temperature", "Trans temp",
                    "221940", "7E2", "A-40", Quantity.TEMPERATURE, -40.0, 150.0,
                    "Community-sourced (GM Mode 22). Some models answer on the broadcast header instead of 7E2."
                )
            ) + gmTirePids,
            notes = "GM transmissions (6L80/8L90/10L80 and others) usually answer on header 7E2; BCM TPMS answers on 241 -> 641."
        ),
        ManufacturerProfile(
            id = "ford",
            name = "Ford / Lincoln",
            wmiPrefixes = listOf("1F", "2F", "3F", "1L", "5L", "MAJ", "WF0", "NM0", "1ZV", "6FP"),
            ecus = listOf(ENGINE, TRANS_7E1, EcuDefinition("Body Control (BCM)", "726")),
            pids = listOf(
                extended(
                    "ford_trans_temp", "Transmission fluid temperature", "Trans temp",
                    "221E1C", "7E1", "((signed(A)*256)+B)/16", Quantity.TEMPERATURE, -40.0, 150.0,
                    "Community-sourced; the formula varies by transmission. Check it against a cold-start reading."
                )
            ) + fordTirePids,
            notes = "Ford PCM on 7E0, TCM on 7E1, BCM TPMS on 726 -> 72E."
        ),
        ManufacturerProfile(
            id = "toyota",
            name = "Toyota / Lexus",
            wmiPrefixes = listOf("JT", "4T", "5T", "2T", "6T1", "MR0", "NMT", "SB1"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = mfrTirePids("750", "221A10", "221A11", "221A13", "221A12"),
            notes = "ATF temperature PIDs (e.g. 2182 on 7E1) use different formulas by model year. Add yours under PIDs, Custom."
        ),
        ManufacturerProfile(
            id = "stellantis",
            name = "Chrysler / Dodge / Jeep / Ram",
            wmiPrefixes = listOf("1C", "2C", "3C", "1J", "3D", "ZAC", "ZFA"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = mfrTirePids("745", "223010", "223011", "223013", "223012")
        ),
        ManufacturerProfile(
            id = "honda",
            name = "Honda / Acura",
            wmiPrefixes = listOf("JH", "1H", "2H", "5J", "19X", "5FN", "7FA", "SHH"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = mfrTirePids("750", "222010", "222011", "222013", "222012")
        ),
        ManufacturerProfile(
            id = "nissan",
            name = "Nissan / Infiniti",
            wmiPrefixes = listOf("JN", "1N", "3N", "5N1", "5N3", "SJN"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = mfrTirePids("745", "222401", "222402", "222404", "222403", "A*1.373", "765")
        ),
        ManufacturerProfile(
            id = "hyundai",
            name = "Hyundai / Kia / Genesis",
            wmiPrefixes = listOf("KM", "5NP", "KN", "5XY", "5XX", "3KP", "U5Y"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = mfrTirePids("770", "220101", "220102", "220104", "220103", "A*1.373")
        ),
        ManufacturerProfile(
            id = "vag",
            name = "Volkswagen / Audi / Škoda / SEAT",
            wmiPrefixes = listOf("WVW", "WV1", "WV2", "WAU", "WA1", "3VW", "1VW", "TMB", "VSS", "WUA"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = mfrTirePids("760", "221821", "221822", "221824", "221823")
        ),
        ManufacturerProfile(
            id = "bmw",
            name = "BMW / MINI",
            wmiPrefixes = listOf("WBA", "WBS", "WBY", "5UX", "5YM", "WMW", "4US"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = mfrTirePids("7E0", "224301", "224302", "224304", "224303"),
            notes = "BMW exposes most data through proprietary diagnostics; generic PIDs are limited."
        ),
        ManufacturerProfile(
            id = "mercedes",
            name = "Mercedes-Benz",
            wmiPrefixes = listOf("WDD", "WDB", "WDC", "WDF", "W1K", "W1N", "W1V", "4JG", "55S"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = mfrTirePids("753", "222110", "222111", "222113", "222112")
        ),
        ManufacturerProfile(
            id = "subaru",
            name = "Subaru",
            wmiPrefixes = listOf("JF", "4S3", "4S4", "4S6"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = mfrTirePids("750", "221120", "221121", "221123", "221122")
        ),
        ManufacturerProfile(
            id = "mazda",
            name = "Mazda",
            wmiPrefixes = listOf("JM", "3MZ", "4F2"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = fordTirePids
        )
    )

    fun byId(id: String): ManufacturerProfile? = all.firstOrNull { it.id == id }

    /** Longest matching WMI prefix wins (e.g. 5NP is Hyundai, 5N1 is Nissan). */
    fun detect(vin: String?): ManufacturerProfile? {
        val code = vin?.uppercase() ?: return null
        return all.flatMap { profile -> profile.wmiPrefixes.map { it to profile } }
            .filter { (prefix, _) -> code.startsWith(prefix) }
            .maxByOrNull { (prefix, _) -> prefix.length }
            ?.second
    }
}
