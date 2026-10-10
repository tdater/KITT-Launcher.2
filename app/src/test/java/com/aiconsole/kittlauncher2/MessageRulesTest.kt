package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.nav.MessageReadout
import com.aiconsole.kittlauncher2.nav.MessageRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRulesTest {

    @Test
    fun offAndMissingSenderSayNothing() {
        assertNull(MessageRules.announcement(MessageReadout.Off, "Messages", "Sam", "hi"))
        assertNull(MessageRules.announcement(MessageReadout.Full, "Messages", "  ", "hi"))
    }

    @Test
    fun senderOnlyAndFull() {
        assertEquals("New WhatsApp message from Sam.", MessageRules.announcement(MessageReadout.SenderOnly, "WhatsApp", "Sam", "secret"))
        assertEquals("Sam says: on my way", MessageRules.announcement(MessageReadout.Full, "Messages", "Sam", " on  my\nway "))
        assertEquals("New Messages message from Sam.", MessageRules.announcement(MessageReadout.Full, "Messages", "Sam", ""))
    }

    @Test
    fun longMessagesAreClippedAtAWord() {
        val long = "word ".repeat(100)
        val spoken = MessageRules.announcement(MessageReadout.Full, "Messages", "Sam", long)!!
        assertTrue(spoken.length < 260)
        assertTrue(spoken.endsWith("word…"))
    }
}
