package com.aiconsole.knightriderlauncher.data

import android.content.Context
import android.os.Build
import android.os.Process
import com.aiconsole.knightriderlauncher.obd.ObdManager
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash capture and the "Report a problem" file: device and app details, recent crashes, the
 * OBD connection log, non-secret settings and this app's own recent log output. Built for a
 * head unit where adb isn't available. API keys live in a separate encrypted store and are
 * never included.
 */
object ProblemReport {

    private const val MAX_CRASHES = 5

    private fun crashDir(context: Context) = File(context.filesDir, "crashes")

    /** Saves uncaught exceptions to a file before the process dies, then lets Android handle them. */
    fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val dir = crashDir(appContext).apply { mkdirs() }
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                File(dir, "crash-${System.currentTimeMillis()}.txt").writeText("Thread: ${thread.name}\n$trace")
                dir.listFiles()?.sortedByDescending { it.name }?.drop(MAX_CRASHES)?.forEach { it.delete() }
            } catch (_: Throwable) {
                // Never let crash logging hide the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun write(context: Context, obd: ObdManager, out: OutputStream) {
        val text = buildString {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
            appendLine("KITT LAUNCHER PROBLEM REPORT")
            appendLine("Created: ${stamp.format(Date())}")
            val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            appendLine("App: ${context.packageName} ${pkg?.versionName ?: "?"}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Build: ${Build.FINGERPRINT}")
            appendLine()

            appendLine("== OBD ==")
            appendLine("State: ${obd.state.value}")
            obd.linkEvents.value.takeLast(80).forEach { e ->
                appendLine("${stamp.format(Date(e.timestamp))} ${e.layer.label} ${if (e.ok) "ok" else "FAIL"}: ${e.message}")
            }
            appendLine()

            appendLine("== Recent crashes ==")
            val crashes = crashDir(context).listFiles()?.sortedByDescending { it.name }.orEmpty()
            if (crashes.isEmpty()) appendLine("None recorded.")
            crashes.forEach { f ->
                val at = f.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull()
                appendLine("-- ${at?.let { stamp.format(Date(it)) } ?: f.name}")
                appendLine(f.readText().take(8_000))
            }
            appendLine()

            appendLine("== Settings (no API keys) ==")
            context.getSharedPreferences("kitt_settings", Context.MODE_PRIVATE).all.toSortedMap().forEach { (k, v) ->
                appendLine("$k = ${v.toString().replace('\n', ' ').take(200)}")
            }
            appendLine()

            appendLine("== App log (this app only) ==")
            append(appLog())
        }
        out.write(text.toByteArray(Charsets.UTF_8))
    }

    /** This process's recent logcat output; apps may read their own logs without extra permission. */
    private fun appLog(): String = try {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "3000", "--pid=${Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() // logcat -d exits once it has dumped; the output is already read.
        output.ifBlank { "(empty)\n" }
    } catch (e: IOException) {
        "(couldn't read the log: ${e.message})\n"
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        "(interrupted)\n"
    }
}
