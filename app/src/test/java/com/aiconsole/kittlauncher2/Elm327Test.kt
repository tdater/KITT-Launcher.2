package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.obd.Elm327
import com.aiconsole.kittlauncher2.obd.ElmTimeoutException
import com.aiconsole.kittlauncher2.obd.ObdTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Elm327Test {

    /** Answers each command from [replies]; a null reply means the adapter stays silent. */
    private class ScriptedAdapter(val replies: (String) -> String?) : ObdTransport {
        override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
        val written = mutableListOf<String>()
        override suspend fun connect() = Unit
        override suspend fun write(data: ByteArray) {
            val command = String(data, Charsets.US_ASCII).trimEnd('\r')
            written += command
            replies(command)?.let { incoming.trySend(it.toByteArray(Charsets.US_ASCII)) }
        }
        override fun close() {
            incoming.close()
        }
    }

    @Test
    fun silentModuleTimesOutAndTheLinkStillWorks() = runBlocking {
        // A body module that never answers keeps the ELM waiting; a CR interrupts it.
        val adapter = ScriptedAdapter { command ->
            when (command) {
                "225008" -> null
                "" -> "STOPPED\r\r>"
                "010C" -> "410C0B20\r\r>"
                else -> "OK\r\r>"
            }
        }
        val elm = Elm327(adapter)
        try {
            elm.exclusive { send("225008", 200) }
            fail("expected a timeout")
        } catch (e: ElmTimeoutException) {
            assertTrue(e.message!!.contains("225008"))
        }
        assertEquals("410C0B20", elm.exclusive { send("010C", 500) })
        assertEquals(listOf("225008", "", "010C"), adapter.written)
    }

    @Test
    fun lateReplyIsDroppedWithoutRepeatingTheCommand() = runBlocking {
        // The reply arrives after the caller gave up: it must not be read as the next answer,
        // and no CR is sent (a CR to an idle ELM repeats the last command).
        val adapter = ScriptedAdapter { command ->
            when (command) {
                "221940" -> null
                "010C" -> "410C0B20\r\r>"
                else -> "OK\r\r>"
            }
        }
        val elm = Elm327(adapter)
        val late = Thread {
            Thread.sleep(400)
            adapter.incoming.trySend("6219405A\r\r>".toByteArray(Charsets.US_ASCII))
        }
        late.start()
        try {
            elm.exclusive { send("221940", 200) }
            fail("expected a timeout")
        } catch (_: ElmTimeoutException) {
        }
        late.join()
        assertEquals("410C0B20", elm.exclusive { send("010C", 500) })
        assertEquals(listOf("221940", "010C"), adapter.written)
    }
}
