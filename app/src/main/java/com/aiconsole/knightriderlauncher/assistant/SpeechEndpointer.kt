package com.aiconsole.knightriderlauncher.assistant

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Decides when the driver has finished speaking, from per-frame loudness. Adapts to road noise:
 * the first frames set the noise floor, speech is anything well above it, and a stretch of
 * near-floor audio after speech ends the recording. Plain Kotlin.
 */
class SpeechEndpointer(
    private val frameMs: Int = 20,
    private val calibrationMs: Int = 300,
    private val speechMarginDb: Double = 10.0,
    private val silenceMarginDb: Double = 6.0,
    private val endSilenceMs: Int = 1_200,
    private val noSpeechTimeoutMs: Int = 6_000,
    private val maxMs: Int = 15_000
) {
    enum class Decision { Continue, Done, NoSpeech }

    private var elapsed = 0
    private var floorDb = Double.NaN
    private var calibrationSum = 0.0
    private var calibrationFrames = 0
    private var heardSpeech = false
    private var silentMs = 0

    val spoke: Boolean get() = heardSpeech

    /** Loudness 0..1 for the voice-level display. */
    var level = 0f
        private set

    fun feed(levelDb: Double): Decision {
        elapsed += frameMs
        level = ((levelDb + 60) / 50).toFloat().coerceIn(0f, 1f)
        if (elapsed <= calibrationMs) {
            calibrationSum += levelDb
            calibrationFrames++
            floorDb = calibrationSum / calibrationFrames
            return Decision.Continue
        }
        val floor = if (floorDb.isNaN()) -60.0 else floorDb
        if (levelDb > floor + speechMarginDb) {
            heardSpeech = true
            silentMs = 0
        } else if (heardSpeech && levelDb < floor + silenceMarginDb) {
            silentMs += frameMs
        }
        return when {
            heardSpeech && silentMs >= endSilenceMs -> Decision.Done
            !heardSpeech && elapsed >= noSpeechTimeoutMs -> Decision.NoSpeech
            elapsed >= maxMs -> if (heardSpeech) Decision.Done else Decision.NoSpeech
            else -> Decision.Continue
        }
    }

    companion object {
        /** RMS level of 16-bit PCM samples in dBFS (silence clamps to -90). */
        fun levelDb(samples: ShortArray, count: Int): Double {
            if (count <= 0) return -90.0
            var sum = 0.0
            for (i in 0 until count) {
                val s = samples[i].toDouble()
                sum += s * s
            }
            val rms = sqrt(sum / count)
            return if (rms < 1) -90.0 else 20 * log10(rms / 32768.0)
        }
    }
}
