package com.aiconsole.kittlauncher2.ui.panes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.nav.LatLon
import com.aiconsole.kittlauncher2.nav.NavState
import com.aiconsole.kittlauncher2.nav.NavStatus
import com.aiconsole.kittlauncher2.nav.Place
import com.aiconsole.kittlauncher2.nav.RouteNavigator
import com.aiconsole.kittlauncher2.nav.RouteRules
import com.aiconsole.kittlauncher2.nav.RouteStep
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittTextField
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** Destination search over the map: a search box, results nearest first, and recent places. */
@Composable
fun RouteSearchPanel(navigator: RouteNavigator, metric: Boolean, onPick: (Place) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var here by remember { mutableStateOf<LatLon?>(null) }
    val recents = remember { navigator.recents() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || busy) return
        // Results show under the box; the keyboard would cover them.
        keyboard?.hide()
        busy = true
        error = null
        scope.launch {
            try {
                here = navigator.position()
                results = navigator.search(q)
                if (results.isNullOrEmpty()) error = "Nothing found for \"$q\"."
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                error = "Search unavailable. Check the data connection."
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier
            .background(KittBlack.copy(alpha = 0.94f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KittTextField(query, { query = it }, "Search address or place", Modifier.weight(1f), onDone = ::search)
            KittButton(if (busy) "…" else "Search", ::search, Modifier.height(48.dp).width(96.dp), ScannerRed, active = true, enabled = !busy)
            KittButton("Close", onClose, Modifier.height(48.dp).width(84.dp), ScannerRed)
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DashAmber) }
        val list = results ?: recents
        if (results == null && recents.isNotEmpty()) {
            Text("RECENT", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(list) { place ->
                PlaceRow(place, RouteRules.distanceLabel(here, place.location, metric)) { onPick(place) }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, distance: String?, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, ScannerRed.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.titleSmall, color = DashWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (place.detail.isNotBlank()) {
                Text(place.detail, style = MaterialTheme.typography.bodySmall, color = DashGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        distance?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = DashAmber) }
    }
}

/** Next maneuver of the in-app route: arrow, distance to it and what to do. */
@Composable
fun RouteBanner(state: NavState, metric: Boolean) {
    val shape = MaterialTheme.shapes.small
    val step = state.nextStep
    val arrived = state.status == NavStatus.Arrived
    Row(
        Modifier
            .widthIn(max = 460.dp)
            .clip(shape)
            .background(KittBlack.copy(alpha = 0.88f))
            .border(1.dp, ScannerRed, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ManeuverArrow(step, arrived, Modifier.size(52.dp))
        Column {
            when {
                arrived -> Text(
                    "ARRIVED" + (state.destination?.let { " · ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = DashWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                step != null -> {
                    state.progress?.let {
                        Text(RouteRules.shortDistance(it.toNextM, metric), style = MaterialTheme.typography.titleLarge, color = DashAmber)
                    }
                    Text(RouteRules.instruction(step), style = MaterialTheme.typography.titleMedium, color = DashWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                else -> Text("Recalculating…", style = MaterialTheme.typography.titleMedium, color = DashWhite)
            }
        }
    }
}

/** Canvas arrow for a maneuver: a stem from below, bent toward the turn; a ring on arrival. */
@Composable
private fun ManeuverArrow(step: RouteStep?, arrived: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = 5.dp.toPx()
        val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val c = Offset(size.width / 2, size.height * 0.55f)
        if (arrived || step == null || step.type == "arrive") {
            drawCircle(ScannerRed, radius = size.minDimension * 0.3f, center = c, style = stroke)
            drawCircle(ScannerRed, radius = size.minDimension * 0.1f, center = c)
            return@Canvas
        }
        val angle = RouteRules.arrowAngle(step)
        val len = size.minDimension * 0.38f
        val path = Path().apply { moveTo(c.x, size.height - w) }
        val tip: Offset
        val heading: Double
        if (angle >= 180f) {
            // U-turn: up, over and back down on the left.
            val r = size.width * 0.2f
            path.lineTo(c.x, c.y - len * 0.4f)
            path.arcTo(Rect(Offset(c.x - 2 * r, c.y - len * 0.4f - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r)), 0f, -180f, false)
            tip = Offset(c.x - 2 * r, c.y + len * 0.4f)
            path.lineTo(tip.x, tip.y)
            heading = Math.PI / 2
        } else {
            path.lineTo(c.x, c.y)
            val rad = Math.toRadians(angle.toDouble() - 90)
            tip = Offset(c.x + (len * Math.cos(rad)).toFloat(), c.y + (len * Math.sin(rad)).toFloat())
            path.lineTo(tip.x, tip.y)
            heading = rad
        }
        drawPath(path, ScannerRed, style = stroke)
        val head = size.minDimension * 0.18f
        for (side in listOf(-1, 1)) {
            val a = heading + Math.PI + side * Math.PI / 4.5
            drawLine(ScannerRed, tip, Offset(tip.x + (head * Math.cos(a)).toFloat(), tip.y + (head * Math.sin(a)).toFloat()), w, StrokeCap.Round)
        }
    }
}

/** Bottom card: route preview with Start, live distance/time left with End, or an error. */
@Composable
fun RouteFooter(state: NavState, metric: Boolean, navigator: RouteNavigator, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .widthIn(max = 420.dp)
            .clip(shape)
            .background(KittBlack.copy(alpha = 0.9f))
            .border(1.dp, ScannerRed, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        state.destination?.let {
            Text(it.name, style = MaterialTheme.typography.titleSmall, color = DashWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (state.status) {
            NavStatus.Routing -> Text("Finding a route…", style = MaterialTheme.typography.bodyMedium, color = DashAmber)
            NavStatus.Error -> {
                Text(state.message ?: "No route.", style = MaterialTheme.typography.bodyMedium, color = DashAmber)
                KittButton("Close", navigator::stop, Modifier.height(40.dp).width(96.dp), ScannerRed)
            }
            NavStatus.Preview, NavStatus.Navigating -> {
                val remaining = state.progress?.remainingM ?: state.route?.distanceM ?: 0.0
                val seconds = state.remainingS ?: 0.0
                val eta = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (seconds * 1000).toLong()))
                Text(
                    "${RouteRules.shortDistance(remaining, metric)} · ${RouteRules.duration(seconds)} · ETA $eta",
                    style = MaterialTheme.typography.titleMedium,
                    color = DashAmber
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.status == NavStatus.Preview) {
                        KittButton("Start", { navigator.start() }, Modifier.height(40.dp).width(96.dp), ScannerRed, active = true)
                        state.destination?.let { place ->
                            KittButton("Other app", { openElsewhere(context, place) }, Modifier.height(40.dp).width(112.dp), ScannerRed)
                        }
                        KittButton("Cancel", navigator::stop, Modifier.height(40.dp).width(96.dp), ScannerRed)
                    } else {
                        KittButton("End", navigator::stop, Modifier.height(40.dp).width(96.dp), ScannerRed, active = true)
                    }
                }
            }
            NavStatus.Idle, NavStatus.Arrived -> Unit
        }
    }
}

/** Hands the destination to Google Maps, Waze or whatever handles geo: links. */
private fun openElsewhere(context: Context, place: Place) {
    val label = Uri.encode(place.name)
    val uri = Uri.parse("geo:${place.location.lat},${place.location.lon}?q=${place.location.lat},${place.location.lon}($label)")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No other navigation app installed", android.widget.Toast.LENGTH_SHORT).show()
    }
}
