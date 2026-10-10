package com.aiconsole.kittlauncher2.ui.panes

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen

/** Widgets page: standard Android home-screen widgets (weather, music, calendar…) in a column. */
@Composable
fun WidgetsPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val manager = remember { AppWidgetManager.getInstance(context) }
    val stored by app.settings.widgetIds.state.collectAsState()
    val ids = stored.lines().mapNotNull { it.toIntOrNull() }
    var picking by remember { mutableStateOf(false) }
    var pendingId by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun save(list: List<Int>) = app.settings.widgetIds.set(list.distinct().joinToString("\n"))
    fun discard(id: Int) {
        app.widgetHost.deleteAppWidgetId(id)
        pendingId = null
    }

    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingId ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) save(ids + id) else discard(id)
        pendingId = null
    }

    fun configureOrAdd(id: Int, info: AppWidgetProviderInfo) {
        val activity = info.configure
        if (activity == null) {
            save(ids + id)
            pendingId = null
            return
        }
        pendingId = id
        try {
            configure.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(activity).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            )
        } catch (e: RuntimeException) {
            // Some configure screens aren't exported; add the widget with its defaults instead.
            save(ids + id)
            pendingId = null
        }
    }

    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingId ?: return@rememberLauncherForActivityResult
        val info = manager.getAppWidgetInfo(id)
        if (result.resultCode == Activity.RESULT_OK && info != null) configureOrAdd(id, info) else discard(id)
    }

    fun add(info: AppWidgetProviderInfo) {
        picking = false
        val id = app.widgetHost.allocateAppWidgetId()
        pendingId = id
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrAdd(id, info)
        } else {
            try {
                bind.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                )
            } catch (_: ActivityNotFoundException) {
                discard(id)
                message = "This head unit doesn't allow launchers to add widgets."
            }
        }
    }

    KittPanel(
        title = "Widgets",
        modifier = modifier,
        accent = DashAmber,
        trailing = {
            KittButton(if (picking) "Cancel" else "+ Add widget", { picking = !picking }, Modifier.height(32.dp), LedGreen, active = !picking)
        }
    ) {
        message?.let { HelpText(it, color = AlertRed) }
        if (picking) {
            val providers = remember { manager.installedProviders.sortedBy { it.loadLabel(context.packageManager).lowercase() } }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(providers, key = { it.provider.flattenToString() }) { info ->
                    val appLabel = remember(info) {
                        runCatching {
                            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(info.provider.packageName, 0)).toString()
                        }.getOrDefault(info.provider.packageName)
                    }
                    KittButton(
                        "${info.loadLabel(context.packageManager)} · $appLabel",
                        { add(info) },
                        Modifier.fillMaxWidth().height(48.dp),
                        DashAmber
                    )
                }
            }
            return@KittPanel
        }
        if (ids.isEmpty()) {
            HelpText("Add weather, calendar, music or any other app's home-screen widgets here.")
            return@KittPanel
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ids, key = { it }) { id ->
                val info = remember(id) { manager.getAppWidgetInfo(id) }
                if (info == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Widget no longer available", Modifier.weight(1f), color = DashGrey)
                        KittButton("Remove", { save(ids - id); discard(id) }, Modifier.height(36.dp), AlertRed)
                    }
                    return@items
                }
                val minHeight = with(LocalDensity.current) { info.minHeight.toDp() }.coerceAtLeast(110.dp)
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(KittBlack)
                        .border(1.dp, KittBezel, MaterialTheme.shapes.small).padding(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            info.loadLabel(context.packageManager).uppercase(),
                            Modifier.weight(1f).padding(start = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = DashWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        KittButton("X", { save(ids - id); discard(id) }, Modifier.height(28.dp).width(40.dp), AlertRed)
                    }
                    AndroidView(
                        factory = { ctx -> app.widgetHost.createView(ctx, id, info) },
                        modifier = Modifier.fillMaxWidth().height(minHeight)
                    )
                }
            }
        }
    }
}
