package com.aiconsole.knightriderlauncher.assistant

import android.content.Context
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Perplexity Agent API client (POST /v1/agent), the replacement for the retired Sonar
 * chat-completions endpoint. Every answer is web-grounded; presets trade speed for depth:
 * fast < low < medium < high.
 */
class PerplexityAssistantClient(
    context: Context,
    private val http: OkHttpClient = assistantHttpClient()
) {
    private val appContext = context.applicationContext

    suspend fun generate(
        apiKey: String,
        preset: String,
        userPrompt: String,
        history: List<ChatTurn>,
        driverName: String,
        vehicleContext: String,
        persona: PersonaStyle
    ): AssistantResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AssistantResult.Failure("No Perplexity API key yet. Add one under KITT CONFIG, AI CORE.")
        }
        if (!isOnline(appContext)) return@withContext AssistantResult.Failure(OFFLINE_MESSAGE, offline = true)

        // History goes into the instructions: the Agent API input is kept to the single current message.
        val recent = history.takeLast(6).joinToString("\n") { (if (it.fromUser) "Driver: " else "KITT: ") + it.text }
        val instructions = KittPersona.systemInstruction(persona, driverName, webSearch = true, vehicleContext) +
            if (recent.isNotEmpty()) "\n\nRECENT CONVERSATION:\n$recent" else ""

        val body = JSONObject()
            .put("preset", preset)
            .put("instructions", instructions)
            .put(
                "input",
                JSONArray().put(
                    JSONObject().put("type", "message").put("role", "user").put("content", userPrompt)
                )
            )
            .put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
            .toString()

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            http.newCall(request).await().use { response ->
                val text = response.body.string()
                if (response.isSuccessful) parse(text, preset) else failure(response.code, text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: UnknownHostException) {
            AssistantResult.Failure(OFFLINE_MESSAGE, offline = true)
        } catch (_: ConnectException) {
            AssistantResult.Failure(OFFLINE_MESSAGE, offline = true)
        } catch (_: SocketTimeoutException) {
            AssistantResult.Failure("Connection timed out. Signal may be weak; try again shortly.", offline = true)
        } catch (_: IOException) {
            AssistantResult.Failure("Connection dropped before the answer arrived. Try again.", offline = true)
        } catch (_: JSONException) {
            AssistantResult.Failure("Received an unreadable response from Perplexity.")
        }
    }

    private fun parse(body: String, preset: String): AssistantResult {
        val root = JSONObject(body)
        val text = toGlanceable(root.optString("output_text"))
        if (text.isBlank()) return AssistantResult.Failure("No answer was returned.")
        val output = root.optJSONArray("output") ?: JSONArray()
        val sources = (0 until output.length())
            .mapNotNull { output.optJSONObject(it) }
            .filter { it.optString("type") == "search_results" }
            .flatMap { item ->
                val results = item.optJSONArray("results") ?: JSONArray()
                (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            }
            .map { Source(title = it.optString("title").ifEmpty { it.optString("url") }, uri = it.optString("url")) }
            .filter { it.title.isNotEmpty() }
            .distinctBy { it.uri }
        return AssistantResult.Answer(text, sources, engine = "perplexity · $preset")
    }

    private fun failure(code: Int, body: String): AssistantResult.Failure {
        val message = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message") ?: json.optString("detail")
        }.getOrNull().orEmpty()
        return AssistantResult.Failure(
            when (code) {
                401 -> "Perplexity rejected the API key. Check it under KITT CONFIG, AI CORE."
                402 -> "Perplexity API credit is used up. Add credit in your Perplexity API settings."
                429 -> "Perplexity rate limit reached. Try again in a minute."
                in 500..599 -> "Perplexity is temporarily unavailable."
                else -> if (message.isNotEmpty()) "Perplexity error: $message" else "Perplexity request failed ($code)."
            }
        )
    }

    private companion object {
        const val ENDPOINT = "https://api.perplexity.ai/v1/agent"
    }
}
