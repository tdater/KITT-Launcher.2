package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.browser.RendererRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererRulesTest {
    @Test
    fun watchdogKillsOnceWhenTheLauncherStalls() {
        assertFalse(RendererRules.shouldKillOnStall(RendererRules.LAUNCHER_STALL_MS - 1, false))
        assertTrue(RendererRules.shouldKillOnStall(RendererRules.LAUNCHER_STALL_MS, false))
        assertFalse(RendererRules.shouldKillOnStall(RendererRules.LAUNCHER_STALL_MS * 3, true))
    }

    @Test
    fun busyPageIsLeftAloneWhileTheLauncherIsFine() {
        assertFalse(RendererRules.shouldTerminate(10_000, 0))
        assertTrue(RendererRules.shouldTerminate(RendererRules.HUNG_MS, 0))
        assertTrue(RendererRules.shouldTerminate(0, RendererRules.LAUNCHER_STALL_MS))
    }
}
