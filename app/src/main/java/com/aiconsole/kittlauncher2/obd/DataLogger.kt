package com.aiconsole.kittlauncher2.obd

import android.content.Context
import android.util.Log
import com.aiconsole.kittlauncher2.data.settings.KittSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Records live sensor data to CSV while the driver keeps using the launcher. Columns are the
 * sensors reporting when recording starts; they stay polled until it stops. Files live in app
 * storage; the Data tab exports them wherever the driver chooses.
 */
class DataLogger(
    context: Context,
    private val obd: ObdManager,
    private val settings: KittSettings,
    private val scope: CoroutineScope
) {
    data class Session(val file: File, val columns: Int, val startedAt: Long, val rows: Int)

    val directory = File(context.filesDir, "logs")

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _files = MutableStateFlow(listFiles())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private var job: Job? = null

    /** Starts recording every sensor that currently has a fresh reading. Returns why it can't, or null. */
    fun start(): String? {
        if (_session.value != null) return null
        if (obd.state.value !is ObdState.Connected) return "Connect to the vehicle first."
        val now = System.currentTimeMillis()
        val defs = obd.definitions.value
        val columns = obd.readings.value
            .filter { (id, reading) -> now - reading.timestamp < FRESH_MS && id in defs }
            .keys.sorted()
            .map { defs.getValue(it) }
        if (columns.isEmpty()) return "No live sensor data yet. Wait for the gauges to update."
        val metric = settings.metricUnits.value

        directory.mkdirs()
        val file = File(directory, CsvLog.fileName(now))
        obd.watch(WATCH_KEY, columns.map { it.id }.toSet())
        _session.value = Session(file, columns.size, now, 0)
        job = scope.launch(Dispatchers.IO) {
            try {
                file.bufferedWriter().use { out ->
                    out.appendLine(CsvLog.header(columns.map { CsvLog.Column(it.name, it.unit(metric)) }))
                    var rows = 0
                    while (isActive) {
                        val t = System.currentTimeMillis()
                        val readings = obd.readings.value
                        val values = columns.map { def ->
                            readings[def.id]?.takeIf { t - it.timestamp < FRESH_MS }?.let { def.displayValue(it.value, metric) }
                        }
                        out.appendLine(CsvLog.row(t, now, values))
                        rows++
                        if (rows % FLUSH_EVERY == 0) out.flush()
                        _session.value = _session.value?.copy(rows = rows)
                        delay(SAMPLE_MS)
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Logging stopped: couldn't write ${file.name}", e)
                finish()
            }
        }
        return null
    }

    fun stop() {
        job?.cancel()
        job = null
        finish()
    }

    fun delete(file: File) {
        if (file.parentFile == directory) file.delete()
        _files.value = listFiles()
    }

    private fun finish() {
        obd.unwatch(WATCH_KEY)
        _session.value = null
        _files.value = listFiles()
    }

    private fun listFiles(): List<File> =
        directory.listFiles { f -> f.extension == "csv" }?.sortedByDescending { it.lastModified() }.orEmpty()

    private companion object {
        const val TAG = "DataLogger"
        const val WATCH_KEY = "data_logger"
        const val SAMPLE_MS = 500L
        const val FRESH_MS = 5_000L
        const val FLUSH_EVERY = 20
    }
}
