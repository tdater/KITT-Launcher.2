package com.aiconsole.kittlauncher2.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Optional short console "blip" on KITT button presses. Generated tones, no audio files;
 * off unless the driver turns it on in KITT Config.
 */
object UiSounds {
    @Volatile
    var enabled = false

    private val tone: ToneGenerator? by lazy {
        try {
            ToneGenerator(AudioManager.STREAM_SYSTEM, 40)
        } catch (e: RuntimeException) {
            Log.w("UiSounds", "No tone generator on this device", e)
            null
        }
    }

    fun click() {
        if (enabled) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
    }
}
