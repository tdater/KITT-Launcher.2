package com.aiconsole.kittlauncher2.browser

/**
 * What the browser does when Android's audio focus changes while a web page is playing.
 * android.media.AudioManager focus codes are inlined so this stays plain Kotlin.
 */
object MediaFocus {

    const val GAIN = 1
    const val LOSS = -1
    const val LOSS_TRANSIENT = -2
    const val LOSS_TRANSIENT_CAN_DUCK = -3

    enum class Action {
        /** Another app took over for good (e.g. play pressed in YouTube Music): pause. */
        Pause,

        /** A short interruption (a call, another app's voice prompt): pause, resume on [GAIN]. */
        PauseUntilGain,

        /** Play again what [PauseUntilGain] paused. */
        Resume,

        /** Ducking (KITT speaking, navigation prompts): Android lowers the volume by itself. */
        Nothing
    }

    fun onChange(change: Int): Action = when (change) {
        LOSS -> Action.Pause
        LOSS_TRANSIENT -> Action.PauseUntilGain
        GAIN -> Action.Resume
        else -> Action.Nothing
    }
}
