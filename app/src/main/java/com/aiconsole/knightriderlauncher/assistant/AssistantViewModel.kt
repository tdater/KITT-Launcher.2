package com.aiconsole.knightriderlauncher.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiconsole.knightriderlauncher.KittApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One question and its answer; [result] is null while waiting. */
data class Exchange(val id: Long, val prompt: String, val result: AssistantResult?)

/** Activity-scoped, so the conversation survives closing and reopening the overlay. */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KittApplication
    private val repository = app.assistantRepository
    private val voice = app.kittVoice
    val speech = SpeechToTextHelper(application)

    val speaking: StateFlow<Boolean> = voice.speaking
    val voiceReply: StateFlow<Boolean> = app.settings.voiceReply.state

    private val _exchanges = MutableStateFlow<List<Exchange>>(emptyList())
    val exchanges: StateFlow<List<Exchange>> = _exchanges.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private var nextId = 0L

    init {
        // A finished voice query is sent automatically: fully hands-free.
        viewModelScope.launch {
            speech.state.collect { state ->
                if (state is SpeechState.Result) {
                    speech.reset()
                    ask(state.text)
                }
            }
        }
        // Questions queued from other screens, e.g. "Ask KITT" on a fault code.
        viewModelScope.launch {
            app.assistantInbox.filterNotNull().collect { prompt ->
                app.assistantInbox.value = null
                ask(prompt.text, prompt.diagnostic)
            }
        }
    }

    fun ask(prompt: String, diagnostic: Boolean = false) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _thinking.value) return
        voice.stop()
        val history = conversationHistory()
        val id = nextId++
        _exchanges.update { (it + Exchange(id, trimmed, null)).takeLast(MAX_EXCHANGES) }
        _thinking.value = true
        viewModelScope.launch {
            val result = try {
                repository.ask(trimmed, history, diagnostic)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AssistantResult.Failure("Assistant error: ${e.message ?: e.javaClass.simpleName}")
            }
            _exchanges.update { list -> list.map { if (it.id == id) it.copy(result = result) else it } }
            _thinking.value = false
            if (voiceReply.value) {
                voice.speak(
                    when (result) {
                        is AssistantResult.Answer -> result.text
                        is AssistantResult.Failure -> result.message
                    }
                )
            }
        }
    }

    /** Last few answered turns, so follow-ups like "and the other side?" make sense. */
    private fun conversationHistory(): List<ChatTurn> =
        _exchanges.value
            .filter { it.result is AssistantResult.Answer }
            .takeLast(HISTORY_TURNS)
            .flatMap {
                listOf(
                    ChatTurn(fromUser = true, text = it.prompt),
                    ChatTurn(fromUser = false, text = (it.result as AssistantResult.Answer).text)
                )
            }

    fun startListening() {
        voice.stop()
        speech.start()
    }

    fun stopListening() = speech.stop()

    fun toggleVoiceReply() {
        val enabled = !voiceReply.value
        app.settings.voiceReply.set(enabled)
        if (!enabled) voice.stop()
    }

    fun clearConversation() {
        _exchanges.value = emptyList()
    }

    /** Called when the overlay closes: stop the mic and any speech in progress. */
    fun silence() {
        speech.cancel()
        voice.stop()
    }

    override fun onCleared() {
        speech.release()
    }

    private companion object {
        const val MAX_EXCHANGES = 20
        const val HISTORY_TURNS = 4
    }
}
