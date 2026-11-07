package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.obd.ClearReply
import com.aiconsole.kittlauncher2.obd.ClearRules
import com.aiconsole.kittlauncher2.obd.DtcReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearRulesTest {

    private fun report(stored: List<String> = emptyList(), pending: List<String> = emptyList(), permanent: List<String> = emptyList()) =
        DtcReport(stored, pending, permanent, milOn = stored.isNotEmpty(), readAt = 0L)

    @Test
    fun positiveReplyIsAccepted() {
        assertEquals(ClearReply.Accepted, ClearRules.classify("44\n"))
        assertEquals(ClearReply.Accepted, ClearRules.classify("44\r44\r"))
    }

    @Test
    fun refusalIsNotMistakenForSuccess() {
        assertEquals(ClearReply.Refused(0x22), ClearRules.classify("7F0422\n"))
        assertEquals(ClearReply.Refused(0x21), ClearRules.classify("7F 04 21"))
    }

    @Test
    fun oneModuleAcceptingBeatsAnotherRefusing() {
        assertEquals(ClearReply.Accepted, ClearRules.classify("7F0422\n44\n"))
    }

    @Test
    fun noDataAndEmptyAreSilent() {
        assertEquals(ClearReply.Silent, ClearRules.classify("NO DATA\n"))
        assertEquals(ClearReply.Silent, ClearRules.classify(""))
    }

    @Test
    fun onlyBusyAnswersAreWorthRetrying() {
        assertTrue(ClearRules.retryable(ClearReply.Refused(0x21)))
        assertTrue(ClearRules.retryable(ClearReply.Refused(0x78)))
        assertFalse(ClearRules.retryable(ClearReply.Refused(0x22)))
        assertFalse(ClearRules.retryable(ClearReply.Silent))
        assertFalse(ClearRules.retryable(ClearReply.Accepted))
    }

    @Test
    fun summaryTellsTheTruthAboutWhatHappened() {
        assertTrue(ClearRules.summary(ClearReply.Refused(0x22), report(listOf("P0420"))).contains("engine OFF"))
        assertEquals("Codes cleared. None remain.", ClearRules.summary(ClearReply.Accepted, report()))
        assertTrue(ClearRules.summary(ClearReply.Accepted, report(permanent = listOf("P0420"))).contains("permanent"))
        assertTrue(ClearRules.summary(ClearReply.Accepted, report(stored = listOf("P0301"))).contains("came straight back"))
        assertTrue(ClearRules.summary(ClearReply.Silent, report(stored = listOf("P0301"))).contains("No module confirmed"))
    }
}
