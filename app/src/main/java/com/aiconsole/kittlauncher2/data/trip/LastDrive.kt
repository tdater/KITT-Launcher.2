package com.aiconsole.kittlauncher2.data.trip

import java.util.Locale

/** What KITT noticed by the end of a drive, for a one-line recap when the next drive starts. */
data class LastDrive(
    val endedAt: Long,
    val grade: String? = null,
    val distanceKm: Double? = null,
    /** Stored, pending and permanent codes the ECU still reported at the end. */
    val codes: List<String> = emptyList(),
    /** Battery / charging warnings from the last few minutes (BatteryHealth). */
    val battery: List<String> = emptyList()
)

/** Pure recap rules and storage (unit-tested). */
object LastDriveRules {

    /** A recap older than this is stale news (the car sat for a week). */
    const val MAX_AGE_MS = 7 * 24 * 3_600_000L
    private const val SEP = '\u001F'

    /** A link back this soon was a dropout mid-drive, not a new drive: no recap yet. */
    const val MIN_GAP_MS = 10 * 60_000L

    fun isStale(drive: LastDrive, now: Long): Boolean = now - drive.endedAt !in 0..MAX_AGE_MS

    fun recap(drive: LastDrive, now: Long, metric: Boolean): String? {
        if (isStale(drive, now) || now - drive.endedAt < MIN_GAP_MS) return null
        val parts = mutableListOf<String>()
        when (drive.codes.size) {
            0 -> Unit
            1 -> parts += "fault code ${drive.codes[0]} was still showing"
            else -> parts += "fault codes ${drive.codes.take(4).joinToString(", ")} were still showing"
        }
        drive.battery.firstOrNull()?.let { parts += it.trimEnd('.').replaceFirstChar { c -> c.lowercase(Locale.US) } }
        if (drive.grade != null && drive.distanceKm != null) {
            val dist = if (metric) "%.0f km".format(Locale.US, drive.distanceKm) else "%.0f miles".format(Locale.US, drive.distanceKm * 0.621371)
            parts += "you scored ${drive.grade} over $dist"
        }
        if (parts.isEmpty()) return null
        return "Last drive: " + parts.joinToString("; ") + "."
    }

    fun encode(drive: LastDrive): String = listOf(
        drive.endedAt.toString(),
        drive.grade.orEmpty(),
        drive.distanceKm?.let { "%.3f".format(Locale.US, it) }.orEmpty(),
        drive.codes.joinToString(","),
        drive.battery.joinToString("\u001E")
    ).joinToString(SEP.toString())

    fun decode(text: String): LastDrive? {
        val p = text.split(SEP)
        if (p.size != 5) return null
        return LastDrive(
            endedAt = p[0].toLongOrNull() ?: return null,
            grade = p[1].ifEmpty { null },
            distanceKm = p[2].toDoubleOrNull(),
            codes = p[3].split(',').filter { it.isNotBlank() },
            battery = p[4].split('\u001E').filter { it.isNotBlank() }
        )
    }
}
