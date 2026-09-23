package com.aiconsole.knightriderlauncher.assistant

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Source(val title: String, val uri: String)

/** One prior turn of the conversation, oldest first. */
data class ChatTurn(val fromUser: Boolean, val text: String)

/** A question queued from elsewhere in the UI (e.g. "Ask KITT" on a fault code). */
data class AssistantPrompt(val text: String, val diagnostic: Boolean = false)

sealed interface AssistantResult {
    data class Answer(
        val text: String,
        val sources: List<Source> = emptyList(),
        val searchQueries: List<String> = emptyList(),
        /** Model or preset that actually answered. */
        val engine: String = "",
        /** Web search was requested but the key's tier doesn't allow it. */
        val searchUnavailable: Boolean = false
    ) : AssistantResult {
        val usedWebSearch: Boolean get() = sources.isNotEmpty() || searchQueries.isNotEmpty()
    }

    data class Failure(val message: String, val offline: Boolean = false) : AssistantResult
}

internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal const val OFFLINE_MESSAGE =
    "No internet connection. I need a data connection to answer; vehicle data is still being logged."

internal fun assistantHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS)
    .build()

/** Suspends on an OkHttp call without blocking a thread; cancelling the coroutine cancels the call. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
}

/** Strips markdown so answers read cleanly on screen and through TTS. */
internal fun toGlanceable(raw: String): String = raw
    .replace("**", "")
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .replace(Regex("(?m)^\\s*[*-]\\s+"), "• ")
    .replace(Regex("\\s*\\[\\d+(?:\\s*,\\s*\\d+)*]"), "")
    .trim()
