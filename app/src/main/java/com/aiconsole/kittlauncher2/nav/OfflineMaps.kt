package com.aiconsole.kittlauncher2.nav

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import java.io.File
import java.io.IOException

/**
 * Offline map files for the home map. OpenStreetMap's tile servers don't allow downloading whole
 * areas, so KITT can't fetch a region itself: what you've driven past stays in the map's cache,
 * and for dead zones you can add a raster map file (MBTiles, osmdroid SQLite or GEMF, made with a
 * tool like MOBAC or downloaded for your region). Files go in the map library's base folder,
 * where its archive reader looks first, before the internet; they apply from the next start.
 */
object OfflineMaps {

    /** osmdroid reads archives from its base folder (set when the map first loads). */
    private fun folder(context: Context): File {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        return Configuration.getInstance().osmdroidBasePath.apply { mkdirs() }
    }

    fun files(context: Context): List<File> =
        folder(context).listFiles().orEmpty().filter { it.isFile && OfflineMapRules.isMapFile(it.name) }.sortedBy { it.name.lowercase() }

    /** Copies a picked file in; returns its name. */
    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val clean = OfflineMapRules.safeName(name) ?: throw IOException("Pick an .mbtiles, .sqlite or .gemf map file")
        val target = File(folder(context), clean)
        val part = File(target.path + ".part")
        try {
            resolver.openInputStream(uri)?.use { input -> part.outputStream().use { input.copyTo(it) } }
                ?: throw IOException("Couldn't read that file")
            if (!part.renameTo(target)) throw IOException("Couldn't save the map file")
        } finally {
            part.delete()
        }
        clean
    }

    fun remove(file: File) {
        file.delete()
    }
}

/** Pure rules for [OfflineMaps] (unit-tested). */
object OfflineMapRules {
    private val EXTENSIONS = listOf("mbtiles", "sqlite", "gemf")

    fun isMapFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** A plain file name with a map extension, or null (no folders, nothing hidden). */
    fun safeName(name: String): String? {
        val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
        if (base.isEmpty() || base.startsWith(".") || !isMapFile(base)) return null
        return base.replace(Regex("[^A-Za-z0-9._ -]"), "_")
    }
}
