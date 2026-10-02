package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.obd.DemoSession
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSessionTest {

    @Test
    fun simulatedReadingsStayInPlausibleRanges() {
        for (t in 0..600 step 7) {
            val f = DemoSession.frame(t.toDouble())
            assertTrue(f.getValue(PidCatalog.SPEED) in 50.0..70.0)
            assertTrue(f.getValue(PidCatalog.RPM) in 1_400.0..2_200.0)
            assertTrue(f.getValue(PidCatalog.COOLANT) in 85.0..100.0)
            assertTrue(f.getValue(PidCatalog.MODULE_VOLTAGE) in 13.5..14.7)
            assertTrue(f.getValue(PidCatalog.THROTTLE) in 0.0..100.0)
        }
    }
}
