package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.obd.PidReading
import kotlinx.coroutines.delay

/**
 * Live readings for the editions without the hologram car: the same six chips (speed, load,
 * throttle, fuel, intake, burn) in a row above [content], and tapping one opens the same info
 * card over the content with the range since start and what the number means.
 */
@Composable
fun ReadoutStrip(
    reading: (String) -> PidReading?,
    metric: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val obd = (LocalContext.current.applicationContext as KittApplication).obd
    DisposableEffect(Unit) {
        obd.watch("drive_readouts", CarReadouts.PIDS)
        onDispose { obd.unwatch("drive_readouts") }
    }
    fun fresh(id: String) = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 15_000 }?.value
    var selected by remember { mutableStateOf<String?>(null) }
    val stats = remember { CarReadouts.ALL.associate { it.key to ReadoutStats() } }
    val readingNow = rememberUpdatedState(reading)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            for (r in CarReadouts.ALL) {
                readingNow.value(r.pid)?.takeIf { System.currentTimeMillis() - it.timestamp < 15_000 }?.let { stats.getValue(r.key).add(it.value) }
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in CarReadouts.ALL) {
                ReadoutChip(
                    CarReadouts.chipLabel(r, metric), CarReadouts.chipValue(r.key, fresh(r.pid), metric), CarReadouts.level(r.key, fresh(r.pid)), selected == r.key,
                    { selected = if (selected == r.key) null else r.key }, Modifier.weight(1f).fillMaxSize()
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content(Modifier.fillMaxSize())
            selected?.let { key ->
                CarReadouts.byKey(key)?.let { spec ->
                    ReadoutCard(spec, fresh(spec.pid), stats.getValue(key), metric, { selected = null }, Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
