package com.aiconsole.knightriderlauncher.ui.panes

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.aiconsole.knightriderlauncher.nav.LatLon
import com.aiconsole.knightriderlauncher.nav.Route
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Handle the pane uses to re-center after the driver pans away. */
class NavMapController {
    internal var overlay: MyLocationNewOverlay? = null
    internal var map: MapView? = null

    fun recenter() {
        val o = overlay ?: return
        o.enableFollowLocation()
        map?.controller?.setZoom(FOLLOW_ZOOM)
    }

    /** Stops following the car and fits the whole route on screen, for the route preview. */
    internal fun showWhole(points: List<LatLon>) {
        val m = map ?: return
        if (points.size < 2) return
        overlay?.disableFollowLocation()
        val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.lat, it.lon) })
        // Before the first layout the map has no size to fit into; wait for it.
        m.post { m.zoomToBoundingBox(box.increaseByScale(1.25f), false) }
    }
}

private const val FOLLOW_ZOOM = 16.5

/**
 * OpenStreetMap tiles (no API key) re-colored as a night map in the theme's [accent]: roads
 * glow on black instead of the usual daytime palette. Location follows the car while [active];
 * GPS is released when the launcher isn't on screen.
 */
@Composable
fun NavMap(
    accent: Color,
    active: Boolean,
    controller: NavMapController,
    modifier: Modifier = Modifier,
    /** Route to draw, if any. */
    route: Route? = null,
    /** Line color for [route]: it must stand out against the [accent]-tinted map. */
    routeColor: Color = Color.White
) {
    val context = LocalContext.current
    val map = remember { createMap(context) }
    val location = remember(map) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), map).apply {
            isDrawAccuracyEnabled = false
            map.overlays.add(this)
        }
    }
    val routeLine = remember(map) {
        Polyline(map).apply {
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
            isEnabled = false
            // Under the car marker.
            map.overlays.add(0, this)
        }
    }
    controller.map = map
    controller.overlay = location

    LaunchedEffect(route, routeColor) {
        routeLine.outlinePaint.color = routeColor.toArgb()
        routeLine.outlinePaint.strokeWidth = 9f * context.resources.displayMetrics.density
        routeLine.setPoints(route?.points.orEmpty().map { GeoPoint(it.lat, it.lon) })
        routeLine.isEnabled = route != null
        map.invalidate()
    }

    LaunchedEffect(accent) {
        map.overlayManager.tilesOverlay.setColorFilter(nightFilter(accent))
        map.invalidate()
    }
    DisposableEffect(active) {
        if (active) {
            map.onResume()
            location.enableMyLocation()
            location.enableFollowLocation()
        } else {
            location.disableMyLocation()
            map.onPause()
        }
        onDispose {
            location.disableMyLocation()
            map.onPause()
        }
    }
    DisposableEffect(map) {
        onDispose { map.onDetach() }
    }
    AndroidView(factory = { map }, modifier = modifier)
}

@SuppressLint("ClickableViewAccessibility") // The touch listener only observes; it never consumes clicks.
private fun createMap(context: Context): MapView {
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = context.packageName
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        controller.setZoom(FOLLOW_ZOOM)
        minZoomLevel = 4.0
        maxZoomLevel = 19.0
        // Panning the map must not swipe the home pages: claim the gesture from the pager.
        setOnTouchListener { v, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }
}

/**
 * Inverted luminance tinted with [accent]: the pale daytime land goes near-black while road
 * casings, labels and water turn into glowing lines in the theme color, like a vector display.
 */
private fun nightFilter(accent: Color): ColorMatrixColorFilter {
    val contrast = 1.5f
    val r = accent.red * contrast
    val g = accent.green * contrast
    val b = accent.blue * contrast
    // Each output channel = (255 - luminance) * tint + a faint floor so the land isn't pure black.
    fun row(tint: Float) = floatArrayOf(-0.299f * tint, -0.587f * tint, -0.114f * tint, 0f, 255f * tint + 6f)
    val matrix = ColorMatrix(row(r) + row(g) + row(b) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
    return ColorMatrixColorFilter(matrix)
}
