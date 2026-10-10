package com.aiconsole.kittlauncher2.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Steering-wheel / hardware button mapping. The activity feeds key presses in; while
 * [learning], the next press is captured for the settings screen instead of acted on.
 * Only works while the launcher is in front, and only for buttons the head unit sends as
 * ordinary key events (many route media keys straight to the playing app).
 */
object HardwareKeys {
    private val _learning = MutableStateFlow(false)
    val learning: StateFlow<Boolean> = _learning.asStateFlow()

    private val _learned = MutableStateFlow<Int?>(null)
    val learned: StateFlow<Int?> = _learned.asStateFlow()

    private val _actions = MutableSharedFlow<KeyAction>(extraBufferCapacity = 4)
    val actions: SharedFlow<KeyAction> = _actions.asSharedFlow()

    fun startLearning() {
        _learned.value = null
        _learning.value = true
    }

    fun cancelLearning() {
        _learning.value = false
    }

    /** Back (KeyEvent.KEYCODE_BACK) always keeps its normal meaning. */
    private const val BACK = 4

    /** Returns true when the key was consumed (learned or bound to an action). */
    fun onKeyUp(keyCode: Int, bindings: Map<Int, KeyAction>): Boolean {
        if (keyCode == BACK) return false
        if (_learning.value) {
            _learned.value = keyCode
            _learning.value = false
            return true
        }
        val action = bindings[keyCode] ?: return false
        _actions.tryEmit(action)
        return true
    }

    /** Swallow the matching key-down so the default handling doesn't also fire. */
    fun consumesDown(keyCode: Int, bindings: Map<Int, KeyAction>): Boolean = keyCode != BACK && (_learning.value || keyCode in bindings)
}
