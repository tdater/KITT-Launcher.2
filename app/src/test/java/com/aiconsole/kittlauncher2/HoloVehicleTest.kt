package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.ui.components.HoloVehicle
import com.aiconsole.kittlauncher2.ui.components.VehicleChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class HoloVehicleTest {
    @Test fun carKeepsTheThemesBody() {
        assertEquals("coupe", HoloVehicle.body(VehicleChoice.Car, "coupe"))
        assertEquals("wedge", HoloVehicle.body(VehicleChoice.Car, "wedge"))
        assertEquals("cruiser", HoloVehicle.body(VehicleChoice.Car, "cruiser"))
    }

    @Test fun truckOverridesEveryTheme() {
        for (theme in listOf("coupe", "wedge", "cruiser")) assertEquals("truck", HoloVehicle.body(VehicleChoice.Truck, theme))
    }

    @Test fun settingDecodesAndFallsBackToTheCar() {
        assertEquals(VehicleChoice.Truck, VehicleChoice.decode("truck"))
        assertEquals(VehicleChoice.Car, VehicleChoice.decode("car"))
        assertEquals(VehicleChoice.Car, VehicleChoice.decode(""))
        assertEquals(VehicleChoice.Car, VehicleChoice.decode("spaceship"))
    }

    @Test fun keysAreUnique() {
        assertEquals(VehicleChoice.entries.size, VehicleChoice.entries.map { it.key }.toSet().size)
    }

    @Test fun flipbookMetadataHasAllFramesWheelsAndAnchors() {
        val rawDir = java.io.File("src/holo/res/raw")
        if (!rawDir.exists()) return
        val requiredAnchors = setOf("engine", "battery", "fuel", "exhaust", "tail", "tailTo", "roof")
        for (kind in listOf("coupe", "cruiser", "wedge", "truck")) {
            val file = java.io.File(rawDir, "holo_$kind.json")
            org.junit.Assert.assertTrue("Missing ${file.path}", file.exists())
            val content = file.readText()
            org.junit.Assert.assertTrue("Missing dimensions in $kind", content.contains("\"w\":720") && content.contains("\"h\":250"))
            for (anchor in requiredAnchors) {
                org.junit.Assert.assertTrue("Missing anchor $anchor in $kind", content.contains("\"$anchor\":["))
            }
            val frameCount = Regex("\"wheels\":").findAll(content).count()
            assertEquals("Expected 72 frames in $kind", 72, frameCount)
        }
    }
}
