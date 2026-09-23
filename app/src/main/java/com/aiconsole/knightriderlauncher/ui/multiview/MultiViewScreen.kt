package com.aiconsole.knightriderlauncher.ui.multiview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.data.LauncherApp
import com.aiconsole.knightriderlauncher.data.MultiViewLayout
import com.aiconsole.knightriderlauncher.data.MultiWindowLauncher
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.KittGraphite
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import kotlinx.coroutines.launch

private const val MAX_SLOTS = 4

/**
 * Full-screen overlay: pick a layout, assign an app to each slot, then ENGAGE to
 * open them as positioned windows.
 */
@Composable
fun MultiViewScreen(
    apps: List<LauncherApp>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    reservedTop: Dp = 0.dp,
    reservedBottom: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val freeform = remember { MultiWindowLauncher.supportsFreeform(context) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val topPx = with(density) { reservedTop.roundToPx() }
    val bottomPx = with(density) { reservedBottom.roundToPx() }
    // Windows tile between the scanner and the dock, so both stay visible and usable.
    val area = remember(configuration, topPx, bottomPx) {
        MultiWindowLauncher.screenBounds(context).apply {
            top += topPx
            bottom = (bottom - bottomPx).coerceAtLeast(top + 1)
        }
    }
    val screenAspect = area.width().toFloat() / area.height()

    var layout by rememberSaveable { mutableStateOf(MultiViewLayout.Stacked) }
    val assignments = remember { mutableStateListOf<LauncherApp?>().apply { repeat(MAX_SLOTS) { add(null) } } }
    var activeSlot by remember { mutableIntStateOf(0) }
    var launching by remember { mutableStateOf(false) }

    val slotCount = layout.slots.size
    val ready = (0 until slotCount).all { assignments[it] != null }

    BackHandler(onBack = onDismiss)

    KittPanel(
        title = "Multi-View",
        // Swallow taps so they don't fall through to the home screen underneath.
        modifier = modifier.pointerInput(Unit) { detectTapGestures {} },
        accent = ScannerRed,
        trailing = {
            Text(
                text = if (freeform) "FREEFORM READY" else "SPLIT-SCREEN ONLY",
                style = MaterialTheme.typography.labelMedium,
                color = if (freeform) LedGreen else DashAmber
            )
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            val slotMap = @Composable { slotModifier: Modifier ->
                SlotMap(
                    layout = layout,
                    assignments = assignments,
                    activeSlot = activeSlot,
                    screenAspect = screenAspect,
                    onSlotClick = { activeSlot = it },
                    modifier = slotModifier
                )
            }
            val appPicker = @Composable { pickerModifier: Modifier ->
                AppPicker(
                    apps = apps,
                    assignments = assignments.take(slotCount),
                    onPick = { app ->
                        assignments[activeSlot] = app
                        activeSlot = nextEmptySlot(assignments, slotCount, activeSlot) ?: activeSlot
                    },
                    modifier = pickerModifier
                )
            }

            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MultiViewLayout.entries.forEach { option ->
                        LayoutPresetCard(
                            layout = option,
                            selected = option == layout,
                            enabled = freeform || !option.needsFreeform,
                            screenAspect = screenAspect,
                            onClick = {
                                layout = option
                                activeSlot = nextEmptySlot(assignments, option.slots.size, -1) ?: 0
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }

                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        slotMap(Modifier.weight(0.45f).fillMaxHeight())
                        appPicker(Modifier.weight(0.55f).fillMaxHeight())
                    }
                } else {
                    slotMap(Modifier.fillMaxWidth().weight(0.45f))
                    appPicker(Modifier.fillMaxWidth().weight(0.55f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KittButton("Cancel", onDismiss, Modifier.weight(1f).fillMaxHeight(), DashGrey)
                    KittButton(
                        label = "Clear",
                        onClick = {
                            for (i in assignments.indices) assignments[i] = null
                            activeSlot = 0
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = DashAmber
                    )
                    KittButton(
                        label = if (launching) "Engaging…" else "Engage",
                        onClick = {
                            launching = true
                            scope.launch {
                                MultiWindowLauncher.launch(
                                    context,
                                    layout,
                                    assignments.take(slotCount).filterNotNull(),
                                    area
                                )
                                launching = false
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(2f).fillMaxHeight(),
                        color = ScannerRed,
                        active = ready,
                        enabled = ready && !launching
                    )
                }
            }
        }
    }
}

/** Next unassigned slot after [current], wrapping around; null when all are filled. */
private fun nextEmptySlot(assignments: List<LauncherApp?>, slotCount: Int, current: Int): Int? =
    ((current + 1 until slotCount) + (0..current.coerceAtMost(slotCount - 1)))
        .firstOrNull { assignments[it] == null }

@Composable
private fun LayoutPresetCard(
    layout: MultiViewLayout,
    selected: Boolean,
    enabled: Boolean,
    screenAspect: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.small
    val accent = when {
        !enabled -> DashGrey.copy(alpha = 0.4f)
        selected -> ScannerRed
        else -> DashAmber
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) ScannerRed.copy(alpha = 0.12f) else KittBlack)
            .border(1.dp, if (selected) ScannerRed else KittBezel, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LayoutDiagram(
            layout = layout,
            color = accent,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(screenAspect, matchHeightConstraintsFirst = true)
        )
        Text(
            text = layout.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LayoutDiagram(layout: MultiViewLayout, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        layout.slots.forEach { slot ->
            val topLeft = Offset(size.width * slot.left + gap / 2, size.height * slot.top + gap / 2)
            val slotSize = Size(size.width * slot.width - gap, size.height * slot.height - gap)
            drawRect(color.copy(alpha = 0.25f), topLeft, slotSize)
            drawRect(color, topLeft, slotSize, style = Stroke(1.dp.toPx()))
        }
    }
}

/** Screen-shaped map of the chosen layout; tap a slot to make it the target for the next app. */
@Composable
private fun SlotMap(
    layout: MultiViewLayout,
    assignments: List<LauncherApp?>,
    activeSlot: Int,
    screenAspect: Float,
    onSlotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.small
    Box(modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .aspectRatio(screenAspect)
                .clip(shape)
                .background(KittBlack)
                .border(1.dp, KittBezel, shape)
        ) {
            layout.slots.forEachIndexed { index, slot ->
                SlotTile(
                    index = index,
                    app = assignments[index],
                    active = index == activeSlot,
                    onClick = { onSlotClick(index) },
                    modifier = Modifier
                        .offset(x = maxWidth * slot.left, y = maxHeight * slot.top)
                        .size(width = maxWidth * slot.width, height = maxHeight * slot.height)
                        .padding(3.dp)
                )
            }
        }
    }
}

@Composable
private fun SlotTile(
    index: Int,
    app: LauncherApp?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.small
    val accent = when {
        active -> ScannerRed
        app != null -> DashAmber
        else -> DashGrey
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (active) ScannerRed.copy(alpha = 0.15f) else KittGraphite)
            .border(if (active) 2.dp else 1.dp, accent, shape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Text("SLOT ${index + 1}", style = MaterialTheme.typography.labelSmall, color = accent)
        if (app != null) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelMedium,
                color = DashWhite,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = if (active) "SELECT APP" else "EMPTY",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
    }
}

@Composable
private fun AppPicker(
    apps: List<LauncherApp>,
    assignments: List<LauncherApp?>,
    onPick: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(apps, key = { it.key }) { app ->
            val slot = assignments.indexOf(app)
            PickerTile(app, slotNumber = if (slot >= 0) slot + 1 else null, onClick = { onPick(app) })
        }
    }
}

@Composable
private fun PickerTile(app: LauncherApp, slotNumber: Int?, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .height(84.dp)
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, if (slotNumber != null) DashAmber else KittBezel, shape)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Text(
                text = app.label,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = DashWhite,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (slotNumber != null) {
            Text(
                text = "$slotNumber",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(DashAmber, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = KittBlack
            )
        }
    }
}
