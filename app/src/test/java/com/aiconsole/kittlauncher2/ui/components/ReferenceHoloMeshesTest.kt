package com.aiconsole.kittlauncher2.ui.components

import org.junit.Assert.*
import org.junit.Test

class ReferenceHoloMeshesTest {
    @Test fun denseMeshesAreFiniteAndKeepAllHudAnchors() {
        for(mesh in listOf(ReferenceHoloMeshes.coupe,ReferenceHoloMeshes.truck)) {
            assertTrue(mesh.edges.size > 2500)
            assertTrue(mesh.edges.size < 6000)
            for(key in listOf("engine","battery","fuel","roof","w0","w1","w2","w3")) assertNotNull(mesh.anchors[key])
            for(e in mesh.edges) {
                assertTrue(e.weight > 0f && e.weight.isFinite())
                for(v in listOf(e.a,e.b)) assertTrue(v.x.isFinite() && v.y.isFinite() && v.z.isFinite())
            }
        }
    }
    @Test fun pickupHasLargerBodyAndCabThanCoupe() {
        assertTrue(ReferenceHoloMeshes.truck.anchors.getValue("roof").y > ReferenceHoloMeshes.coupe.anchors.getValue("roof").y)
        assertTrue(ReferenceHoloMeshes.truck.anchors.getValue("w0").x > ReferenceHoloMeshes.coupe.anchors.getValue("w0").x)
        assertTrue(ReferenceHoloMeshes.truck.edges.any { it.part == "chassis" })
        assertTrue(ReferenceHoloMeshes.coupe.edges.any { it.part == "interior" })
    }
}
