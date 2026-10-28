package com.aiconsole.kittlauncher2.ui.panes

import com.aiconsole.kittlauncher2.ui.components.Annunciator
import com.aiconsole.kittlauncher2.ui.components.AnnunciatorRow
import com.aiconsole.kittlauncher2.ui.components.skinFrame
import com.aiconsole.kittlauncher2.BuildConfig
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.data.AppGroup
import com.aiconsole.kittlauncher2.data.AppLists
import com.aiconsole.kittlauncher2.data.AppUsage
import com.aiconsole.kittlauncher2.data.LauncherApp
import com.aiconsole.kittlauncher2.ui.components.AppIcon
import com.aiconsole.kittlauncher2.ui.components.ChoiceRow
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.components.KittTextField
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlinx.coroutines.launch

/**
 * Every installed app: search, an A-Z rail for quick jumps, and long-press to pin to the home
 * grid, hide, or open App info. Large targets, one tap to launch.
 */
@Composable
fun AppDrawerPane(
    apps: List<LauncherApp>,
    onLaunch: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = (context.applicationContext as KittApplication).settings
    val pinned = AppLists.decode(settings.pinnedApps.state.collectAsState().value)
    val hidden = AppLists.decode(settings.hiddenApps.state.collectAsState().value).toSet()
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LauncherApp?>(null) }
    var filter by remember { mutableStateOf(ALL) }
    val usage = AppUsage.decode(settings.appUsage.state.collectAsState().value)
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val visible = apps.filter { (showHidden || it.key !in hidden) && AppLists.matches(it.label, query) }
    val filters = listOf(ALL) + (if (usage.isNotEmpty()) listOf(MOST_USED) else emptyList()) +
        AppGroup.entries.filter { g -> visible.any { it.group == g } }.map { it.label }
    val shown = when (filter) {
        ALL -> visible
        MOST_USED -> AppUsage.mostUsed(usage, 16).mapNotNull { key -> visible.firstOrNull { it.key == key } }
        else -> visible.filter { it.group.label == filter }
    }
    val sections = shown.map { AppLists.sectionOf(it.label) }.toSet()

    KittPanel(
        title = "Apps",
        modifier = modifier,
        accent = DashAmber,
        trailing = { Text("${shown.size} OF ${apps.size}", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (BuildConfig.HOLO_DESIGN) {
                AnnunciatorRow(
                    listOf(
                        Annunciator("ALL ${apps.size}", filter == ALL, LedGreen),
                        Annunciator("PINNED ${pinned.size}", pinned.isNotEmpty(), DashAmber),
                        Annunciator("HIDDEN ${hidden.size}", showHidden, DashAmber),
                        Annunciator("SEARCH", query.isNotBlank(), ScannerRed)
                    ),
                    Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KittTextField(query, { query = it }, "Search apps…", Modifier.weight(1f))
                KittButton(
                    label = if (showHidden) "Hide hidden" else "Show hidden (${hidden.size})",
                    onClick = { showHidden = !showHidden },
                    modifier = Modifier.height(48.dp),
                    color = DashAmber,
                    active = showHidden,
                    enabled = hidden.isNotEmpty() || showHidden
                )
            }
            if (BuildConfig.HOLO_DESIGN) {
                androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(filters.size) { i ->
                        com.aiconsole.kittlauncher2.ui.components.SkinTab(filters[i], filters[i] == filter, { filter = filters[i] }, Modifier.height(40.dp))
                    }
                }
            } else {
                ChoiceRow(filters, filter, { it }, { filter = it }, color = DashAmber)
            }
            selected?.let { app ->
                AppActions(
                    app = app,
                    pinned = app.key in pinned,
                    hidden = app.key in hidden,
                    onPin = {
                        settings.pinnedApps.set(AppLists.encode(AppLists.toggle(pinned, app.key)))
                        selected = null
                    },
                    onHide = {
                        settings.hiddenApps.set(AppLists.encode(AppLists.toggle(hidden.toList(), app.key)))
                        selected = null
                    },
                    onInfo = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.packageName}".toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: ActivityNotFoundException) {
                        }
                        selected = null
                    },
                    onCancel = { selected = null }
                )
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.key }) { app ->
                        DrawerTile(
                            app = app,
                            pinned = app.key in pinned,
                            hidden = app.key in hidden,
                            onClick = { onLaunch(app) },
                            onLongClick = { selected = app }
                        )
                    }
                }
                if (query.isBlank() && filter == ALL) {
                    Column(
                        Modifier.width(32.dp).fillMaxHeight().padding(start = 4.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppLists.RAIL.forEach { letter ->
                            val present = letter in sections
                            Text(
                                text = letter.toString(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clickable(enabled = present) {
                                        AppLists.firstIndexOf(shown.map { it.label }, letter)?.let {
                                            scope.launch { gridState.scrollToItem(it) }
                                        }
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (present) DashAmber else DashGrey.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppActions(
    app: LauncherApp,
    pinned: Boolean,
    hidden: Boolean,
    onPin: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(KittBlack)
            .border(1.dp, DashAmber, MaterialTheme.shapes.small)
            .padding(6.dp)
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app, Modifier.size(36.dp))
        Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = DashWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
        KittButton(if (pinned) "Unpin" else "Pin to home", onPin, Modifier.fillMaxHeight(), LedGreen, active = true)
        KittButton(if (hidden) "Unhide" else "Hide", onHide, Modifier.fillMaxHeight(), DashAmber)
        KittButton("Info", onInfo, Modifier.fillMaxHeight(), DashAmber)
        KittButton("X", onCancel, Modifier.fillMaxHeight().width(48.dp), DashGrey)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerTile(app: LauncherApp, pinned: Boolean, hidden: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .height(104.dp)
            .clip(shape)
            .background(KittBlack)
            .then(if (BuildConfig.HOLO_DESIGN) Modifier.skinFrame(shape, if (pinned) ScannerRed else KittBezel) else Modifier.border(1.dp, if (pinned) ScannerRed else KittBezel, shape))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (hidden) 0.4f else 1f)
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(app, Modifier.size(48.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.labelMedium,
                color = DashWhite,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (pinned) {
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(8.dp).background(ScannerRed))
        }
    }
}

private const val ALL = "All"
private const val MOST_USED = "Most used"
