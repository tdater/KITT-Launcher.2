package com.aiconsole.kittlauncher2.browser

/**
 * Turns game-controller buttons into the keyboard keys browser emulators listen for
 * (EmulatorJS and most web emulators default to arrows, Z/X/A/S, Enter and Shift).
 * Android KeyEvent codes are inlined so this stays plain Kotlin.
 */
object Gamepad {

    // android.view.KeyEvent codes.
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109

    private const val KEY_ENTER = 66
    private const val KEY_SHIFT_RIGHT = 60
    private const val KEY_Z = 54
    private const val KEY_X = 52
    private const val KEY_A = 29
    private const val KEY_S = 47
    private const val KEY_Q = 45
    private const val KEY_W = 51

    /** Controller key -> keyboard key. D-pad keys pass through as arrows. */
    val DEFAULT: Map<Int, Int> = mapOf(
        DPAD_UP to DPAD_UP,
        DPAD_DOWN to DPAD_DOWN,
        DPAD_LEFT to DPAD_LEFT,
        DPAD_RIGHT to DPAD_RIGHT,
        BUTTON_A to KEY_X,
        BUTTON_B to KEY_Z,
        BUTTON_X to KEY_S,
        BUTTON_Y to KEY_A,
        BUTTON_L1 to KEY_Q,
        BUTTON_R1 to KEY_W,
        BUTTON_START to KEY_ENTER,
        BUTTON_SELECT to KEY_SHIFT_RIGHT
    )

    fun map(keyCode: Int): Int? = DEFAULT[keyCode]

    /** Analog stick to D-pad: which directions are held for stick position (x, y in -1..1). */
    fun stickDirections(x: Float, y: Float, deadZone: Float = 0.45f): Set<Int> = buildSet {
        if (x <= -deadZone) add(DPAD_LEFT)
        if (x >= deadZone) add(DPAD_RIGHT)
        if (y <= -deadZone) add(DPAD_UP)
        if (y >= deadZone) add(DPAD_DOWN)
    }
}

/** A browser game site for the Games page. */
data class GameSite(val label: String, val url: String, val note: String)

/**
 * The Games page's buttons: the owner's bookmarked game sites that are licensed or preservation
 * archives. Sites hosting commercial ROMs aren't built in; the driver can import their own
 * bookmarks into My sites.
 */
object GameSites {
    val ALL = listOf(
        GameSite("Internet Arcade", "https://archive.org/details/internetarcade", "Classic arcade machines, preserved by the Internet Archive"),
        GameSite("MS-DOS Games", "https://archive.org/details/softwarelibrary_msdos_games", "Thousands of DOS games, run in the browser"),
        GameSite("Level Devil", "https://poki.com/en/g/level-devil", "Platformer on Poki"),
        GameSite("Poki", "https://poki.com/", "Free browser games"),
        GameSite("CrazyGames", "https://www.crazygames.com/", "Free browser games"),
        GameSite("Addicting Games", "https://www.addictinggames.com/", "Over 5,000 free browser games")
    )
}
