package com.aiconsole.kittlauncher2.ui.components

import com.aiconsole.kittlauncher2.obd.PidCatalog
import org.junit.Assert.*
import org.junit.Test

class HoloWireframeIntegrationTest {
    @Test fun missingTelemetryIsUnknown() {
        for (i in 0..3) {
            val c = holoFallbackCallout(i, false) { null }
            assertEquals(ReadoutLevel.Unknown, c.level)
            assertEquals("--", c.value)
            assertFalse(c.anchor.startsWith("w"))
        }
    }
    @Test fun validZeroLoadIsNotMissing() {
        val c = holoFallbackCallout(0, true) { if(it == PidCatalog.ENGINE_LOAD) 0.0 else null }
        assertEquals(ReadoutLevel.Ok, c.level)
        assertEquals("0%", c.value)
    }
    @Test fun airflowReplacesMissingFuelRate() {
        val c = holoFallbackCallout(3, false) { if(it == PidCatalog.MAF) 3.2 else null }
        assertEquals("fallback:maf", c.key)
        assertEquals("3.2 g/s", c.value)
        assertEquals("engine", c.anchor)
    }
    @Test fun invalidNumbersAreNotHealthy() {
        val c = holoFallbackCallout(0, false) { Double.NaN }
        assertEquals(ReadoutLevel.Unknown, c.level)
        assertEquals("--", holoFallbackText("rpm", Double.POSITIVE_INFINITY, false))
    }
    @Test fun meshesHaveFiniteGeometryAndFourWheelAnchors() {
        for(mesh in listOf(HoloWireMeshes.coupe,HoloWireMeshes.truck)) {
            assertTrue(mesh.edges.size > 300)
            for(i in 0..3) assertNotNull(mesh.anchors["w$i"])
            for(edge in mesh.edges) for(v in listOf(edge.a,edge.b)) {
                assertTrue(v.x.isFinite() && v.y.isFinite() && v.z.isFinite())
            }
            assertTrue(mesh.anchors.getValue("w0").z > 0f)
            assertTrue(mesh.anchors.getValue("w1").z < 0f)
            assertTrue(mesh.anchors.getValue("w0").x > mesh.anchors.getValue("w2").x)
        }
    }
}
