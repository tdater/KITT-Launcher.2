package com.aiconsole.kittlauncher2.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import com.aiconsole.kittlauncher2.data.settings.KittSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * KITT's own speech input: records the microphone directly and has Gemini transcribe the clip.
 * It never involves the head unit's speech recognizer, so it can't trigger the manufacturer's
 * voice assistant (which on some units switches to the radio app).
 */
class GeminiTranscriber(private val settings: KittSettings) {

    private val http by lazy { assistantHttpClient() }

    @Volatile
    private var stopRequested = false

    /** Ends the recording early (the driver tapped the mic again); what was heard is kept. */
    fun requestStop() {
        stopRequested = true
    }

    /**
     * Records until the driver stops talking, reporting loudness 0..1 to [onLevel]. Returns 16 kHz
     * mono PCM, or null if nothing was said. Caller must hold RECORD_AUDIO.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(onLevel: (Float) -> Unit): ByteArray? = withContext(Dispatchers.IO) {
        stopRequested = false
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw IOException("Microphone unavailable")
        val recorder = AudioRecord(
            // Plain MIC by default: on some head units the voice-recognition source is routed
            // through the manufacturer's voice system, which switches to its own app.
            if (settings.voiceRecognitionMic.value) MediaRecorder.AudioSource.VOICE_RECOGNITION else MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, FRAME_SAMPLES * 4)
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IOException("Microphone is busy")
        }
        val endpointer = SpeechEndpointer(frameMs = FRAME_MS)
        val frame = ShortArray(FRAME_SAMPLES)
        val pcm = ByteArrayOutputStream()
        val bytes = ByteBuffer.allocate(FRAME_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)
        try {
            recorder.startRecording()
            while (isActive && !stopRequested) {
                val n = recorder.read(frame, 0, frame.size)
                if (n <= 0) throw IOException("Microphone read failed ($n)")
                bytes.clear()
                for (i in 0 until n) bytes.putShort(frame[i])
                pcm.write(bytes.array(), 0, n * 2)
                val decision = endpointer.feed(SpeechEndpointer.levelDb(frame, n))
                onLevel(endpointer.level)
                if (decision == SpeechEndpointer.Decision.Done) break
                if (decision == SpeechEndpointer.Decision.NoSpeech) return@withContext null
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        if (endpointer.spoke || stopRequested) pcm.toByteArray() else null
    }

    /** Sends the clip to Gemini and returns the transcript. Throws [IOException]. */
    suspend fun transcribe(pcm: ByteArray): String = withContext(Dispatchers.IO) {
        val key = settings.geminiKey.ifEmpty { throw IOException("No Gemini key") }
        val wav = GeminiSpeechProtocol.wavFromPcm(pcm, SAMPLE_RATE)
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", PROMPT))
                            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/wav").put("data", Base64.encodeToString(wav, Base64.NO_WRAP))))
                    )
                )
            )
            .put("generationConfig", JSONObject().put("temperature", 0))
            .toString()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${settings.chatModel.value}:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = http.newCall(request).await().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw IOException("Gemini transcription failed (HTTP ${r.code})")
            text
        }
        try {
            val parts = JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            (0 until parts.length()).joinToString(" ") { parts.getJSONObject(it).optString("text") }.trim()
        } catch (e: JSONException) {
            throw IOException("Unexpected transcription response", e)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        const val PROMPT = "Transcribe this in-car voice command exactly, in the language spoken. " +
            "Reply with only the transcript, no quotes or commentary. If nothing intelligible was said, reply with nothing."
    }
}
