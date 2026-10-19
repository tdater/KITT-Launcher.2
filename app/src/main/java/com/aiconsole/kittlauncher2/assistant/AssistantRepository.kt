package com.aiconsole.kittlauncher2.assistant

import com.aiconsole.kittlauncher2.ui.theme.ThemeState

import android.database.SQLException
import com.aiconsole.kittlauncher2.data.settings.AiProvider
import com.aiconsole.kittlauncher2.data.settings.KittSettings
import com.aiconsole.kittlauncher2.data.trip.TripMetricDao
import com.aiconsole.kittlauncher2.data.trip.TripMetricRecorder
import java.util.concurrent.TimeUnit

/**
 * Builds KITT's vehicle context (live OBD data, fault codes, 30-day baseline) and routes the
 * question to the configured provider. Diagnostic questions go to the more capable model.
 */
class AssistantRepository(
    private val settings: KittSettings,
    private val gemini: GeminiAssistantClient,
    private val perplexity: PerplexityAssistantClient,
    private val dao: TripMetricDao,
    private val recorder: TripMetricRecorder,
    private val vehicleContext: () -> VehicleContext?,
    /** The car right now, for answers with no signal (null in tests). */
    private val offlineFacts: (() -> OfflineFacts)? = null
) {
    suspend fun ask(prompt: String, history: List<ChatTurn>, diagnostic: Boolean = false): AssistantResult {
        val useDiagnosticModel = diagnostic || KittPersona.isDiagnostic(prompt)
        val context = VehicleContextFormatter.vehicleContext(vehicleContext(), baselineSummary())
        val driverName = settings.driverName.value

        val online = askOnline(prompt, history, useDiagnosticModel, context, driverName)
        if (online !is AssistantResult.Failure) return online
        // No signal, no key or the service failed: the common car questions are answered from the data.
        val facts = offlineFacts?.invoke() ?: return online
        OfflineAnswers.answer(prompt, facts)?.let { return AssistantResult.Answer(it, engine = OFFLINE_ENGINE) }
        return if (online.offline) AssistantResult.Failure(OfflineAnswers.NEEDS_SIGNAL, offline = true) else online
    }

    private suspend fun askOnline(
        prompt: String,
        history: List<ChatTurn>,
        useDiagnosticModel: Boolean,
        context: String,
        driverName: String
    ): AssistantResult {
        return when (settings.provider.value) {
            AiProvider.Gemini -> {
                val modelPref = if (useDiagnosticModel) settings.diagnosticModel else settings.chatModel
                val requested = modelPref.value
                val result = gemini.generateAutomotiveResponse(
                    apiKey = settings.geminiKey,
                    model = requested,
                    userPrompt = prompt,
                    history = history,
                    driverName = driverName,
                    vehicleContext = context,
                    webSearch = settings.webSearch.value,
                    persona = settings.persona.value,
                    character = filmCharacter()
                )
                // A retired model was swapped for a live one: remember it.
                if (result is AssistantResult.Answer && result.engine.isNotEmpty() && result.engine != requested) {
                    modelPref.set(result.engine)
                }
                result
            }
            AiProvider.Perplexity -> perplexity.generate(
                apiKey = settings.perplexityKey,
                preset = if (useDiagnosticModel) settings.perplexityDiagnosticPreset.value else settings.perplexityChatPreset.value,
                userPrompt = prompt,
                history = history,
                driverName = driverName,
                vehicleContext = context,
                persona = settings.persona.value,
                webGrounded = PerplexityRouting.needsWeb(prompt, useDiagnosticModel, settings.webSearch.value),
                localModel = settings.perplexityLocalModel.value,
                character = filmCharacter()
            )
        }
    }

    private suspend fun baselineSummary(): String = try {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(BASELINE_DAYS)
        VehicleContextFormatter.baseline(dao.baselineSince(since), recorder.live.value)
    } catch (_: SQLException) {
        "Trip history unavailable."
    } catch (_: IllegalStateException) {
        "Trip history unavailable."
    }

    private companion object {
        const val BASELINE_DAYS = 30L
        const val OFFLINE_ENGINE = "KITT offline (from the car's data)"
    }

    /** The film theme's character, when one is on and the driver wants it. */
    private fun filmCharacter(): FilmCharacter? =
        if (settings.filmCharacters.value) ThemeState.filmCharacter else null
}
