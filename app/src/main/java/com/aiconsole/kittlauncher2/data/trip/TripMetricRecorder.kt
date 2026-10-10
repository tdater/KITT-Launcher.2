package com.aiconsole.kittlauncher2.data.trip

import android.database.SQLException
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Latest live reading plus any active diagnostic trouble codes. */
data class VehicleTelemetry(
    val reading: TripMetricLog,
    val faultCodes: List<String> = emptyList()
)

/**
 * Entry point for OBD data. Call [record] from the OBD connection code for every decoded
 * frame: it updates [live] immediately and queues the frame for batched Room inserts on IO.
 * Nothing feeds it yet; wire in an ELM327/OBD source when one is added.
 */
class TripMetricRecorder(
    private val dao: TripMetricDao,
    scope: CoroutineScope,
    private val batchSize: Int = 50,
    private val flushIntervalMs: Long = 5_000
) {
    private val queue = Channel<TripMetricLog>(capacity = 2_048, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _live = MutableStateFlow<VehicleTelemetry?>(null)
    val live: StateFlow<VehicleTelemetry?> = _live.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val batch = ArrayList<TripMetricLog>(batchSize)
            while (true) {
                // Wait for the first frame, then collect until the batch fills or the interval ends.
                batch += queue.receive()
                val deadline = System.currentTimeMillis() + flushIntervalMs
                while (batch.size < batchSize) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) break
                    batch += withTimeoutOrNull(remaining) { queue.receive() } ?: break
                }
                flush(batch)
                batch.clear()
            }
        }
    }

    /** Non-blocking; safe to call from any thread. Invalid frames are dropped. */
    fun record(log: TripMetricLog, faultCodes: List<String> = _live.value?.faultCodes.orEmpty()) {
        if (!log.isValid()) return
        _live.value = VehicleTelemetry(log, faultCodes)
        queue.trySend(log)
    }

    private suspend fun flush(batch: List<TripMetricLog>) {
        try {
            dao.insertAll(batch.toList())
        } catch (e: SQLException) {
            // Losing one batch beats crashing the head unit; the live view is unaffected.
            Log.w(TAG, "Dropped ${batch.size} trip samples", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Dropped ${batch.size} trip samples", e)
        }
    }

    private companion object {
        const val TAG = "TripMetricRecorder"
    }
}
