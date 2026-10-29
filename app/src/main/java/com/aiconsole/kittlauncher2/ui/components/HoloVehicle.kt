package com.aiconsole.kittlauncher2.ui.components

/** Which vehicle the hologram turntable shows on the KITT Holo Drive screen. */
enum class VehicleChoice(val key: String, val label: String, val blurb: String) {
    Car("car", "Car", "The sports car; its body follows the theme (coupe, cruiser, or the wedge for Back to the Future)."),
    Truck("truck", "Truck", "A crew-cab pickup with an open bed, on the same turntable with the same live readings.");

    companion object {
        /** Unknown or empty settings fall back to the car, so an old backup never breaks the screen. */
        fun decode(key: String): VehicleChoice = entries.firstOrNull { it.key == key } ?: Car
    }
}

/** Picks the turntable art: the chosen vehicle, or the theme's car body ([themeBody]) for the car. */
object HoloVehicle {
    fun body(choice: VehicleChoice, themeBody: String): String = if (choice == VehicleChoice.Truck) "truck" else themeBody
}
