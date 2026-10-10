package com.aiconsole.kittlauncher2.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Lowers other audio (music, video) while KITT speaks: holds "transient, may duck" audio focus
 * for the whole utterance, and tags KITT's speech as navigation guidance so head units mix it
 * over music the way they do turn-by-turn prompts.
 */
class AudioDucker(context: Context) {

    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val listener = AudioManager.OnAudioFocusChangeListener { }
    private var request: AudioFocusRequest? = null
    private var held = false

    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun duck() {
        if (held || audio == null) return
        held = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(listener)
                .build()
            request = r
            audio.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun release() {
        if (!held || audio == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let(audio::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(listener)
        }
        held = false
    }
}
