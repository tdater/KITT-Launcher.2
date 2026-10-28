package com.aiconsole.kittlauncher2.ui.panes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.obd.PidReading
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.gauges.GaugeTile
import com.aiconsole.kittlauncher2.ui.gauges.GaugeUi
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed

/**
 * Gauges page: the dashboard gauges at a size readable at a glance. Like the home grid, each
 * tile pulls its value through [reading] so OBD updates redraw only the tiles. In cluster layout
 * the first gauge is drawn large, like the main dial of an instrument cluster.
 */
@Composable
fun GaugeWallPane(
    gauges: List<GaugeUi>,
    metric: Boolean,
    reading: (String) -> PidReading?,
    onGaugeClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPursuit: () -> Unit = {}
) {
    val store = (LocalContext.current.applicationContext as KittApplication).gauges
    val cluster by store.cluster.collectAsState()
    KittPanel(
        title = "Gauges",
        modifier = modifier,
        accent = LedGreen,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KittButton(
                    label = if (cluster) "Cluster" else "Grid",
                    onClick = { store.setCluster(!cluster) },
                    modifier = Modifier.height(32.dp).width(96.dp),
                    color = DashAmber,
                    active = cluster
                )
                KittButton("Pursuit", onPursuit, Modifier.height(32.dp).width(104.dp), ScannerRed, active = true)
            }
        }
    ) {
        if (gauges.isEmpty()) {
            Text(
                "No gauges yet. Tap here to add some in Diagnostics.",
                modifier = Modifier.clickable(onClick = onGaugeClick).padding(8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = DashGrey
            )
            return@KittPanel
        }
        @Composable
        fun Tile(gauge: GaugeUi, tileModifier: Modifier) = GaugeTile(
            gauge = gauge.copy(reading = reading(gauge.config.pidId)),
            metric = metric,
            modifier = tileModifier,
            onClick = onGaugeClick
        )

        @Composable
        fun Grid(list: List<GaugeUi>, gridModifier: Modifier, minCell: Dp, tileHeight: Dp) = LazyVerticalGrid(
            columns = GridCells.Adaptive(minCell),
            modifier = gridModifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.config.id }) { gauge -> Tile(gauge, Modifier.height(tileHeight)) }
        }

        if (!cluster || gauges.size < 2) {
            Grid(gauges, Modifier.fillMaxSize(), 220.dp, 180.dp)
            return@KittPanel
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val hero = gauges.first()
            val rest = gauges.drop(1)
            if (maxHeight > maxWidth) {
                // Portrait: the big gauge across the top, the rest in a grid below.
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(hero, Modifier.fillMaxWidth().weight(0.45f))
                    Grid(rest, Modifier.fillMaxWidth().weight(0.55f), 200.dp, 170.dp)
                }
            } else {
                // Landscape: the big gauge on the left, the rest beside it.
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(hero, Modifier.fillMaxHeight().weight(0.45f))
                    Grid(rest, Modifier.fillMaxHeight().weight(0.55f), 190.dp, 160.dp)
                }
            }
        }
    }
}
