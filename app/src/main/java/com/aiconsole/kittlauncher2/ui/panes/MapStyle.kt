package com.aiconsole.kittlauncher2.ui.panes

/**
 * The hologram look of the navigation map, built from the theme's accent color so every theme and
 * film theme gets its own: deep land in a dark shade of the accent, roads and labels in the bright
 * accent running to white at the busiest lines. Pure, so the numbers are unit-tested.
 */
object MapStyle {
    private const val GAIN = 2.6f
    private const val WHITE_HOT = 0.45f
    private const val LAND = 0.10f

    /**
     * Android ColorMatrix values (4x5, row-major) for the daytime OpenStreetMap tiles: luminance is
     * inverted so the pale land goes dark and roads, water edges and labels light up, then tinted.
     */
    fun matrix(red: Float, green: Float, blue: Float): FloatArray {
        // Dim theme accents (KITT's dark red) are lifted to full brightness first: a hologram glows.
        val peak = maxOf(red, green, blue, 0.05f)
        fun row(raw: Float): FloatArray {
            val accent = raw / peak
            val tint = accent * GAIN + WHITE_HOT
            return floatArrayOf(-0.299f * tint, -0.587f * tint, -0.114f * tint, 0f, 255f * tint + 255f * accent * LAND)
        }
        return row(red) + row(green) + row(blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
    }

    /** Hue in degrees (0..360), or -1 for a grey with no hue. */
    fun hue(red: Float, green: Float, blue: Float): Float {
        val max = maxOf(red, green, blue)
        val delta = max - minOf(red, green, blue)
        if (delta < 0.08f) return -1f
        val h = when (max) {
            red -> ((green - blue) / delta) % 6f
            green -> (blue - red) / delta + 2f
            else -> (red - green) / delta + 4f
        } * 60f
        return if (h < 0) h + 360f else h
    }

    /**
     * Index of the option whose hue is furthest from the map's [accent] (RGB 0..1 triples), so the
     * route never blends into a map tinted the same color; a grey option counts as 90 degrees away.
     */
    fun mostContrasting(accent: Triple<Float, Float, Float>, options: List<Triple<Float, Float, Float>>): Int {
        val base = hue(accent.first, accent.second, accent.third)
        fun distance(o: Triple<Float, Float, Float>): Float {
            val h = hue(o.first, o.second, o.third)
            if (base < 0 || h < 0) return 90f
            val d = Math.abs(base - h)
            return minOf(d, 360f - d)
        }
        return options.indices.maxByOrNull { distance(options[it]) } ?: 0
    }

    /** The accent at full brightness, for the grid, rings and brackets drawn over the map. */
    fun bright(red: Float, green: Float, blue: Float): Triple<Float, Float, Float> {
        val peak = maxOf(red, green, blue, 0.05f)
        return Triple(red / peak, green / peak, blue / peak)
    }

    /** Width of the glow around the route, relative to the route line. */
    const val HALO_WIDTH = 2.4f

    /** Spacing of the faint hologram grid over the map, in dp. */
    const val GRID_DP = 56f
}
