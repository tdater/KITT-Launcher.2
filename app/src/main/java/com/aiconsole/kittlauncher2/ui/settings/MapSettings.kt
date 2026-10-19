package com.aiconsole.kittlauncher2.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.data.SafeMode
import com.aiconsole.kittlauncher2.nav.OfflineMaps
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

/** Offline map files for dead zones (see [OfflineMaps]): add, list, delete, restart to apply. */
@Composable
fun OfflineMapSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(OfflineMaps.files(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var changed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = "Copying the map file…"
        scope.launch {
            message = try {
                val name = OfflineMaps.import(context, uri)
                files = OfflineMaps.files(context)
                changed = true
                "$name added. Restart KITT to use it."
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                e.message
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("OFFLINE MAP", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        HelpText(
            "Places you've already seen on the map stay viewable without signal. For dead zones, add a raster map file " +
                "of your area (.mbtiles, .sqlite or .gemf, e.g. made with MOBAC): the map uses it first. OpenStreetMap's " +
                "servers don't allow downloading whole areas, so KITT can't fetch one itself."
        )
        files.forEach { file ->
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${file.name} · ${file.length() / 1_048_576} MB", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = DashWhite, maxLines = 2)
                KittButton("Delete", {
                    OfflineMaps.remove(file)
                    files = OfflineMaps.files(context)
                    changed = true
                }, Modifier.height(40.dp).width(88.dp), DashGrey)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Add map file", { runCatching { picker.launch(arrayOf("*/*")) } }, Modifier.height(44.dp).width(150.dp), DashAmber)
            if (changed) KittButton("Restart KITT", { SafeMode.restart(context) }, Modifier.height(44.dp).width(150.dp), LedGreen)
        }
        message?.let { HelpText(it, color = DashAmber) }
    }
}
