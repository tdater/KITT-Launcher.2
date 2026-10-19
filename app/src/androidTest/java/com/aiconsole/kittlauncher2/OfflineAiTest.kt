package com.aiconsole.kittlauncher2

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiconsole.kittlauncher2.data.settings.PersonaStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The offline AI end to end on CI's emulator (.github/workflows/model-check.yml): downloads the
 * pinned model, loads it with this LiteRT-LM build and answers a car question, logging how long
 * each step took (tag OfflineAiTest). Proves the runtime reads the model; head-unit speed differs.
 */
@RunWith(AndroidJUnit4::class)
class OfflineAiTest {

    @Test
    fun answersACarQuestionOffline() = runBlocking {
        val app: KittApplication = ApplicationProvider.getApplicationContext()
        val brain = app.offlineBrain
        assertNull("unsupported: ${brain.unsupported}", brain.unsupported)
        var t = SystemClock.elapsedRealtime()
        if (!brain.installed.value) brain.install()
        Log.i(TAG, "download ${SystemClock.elapsedRealtime() - t} ms")
        val data = "VEHICLE: 2014 Ford Mustang\nOBD LINK: connected\nLIVE DATA:\n- Coolant temperature: 118 degrees C\n" +
            "- Engine speed: 820 rpm\n- Battery: 13.9 V\nSTORED CODES: P0217 (engine coolant over temperature)"
        for (question in listOf("Is the engine OK?", "What does code P0217 mean?", "What's my battery voltage?")) {
            t = SystemClock.elapsedRealtime()
            val reply = brain.answer(question, emptyList(), PersonaStyle.Classic, "", data)
            Log.i(TAG, "answer ${SystemClock.elapsedRealtime() - t} ms: Q: $question A: $reply")
            assertTrue(reply.isNotBlank())
        }
        brain.release()
    }

    private companion object {
        const val TAG = "OfflineAiTest"
    }
}
