package com.aiconsole.knightriderlauncher.ui.diagnostics

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.obd.AdapterConfig
import com.aiconsole.knightriderlauncher.obd.AdapterDiscovery
import com.aiconsole.knightriderlauncher.obd.AdapterType
import com.aiconsole.knightriderlauncher.obd.ManufacturerProfiles
import com.aiconsole.knightriderlauncher.obd.ObdConnectionService
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.KittToggle
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val PROTOCOLS = listOf(
    "0" to "Auto",
    "6" to "CAN 11/500",
    "7" to "CAN 29/500",
    "8" to "CAN 11/250",
    "9" to "CAN 29/250",
    "1" to "J1850 PWM",
    "2" to "J1850 VPW",
    "3" to "ISO 9141",
    "4" to "KWP slow",
    "5" to "KWP fast"
)

/** Adapter selection, connection control, vehicle identity and manufacturer options. */
@Composable
fun LinkTab(app: KittApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by app.obd.state.collectAsState()
    val hz by app.obd.refreshHz.collectAsState()
    val profile by app.obd.profile.collectAsState()
    val manufacturerChoice by app.settings.manufacturerProfile.state.collectAsState()
    val protocol by app.settings.obdProtocol.state.collectAsState()
    val fastPolling by app.settings.obdFastPolling.state.collectAsState()
    val autoConnect by app.settings.obdAutoConnect.state.collectAsState()
    var saved by remember { mutableStateOf(app.savedAdapter()) }
    var hasPermission by remember { mutableStateOf(AdapterDiscovery.hasPermissions(context)) }
    var paired by remember { mutableStateOf(if (hasPermission) AdapterDiscovery.pairedDevices(context) else emptyList()) }
    val bleFound = remember { mutableStateListOf<AdapterConfig>() }
    val classicFound = remember { mutableStateListOf<AdapterConfig>() }
    var scanning by remember { mutableStateOf<Job?>(null) }
    var wifiAddress by remember { mutableStateOf("192.168.0.10:35000") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = AdapterDiscovery.hasPermissions(context)
        if (hasPermission) paired = AdapterDiscovery.pairedDevices(context)
    }

    fun requestPermissions() {
        val extra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
        permissionLauncher.launch(AdapterDiscovery.requiredPermissions() + extra)
    }

    fun choose(config: AdapterConfig) {
        app.saveAdapter(config)
        saved = config
        if (state !is ObdState.Idle) ObdConnectionService.start(context) // Reconnect to the new adapter.
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionHeader("Vehicle link", color = LedGreen) }
        item {
            val info = (state as? ObdState.Connected)?.info
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val (status, color) = when (val s = state) {
                    is ObdState.Connected -> "CONNECTED" to LedGreen
                    is ObdState.Connecting -> s.stage.uppercase() to DashAmber
                    is ObdState.Error -> "${s.message} · retry in ${s.retryInSeconds}s" to AlertRed
                    ObdState.Idle -> "NOT CONNECTED" to DashGrey
                }
                Text(status, style = MaterialTheme.typography.titleMedium, color = color)
                Text("Adapter: ${saved?.let { "${it.name} (${it.type.label})" } ?: "none selected"}", style = MaterialTheme.typography.bodyMedium, color = DashWhite)
                if (info != null) {
                    Text("ELM: ${info.elmVersion} · ${info.protocol}", style = MaterialTheme.typography.bodyMedium, color = DashWhite)
                    Text("VIN: ${info.vin ?: "not reported"}", style = MaterialTheme.typography.bodyMedium, color = DashWhite)
                    Text(
                        "Detected: ${info.detectedProfile?.name ?: "unknown make"} · ${info.supportedPidCount} PIDs · " +
                            "%.1f Hz".format(java.util.Locale.US, hz),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DashWhite
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittButton(
                    label = "Connect",
                    onClick = {
                        if (!hasPermission && saved?.type != AdapterType.WIFI) requestPermissions()
                        else ObdConnectionService.start(context)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = LedGreen,
                    active = true,
                    enabled = saved != null
                )
                KittButton(
                    label = "Disconnect",
                    onClick = { ObdConnectionService.stop(context) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = AlertRed,
                    enabled = state !is ObdState.Idle
                )
            }
        }
        item { KittToggle("Auto-connect", autoConnect, { app.settings.obdAutoConnect.set(it) }, description = "Reconnect whenever the launcher starts.") }

        item { SectionHeader("Bluetooth adapters", color = DashAmber) }
        if (!hasPermission) {
            item {
                KittButton("Allow Bluetooth access", ::requestPermissions, Modifier.fillMaxWidth().height(44.dp), DashAmber, active = true)
            }
        } else {
            item {
                HelpText(
                    "Scan to find your adapter; it doesn't need pairing in Android settings first. " +
                        "If Android asks for a PIN when connecting, it's usually 1234 or 0000."
                )
            }
            if (AdapterDiscovery.scanNeedsLocationEnabled(context)) {
                item { HelpText("Turn on Location: this Android version finds no Bluetooth devices while it's off.", color = AlertRed) }
            }
            items(paired, key = { "p_${it.address}" }) { device ->
                KittButton(
                    label = "${device.name}${if (AdapterDiscovery.isLikelyObd(device.name)) "  · OBD" else ""}",
                    onClick = { choose(device) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    color = LedGreen,
                    active = saved?.address == device.address
                )
            }
            item {
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KittButton(
                        label = if (scanning != null) "Scanning…" else "Scan for adapters",
                        onClick = {
                            bleFound.clear()
                            classicFound.clear()
                            paired = AdapterDiscovery.pairedDevices(context)
                            scanning = scope.launch {
                                val classic = launch {
                                    withTimeoutOrNull(15_000) {
                                        AdapterDiscovery.scanClassic(context).collect { found ->
                                            val known = classicFound.any { it.address == found.address } ||
                                                paired.any { it.address == found.address }
                                            if (!known) classicFound += found
                                        }
                                    }
                                }
                                withTimeoutOrNull(10_000) {
                                    AdapterDiscovery.scanBle(context).collect { found ->
                                        if (bleFound.none { it.address == found.address }) bleFound += found
                                    }
                                }
                                classic.join()
                                scanning = null
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = DashAmber,
                        enabled = scanning == null
                    )
                    KittButton(
                        label = "Pair new device",
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: ActivityNotFoundException) {
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = DashAmber
                    )
                }
            }
            items(classicFound.sortedByDescending { AdapterDiscovery.isLikelyObd(it.name) }, key = { "c_${it.address}" }) { device ->
                KittButton(
                    label = "BT · ${device.name}${if (AdapterDiscovery.isLikelyObd(device.name)) "  · OBD" else ""}",
                    onClick = { choose(device) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    color = LedGreen,
                    active = saved?.address == device.address
                )
            }
            items(bleFound.sortedByDescending { AdapterDiscovery.isLikelyObd(it.name) }, key = { "b_${it.address}" }) { device ->
                KittButton(
                    label = "BLE · ${device.name}",
                    onClick = { choose(device) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    color = LedGreen,
                    active = saved?.address == device.address
                )
            }
        }

        item { SectionHeader("Wi-Fi adapter", color = DashAmber) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittTextField(wifiAddress, { wifiAddress = it }, "192.168.0.10:35000", Modifier.weight(1f))
                KittButton(
                    label = "Use Wi-Fi",
                    onClick = { choose(AdapterConfig(AdapterType.WIFI, wifiAddress.trim(), "Wi-Fi ELM327")) },
                    modifier = Modifier.height(48.dp),
                    color = LedGreen,
                    active = saved?.type == AdapterType.WIFI
                )
            }
        }

        item { SectionHeader("Manufacturer", color = DashAmber) }
        item {
            ChoiceRow(
                options = listOf(ManufacturerProfiles.AUTO) + ManufacturerProfiles.all.map { it.id },
                selected = manufacturerChoice,
                label = { id ->
                    if (id == ManufacturerProfiles.AUTO) "Auto (VIN)" else ManufacturerProfiles.byId(id)?.name?.substringBefore(" (") ?: id
                },
                onSelect = { app.settings.manufacturerProfile.set(it) }
            )
        }
        item {
            HelpText("Active: ${profile.name}. ECUs: ${profile.ecus.joinToString { "${it.name} ${it.header}" }}")
        }

        item { SectionHeader("Protocol & speed", color = DashAmber) }
        item {
            ChoiceRow(PROTOCOLS.map { it.first }, protocol, { code -> PROTOCOLS.first { it.first == code }.second }, {
                app.settings.obdProtocol.set(it)
            })
        }
        item {
            KittToggle(
                "Fast multi-PID polling",
                fastPolling,
                { app.settings.obdFastPolling.set(it) },
                description = "Up to 6 sensors per request on CAN cars. Turn off if values freeze on a clone adapter."
            )
        }
        item { HelpText("Protocol and speed changes apply on the next connection.") }
    }
}
