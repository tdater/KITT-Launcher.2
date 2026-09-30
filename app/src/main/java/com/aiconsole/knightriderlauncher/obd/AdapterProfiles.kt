package com.aiconsole.knightriderlauncher.obd

/** Saved adapters, one per line: TYPE|address|name. Plain Kotlin. */
object AdapterProfiles {

    fun encode(configs: List<AdapterConfig>): String =
        configs.distinctBy { it.address }.joinToString("\n") { "${it.type.name}|${it.address}|${it.name.replace("\n", " ")}" }

    fun decode(text: String): List<AdapterConfig> = text.lines().mapNotNull { line ->
        val parts = line.split('|', limit = 3)
        if (parts.size != 3) return@mapNotNull null
        val type = runCatching { AdapterType.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
        val address = parts[1].trim().ifEmpty { return@mapNotNull null }
        AdapterConfig(type, address, parts[2].ifEmpty { address })
    }.distinctBy { it.address }
}
