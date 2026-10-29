package com.aiconsole.kittlauncher2.ui.panes

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiconsole.kittlauncher2.ui.theme.LocalLiteMode
import kotlinx.coroutines.delay
import com.aiconsole.kittlauncher2.nav.LatLon
import com.aiconsole.kittlauncher2.nav.Route
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
    // A neon route: a wide soft halo, the colored line, and a white-hot core, all under the car marker.
    fun routeLayer(): Polyline = Polyline(map).apply {
        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        isEnabled = false
    }
    val routeLines = remember(map) {
        listOf(routeLayer(), routeLayer(), routeLayer()).also { layers -> layers.forEachIndexed { i, l -> map.overlays.add(i, l) } }
    }
    controller.map = map
    controller.overlay = location

    LaunchedEffect(route, routeColor) {
        val width = 9f * context.resources.displayMetrics.density
        val points = route?.points.orEmpty().map { GeoPoint(it.lat, it.lon) }
        val (halo, line, core) = routeLines
        halo.outlinePaint.color = routeColor.copy(alpha = 0.28f).toArgb()
        halo.outlinePaint.strokeWidth = width * MapStyle.HALO_WIDTH
        line.outlinePaint.color = routeColor.toArgb()
        line.outlinePaint.strokeWidth = width
        core.outlinePaint.color = Color.White.copy(alpha = 0.85f).toArgb()
        core.outlinePaint.strokeWidth = width * 0.3f
        for (l in routeLines) {
            l.setPoints(points)
            l.isEnabled = route != null
        }
        map.invalidate()
    }

    LaunchedEffect(accent) {
        map.overlayManager.tilesOverlay.setColorFilter(hologramFilter(accent))
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
    Box(modifier) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
        val glow = MapStyle.bright(accent.red, accent.green, accent.blue).let { Color(it.first, it.second, it.third) }
        HologramOverlay(glow, map, location, Modifier.fillMaxSize())
    }
}

/**
 * The hologram dressing over the tiles: a faint grid, scan sweep, corner brackets and pulsing
 * sonar rings on the car. It never takes touches, and stands still in lite mode.
 */
@Composable
private fun HologramOverlay(accent: Color, map: MapView, location: MyLocationNewOverlay, modifier: Modifier) {
    val lite = LocalLiteMode.current
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lite) {
        if (lite) return@LaunchedEffect
        var last = SystemClock.uptimeMillis()
        while (true) {
            delay(50)
            val now = SystemClock.uptimeMillis()
            phase.floatValue = (phase.floatValue + (now - last).coerceAtMost(250L) / 3200f) % 1f
            last = now
        }
    }
    Canvas(modifier) {
        val t = phase.floatValue
        val grid = MapStyle.GRID_DP.dp.toPx()
        val line = accent.copy(alpha = 0.18f)
        var x = grid
        while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += grid }
        var y = grid
        while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += grid }
        // Fine scanlines, like a projected image.
        val scan = Color.Black.copy(alpha = 0.18f)
        var sy = 0f
        while (sy < size.height) { drawLine(scan, Offset(0f, sy), Offset(size.width, sy), 1f); sy += 4.dp.toPx() }
        if (!lite) {
            val sweep = size.height * t
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.32f), Color.Transparent), sweep - 40.dp.toPx(), sweep + 40.dp.toPx()),
                Offset(0f, sweep - 40.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 80.dp.toPx())
            )
            val here = location.myLocation
            if (here != null) {
                val p = map.projection.toPixels(here, null)
                val center = Offset(p.x.toFloat(), p.y.toFloat())
                for (k in 0 until 3) {
                    val f = (t * 3f + k) % 3f / 3f
                    drawCircle(accent.copy(alpha = (1f - f) * 0.7f), radius = 10.dp.toPx() + f * 46.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                }
            }
        }
        // Corner brackets frame the hologram.
        val arm = 22.dp.toPx()
        val inset = 6.dp.toPx()
        val w = 2.dp.toPx()
        val bracket = accent.copy(alpha = 0.9f)
        for ((cx, dx) in listOf(inset to 1f, size.width - inset to -1f)) {
            for ((cy, dy) in listOf(inset to 1f, size.height - inset to -1f)) {
                drawLine(bracket, Offset(cx, cy), Offset(cx + dx * arm, cy), w)
                drawLine(bracket, Offset(cx, cy), Offset(cx, cy + dy * arm), w)
            }
        }
        // Soft vignette so the glow sits in the middle.
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)), center, size.maxDimension * 0.8f))
    }
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

/** The tiles as a hologram in the theme's [accent]; the numbers live in [MapStyle]. */
private fun hologramFilter(accent: Color): ColorMatrixColorFilter =
    ColorMatrixColorFilter(ColorMatrix(MapStyle.matrix(accent.red, accent.green, accent.blue)))
