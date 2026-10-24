package com.aiconsole.kittlauncher2.assistant

import kotlin.math.roundToInt

/** Pure helpers for playing synthesized voice clips ([OnDeviceVoice.play]). */
object VoicePcm {

    /** Float samples (-1..1, clipped) to 16-bit PCM, which every Android audio stack plays. */
    fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { i ->
        (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
    }

    /** True once the play position has stood still for [limitMs] (the sink stopped taking audio). */
    class StallWatch(private val limitMs: Long) {
        private var lastPosition = -1
        private var since = 0L

        fun stalled(position: Int, now: Long): Boolean {
            if (position != lastPosition) {
                lastPosition = position
                since = now
                return false
            }
            return now - since >= limitMs
        }
    }
}
