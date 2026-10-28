package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.obd.PidCatalog
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import kotlin.math.roundToInt

private const val SPARK = "01_0E"
private const val LAMBDA = "01_44"
private const val STOICH_AFR = 14.7

/**
 * Live ECU telemetry cards: air-fuel ratio (from the commanded equivalence ratio), spark advance,
 * MAF flow and intake air temperature, each with an amber segmented bar. A card shows "--" until
 * the car reports that value; cars don't all support every PID.
 */
@Composable
fun EcuCards(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val readings by app.obd.readings.collectAsState()
    val metric by app.settings.metricUnits.state.collectAsState()
    DisposableEffect(Unit) {
        app.obd.watch("ecu_cards", setOf(SPARK, LAMBDA, PidCatalog.MAF, PidCatalog.INTAKE_TEMP))
        onDispose { app.obd.unwatch("ecu_cards") }
    }
    fun value(id: String) = readings[id]?.takeIf { System.currentTimeMillis() - it.timestamp < 10_000 }?.value

    val afr = value(LAMBDA)?.let { it * STOICH_AFR }
    val spark = value(SPARK)
    val maf = value(PidCatalog.MAF)
    val iat = value(PidCatalog.INTAKE_TEMP)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Card("AFR", afr?.let { "%.1f".format(it) }, ((afr ?: 0.0) - 10) / 10, Modifier.weight(1f))
        Card("SPARK", spark?.let { "%.0f°".format(it) }, ((spark ?: 0.0) + 10) / 60, Modifier.weight(1f))
        Card("MAF", maf?.let { "%.1f g/s".format(it) }, (maf ?: 0.0) / 60, Modifier.weight(1f))
        Card(
            "IAT",
            iat?.let { if (metric) "${it.roundToInt()}°C" else "${(it * 9 / 5 + 32).roundToInt()}°F" },
            ((iat ?: 0.0) + 20) / 100,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun Card(label: String, value: String?, level: Double, modifier: Modifier) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier.fillMaxHeight().clip(shape).background(KittBlack).skinFrame(shape, DashAmber).padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
        Text(value ?: "--", style = MaterialTheme.typography.titleSmall, color = if (value == null) DashGrey else DashAmber, maxLines = 1)
        LedBarGauge(level.toFloat(), Modifier.fillMaxWidth().height(6.dp), segments = 10, color = DashAmber)
    }
}
