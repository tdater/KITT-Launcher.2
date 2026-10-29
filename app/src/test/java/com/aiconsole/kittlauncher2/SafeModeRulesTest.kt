package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.data.SafeModeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeModeRulesTest {
    private val now = 1_000_000_000L

    @Test fun anUnfinishedStartCountsAsAFailedStart() {
        val crashes = SafeModeRules.withUnfinishedStart(emptyList(), now - 30_000, now)
        assertEquals(listOf(now - 30_000), crashes)
    }

    @Test fun noMarkerOrAnOldMarkerAddsNothing() {
        assertTrue(SafeModeRules.withUnfinishedStart(emptyList(), 0L, now).isEmpty())
        assertTrue(SafeModeRules.withUnfinishedStart(emptyList(), now - SafeModeRules.SPAN_MS - 1, now).isEmpty())
    }

    @Test fun threeSilentRestartsTripSafeMode() {
        var crashes = emptyList<Long>()
        for (i in 0 until 3) crashes = SafeModeRules.withUnfinishedStart(crashes, now - 100_000L + i * 20_000L, now)
        assertTrue(SafeModeRules.shouldTrip(crashes, now))
        assertFalse(SafeModeRules.shouldTrip(crashes.take(2), now))
    }
}
