package com.aiconsole.kittlauncher2.ui.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.data.LauncherApp
import com.aiconsole.kittlauncher2.obd.PidReading
import com.aiconsole.kittlauncher2.ui.components.AppIcon
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.gauges.GaugeTile
import com.aiconsole.kittlauncher2.ui.gauges.GaugeTileHeight
import com.aiconsole.kittlauncher2.ui.gauges.GaugeUi
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack

private val AppTileHeight = 96.dp

/**
 * Middle pane: the driver's live gauges followed by quick-launch app shortcuts.
 *
 * [gauges] carry configuration only; each tile pulls its live value through [reading] inside
 * its own item, so OBD updates redraw the gauge tiles and never the app grid.
 */
@Composable
fun StatusGridPane(
    apps: List<LauncherApp>,
    onAppClick: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    minCellWidth: Dp = 140.dp,
    gauges: List<GaugeUi> = emptyList(),
    metric: Boolean = false,
    onGaugeClick: () -> Unit = {},
    reading: (String) -> PidReading? = { null },
    appTileHeight: Dp = AppTileHeight,
    appsTitle: String = "Applications"
) {
    KittPanel(
        title = "Systems",
        modifier = modifier,
        accent = DashAmber,
        trailing = {
            Text("${apps.size} APPS", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minCellWidth),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header_status", span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("Vehicle Status")
            }
            if (gauges.isEmpty()) {
                item(key = "no_gauges", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "No gauges yet. Open DIAGNOSTICS to add some.",
                        modifier = Modifier.clickable(onClick = onGaugeClick).padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DashGrey
                    )
                }
            }
            items(gauges, key = { "gauge_${it.config.id}" }) { gauge ->
                GaugeTile(
                    gauge = gauge.copy(reading = reading(gauge.config.pidId)),
                    metric = metric,
                    modifier = Modifier.height(GaugeTileHeight),
                    onClick = onGaugeClick
                )
            }
            item(key = "header_apps", span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(appsTitle)
            }
            items(apps, key = { it.key }) { app ->
                AppTile(app, appTileHeight, onClick = { onAppClick(app) })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = DashGrey
    )
}

@Composable
private fun AppTile(app: LauncherApp, height: Dp, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = Modifier
            .height(height)
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, KittBezel, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIcon(app, Modifier.size(44.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            color = DashWhite,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
