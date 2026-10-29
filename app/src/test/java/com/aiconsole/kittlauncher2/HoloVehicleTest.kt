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
}
