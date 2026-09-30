package com.aiconsole.knightriderlauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * One-file backup of the launcher's setup: settings, gauges, custom PIDs and userscripts.
 * API keys are deliberately excluded: they're encrypted with a key that never leaves this
 * device's keystore, so they couldn't be restored anyway and shouldn't sit in a file.
 */
class LauncherBackup(private val context: Context) {

    private val scriptsDir = File(context.filesDir, "userscripts")

    fun export(out: OutputStream) {
        val prefs = JSONObject()
        PREFS.forEach { name ->
            val values = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                val entry = when (value) {
                    is Boolean -> JSONObject().put("t", "b").put("v", value)
                    is Int -> JSONObject().put("t", "i").put("v", value)
                    is Long -> JSONObject().put("t", "l").put("v", value)
                    is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                    is String -> JSONObject().put("t", "s").put("v", value)
                    is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.filterIsInstance<String>()))
                    else -> null
                }
                entry?.let { values.put(key, it) }
            }
            prefs.put(name, values)
        }
        val scripts = JSONObject()
        scriptsDir.listFiles { f -> f.name.endsWith(".user.js") }?.forEach { scripts.put(it.name, it.readText()) }
        val root = JSONObject().put("format", FORMAT).put("version", 1).put("prefs", prefs).put("userscripts", scripts)
        out.write(root.toString(2).toByteArray(Charsets.UTF_8))
    }

    /** Replaces the current setup with the backup's. Throws [IOException] on a bad file. */
    fun import(input: InputStream) {
        val root = try {
            JSONObject(input.readBytes().toString(Charsets.UTF_8))
        } catch (e: JSONException) {
            throw IOException("Not a KITT backup file", e)
        }
        if (root.optString("format") != FORMAT) throw IOException("Not a KITT backup file")
        val prefs = root.getJSONObject("prefs")
        PREFS.filter(prefs::has).forEach { name ->
            val values = prefs.getJSONObject(name)
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.keys().forEach { key ->
                val entry = values.getJSONObject(key)
                when (entry.getString("t")) {
                    "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                    "i" -> editor.putInt(key, entry.getInt("v"))
                    "l" -> editor.putLong(key, entry.getLong("v"))
                    "f" -> editor.putFloat(key, entry.getDouble("v").toFloat())
                    "s" -> editor.putString(key, entry.getString("v"))
                    "ss" -> {
                        val array = entry.getJSONArray("v")
                        editor.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                    }
                }
            }
            if (!editor.commit()) throw IOException("Couldn't write $name")
        }
        root.optJSONObject("userscripts")?.let { scripts ->
            scriptsDir.mkdirs()
            scripts.keys().forEach { fileName ->
                // Only plain file names; never paths from the backup.
                if (fileName.endsWith(".user.js") && '/' !in fileName && '\\' !in fileName) {
                    File(scriptsDir, fileName).writeText(scripts.getString(fileName))
                }
            }
        }
    }

    private companion object {
        const val FORMAT = "kitt-launcher-backup"
        val PREFS = listOf("kitt_settings", "kitt_gauges", "kitt_custom_pids", "userscripts")
    }
}
