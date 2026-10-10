package com.aiconsole.kittlauncher2.assistant

import android.content.Context
import android.util.Base64
import com.aiconsole.kittlauncher2.data.settings.KittSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Synthesises one chunk of speech with Gemini and returns a playable WAV file.
 * Results are cached on disk, so alerts and stock phrases play instantly and work offline.
 */
class GeminiSpeechClient(private val context: Context, private val settings: KittSettings) {

    private val http = assistantHttpClient()
    private val cacheDir = File(context.cacheDir, "kitt_voice")

    @Volatile
    private var modelIndex = 0

    /** Throws [IOException] when there's no key, no network, or the API refuses. */
    suspend fun synthesize(text: String, voice: String, style: String): File = withContext(Dispatchers.IO) {
        val file = File(cacheDir, GeminiSpeechProtocol.cacheKey(voice, style, text) + ".wav")
        if (file.length() > WAV_HEADER_BYTES) {
            file.setLastModified(System.currentTimeMillis())
            return@withContext file
        }
        val key = settings.geminiKey
        if (key.isEmpty()) throw IOException("No Gemini key")
        if (!isOnline(context)) throw IOException("Offline")

        while (modelIndex < GeminiSpeechProtocol.MODELS.size) {
            val model = GeminiSpeechProtocol.MODELS[modelIndex]
            val request = Request.Builder()
                .url(INTERACTIONS_URL)
                .header("x-goog-api-key", key)
                .post(GeminiSpeechProtocol.requestBody(model, text, voice, style).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val (code, body) = http.newCall(request).await().use { it.code to it.body.string() }
            if (code == 404) {
                modelIndex++ // Retired or not yet available on this key: try the next model.
                continue
            }
            if (code !in 200..299) throw IOException("Gemini speech HTTP $code: ${errorMessage(body)}")
            val audio = audioFrom(body)
            cacheDir.mkdirs()
            val tmp = File(cacheDir, file.name + ".tmp")
            tmp.writeBytes(if (GeminiSpeechProtocol.isWav(audio)) audio else GeminiSpeechProtocol.wavFromPcm(audio))
            if (!tmp.renameTo(file)) throw IOException("Couldn't cache speech")
            prune()
            return@withContext file
        }
        throw IOException("No Gemini speech model available")
    }

    /** Last audio part of the model's output steps. */
    private fun audioFrom(body: String): ByteArray = try {
        val steps = JSONObject(body).getJSONArray("steps")
        var data: String? = null
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            if (step.optString("type") != "model_output") continue
            val content = step.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.getJSONObject(j)
                if (part.optString("type") == "audio") data = part.optString("data").ifEmpty { data }
            }
        }
        Base64.decode(data ?: throw IOException("No audio in Gemini response"), Base64.DEFAULT)
    } catch (e: JSONException) {
        throw IOException("Unreadable Gemini speech response", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("Corrupt audio in Gemini response", e)
    }

    private fun errorMessage(body: String): String =
        runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrDefault(body.take(200))

    private fun prune() {
        val files = cacheDir.listFiles { f -> f.extension == "wav" }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        files.forEachIndexed { index, f ->
            total += f.length()
            if (index >= MAX_CACHED_FILES || total > MAX_CACHE_BYTES) f.delete()
        }
    }

    private companion object {
        const val INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
        const val WAV_HEADER_BYTES = 44L
        const val MAX_CACHED_FILES = 400
        const val MAX_CACHE_BYTES = 40L * 1024 * 1024
    }
}
