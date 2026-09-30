package com.aiconsole.knightriderlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.aiconsole.knightriderlauncher.assistant.AssistantPrompt
import com.aiconsole.knightriderlauncher.assistant.KittAlert
import com.aiconsole.knightriderlauncher.data.InstalledApps
import com.aiconsole.knightriderlauncher.data.LauncherApp
import com.aiconsole.knightriderlauncher.nav.NavBridge
import com.aiconsole.knightriderlauncher.nav.NavInstruction
import com.aiconsole.knightriderlauncher.obd.AdapterDiscovery
import com.aiconsole.knightriderlauncher.obd.AdapterType
import com.aiconsole.knightriderlauncher.obd.ObdConnectionService
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import com.aiconsole.knightriderlauncher.obd.PidReading
import com.aiconsole.knightriderlauncher.ui.assistant.AssistantOverlay
import com.aiconsole.knightriderlauncher.ui.components.VoiceModulator
import com.aiconsole.knightriderlauncher.ui.diagnostics.DiagnosticsScreen
import com.aiconsole.knightriderlauncher.ui.gauges.GaugeUi
import com.aiconsole.knightriderlauncher.ui.multiview.MultiViewScreen
import com.aiconsole.knightriderlauncher.ui.panes.ControlDock
import com.aiconsole.knightriderlauncher.ui.panes.HudState
import com.aiconsole.knightriderlauncher.ui.panes.NavigationPane
import com.aiconsole.knightriderlauncher.ui.panes.StatusGridPane
import com.aiconsole.knightriderlauncher.ui.scanner.KittScanner
import com.aiconsole.knightriderlauncher.ui.scanner.LauncherVisibility
import com.aiconsole.knightriderlauncher.ui.scanner.ScannerOverlayService
import com.aiconsole.knightriderlauncher.ui.settings.KittConfigScreen
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.KnightRiderLauncherTheme
import com.aiconsole.knightriderlauncher.ui.theme.MatrixRain
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.ThemeState
import com.aiconsole.knightriderlauncher.ui.theme.UiTheme
import com.aiconsole.knightriderlauncher.ui.trip.TripScreen
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<LauncherApp>>(emptyList())

    /** Bumped by the steering-wheel voice button (VOICE_COMMAND / ASSIST intents). */
    private var voiceTrigger by mutableIntStateOf(0)

    private val kitt: KittApplication get() = application as KittApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleVoiceIntent(intent)
        setContent {
            KnightRiderLauncherTheme {
                // This is the home screen: there is nothing to go back to.
                BackHandler {}
                val settings = kitt.settings
                val showMap by settings.showMap.state.collectAsState()
                val navNotifications by settings.navNotifications.state.collectAsState()
                val navInstruction by NavBridge.current.collectAsState()
                val rain by settings.matrixRain.state.collectAsState()
                val night by kitt.nightMode.night.collectAsState()
                LauncherRoot(
                    apps = apps,
                    onAppClick = { InstalledApps.launch(this, it) },
                    dash = rememberDashState(kitt),
                    voiceTrigger = voiceTrigger,
                    onAskKitt = { kitt.assistantInbox.value = AssistantPrompt(it, diagnostic = true) },
                    onDismissAlert = { kitt.alerts.dismiss() },
                    showMap = showMap,
                    nav = if (navNotifications) navInstruction else null,
                    rain = rain && ThemeState.theme == UiTheme.Colorado,
                    night = night
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        LauncherVisibility.visible.value = true
        kitt.nightMode.setActive(true)
        startVehicleSystems()
    }

    override fun onStop() {
        LauncherVisibility.visible.value = false
        kitt.nightMode.setActive(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Refresh on every return to home so installs/uninstalls show up (icons are cached).
        lifecycleScope.launch { apps = InstalledApps.load(applicationContext) }
    }

    private fun handleVoiceIntent(intent: Intent?) {
        if (intent?.action in VOICE_ACTIONS) voiceTrigger++
    }

    /** Services must be started while an activity is visible, so the launcher kicks them off. */
    private fun startVehicleSystems() {
        val settings = kitt.settings
        val adapter = kitt.savedAdapter()
        if (settings.obdAutoConnect.value && adapter != null && kitt.obd.state.value == ObdState.Idle &&
            (adapter.type == AdapterType.WIFI || AdapterDiscovery.hasPermissions(this))
        ) {
            ObdConnectionService.start(this)
        }
        if (settings.scannerOverlay.value && Settings.canDrawOverlays(this)) {
            ScannerOverlayService.start(this)
        }
        kitt.alerts.greetOnce()
    }

    private companion object {
        val VOICE_ACTIONS = setOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST)
    }
}

enum class LauncherOverlay { None, MultiView, Assistant, Diagnostics, Config, Trips }

/** Live vehicle state for the home screen. */
data class DashState(
    /** Read lazily so live OBD updates redraw only the HUD readouts, not the map. */
    val hud: () -> HudState = { HudState() },
    /** Gauge configuration only; live values come from [reading] so updates redraw just the tiles. */
    val gauges: List<GaugeUi> = emptyList(),
    val reading: (String) -> PidReading? = { null },
    val metric: Boolean = false,
    val alert: KittAlert? = null,
    val kittSpeaking: Boolean = false
)

@Composable
private fun rememberDashState(app: KittApplication): DashState {
    val state = app.obd.state.collectAsState()
    val readings = app.obd.readings.collectAsState()
    val defs = app.obd.definitions.collectAsState()
    val gaugeConfigs by app.gauges.gauges.collectAsState()
    val metric = app.settings.metricUnits.state.collectAsState()
    val alert by app.alerts.active.collectAsState()
    val speaking by app.kittVoice.speaking.collectAsState()
    val hz = app.obd.refreshHz.collectAsState()

    val hudState = remember {
        derivedStateOf {
            val isMetric = metric.value
            val speedDef = defs.value[PidCatalog.SPEED]
            val speed = readings.value[PidCatalog.SPEED]?.value
            val rpm = readings.value[PidCatalog.RPM]?.value
            val link = state.value
            HudState(
                speed = if (speed != null && speedDef != null) speedDef.displayValue(speed, isMetric).roundToInt().toString() else null,
                speedUnit = if (isMetric) "KM/H" else "MPH",
                rpmFraction = ((rpm ?: 0.0) / 7000.0).toFloat().coerceIn(0f, 1f),
                rpmText = rpm?.roundToInt()?.toString(),
                linkLabel = when (link) {
                    is ObdState.Connected -> "OBD LINK · ${"%.1f".format(Locale.US, hz.value)} HZ"
                    is ObdState.Connecting -> "LINKING"
                    is ObdState.Error -> "LINK ERROR"
                    ObdState.Idle -> "OBD OFFLINE"
                },
                linked = link is ObdState.Connected
            )
        }
    }
    val hud = remember(hudState) { { hudState.value } }
    val reading = remember(readings) { { pidId: String -> readings.value[pidId] } }
    return DashState(
        hud = hud,
        gauges = gaugeConfigs.map { GaugeUi(it, defs.value[it.pidId], null) },
        reading = reading,
        metric = metric.value,
        alert = alert,
        kittSpeaking = speaking
    )
}

private val ScannerHeight = 22.dp

/**
 * Responsive shell under a permanent KITT scanner strip.
 *
 * Tall (portrait head units, e.g. 9:16): stacked 50% map / 35% grid / 15% dock.
 * Wide (16:9 landscape): map and grid side by side above a 20% dock.
 * Full-screen features are overlays drawn below the scanner, so it never disappears.
 */
@Composable
fun LauncherRoot(
    apps: List<LauncherApp>,
    onAppClick: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    dash: DashState = DashState(),
    voiceTrigger: Int = 0,
    onAskKitt: (String) -> Unit = {},
    onDismissAlert: () -> Unit = {},
    showMap: Boolean = false,
    nav: NavInstruction? = null,
    rain: Boolean = false,
    night: Boolean = false
) {
    var overlay by rememberSaveable { mutableStateOf(LauncherOverlay.None) }
    var autoListen by remember { mutableStateOf(false) }
    val view = LocalView.current
    var mapArea by remember { mutableStateOf<android.graphics.Rect?>(null) }
    LaunchedEffect(voiceTrigger) {
        if (voiceTrigger > 0) {
            autoListen = true
            overlay = LauncherOverlay.Assistant
        }
    }

    val askKitt = { prompt: String ->
        onAskKitt(prompt)
        autoListen = false
        overlay = LauncherOverlay.Assistant
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Night: dim everything, scanner included, without blocking touches.
            .drawWithContent {
                drawContent()
                if (night) drawRect(Color.Black, alpha = NightDim)
            }
    ) {
        // Colorado OS: falling glyphs behind the translucent panels.
        if (rain) MatrixRain(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            KittScanner(Modifier.fillMaxWidth().height(ScannerHeight), alert = dash.alert?.severe == true)

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                val isTall = maxHeight >= maxWidth * 1.2f
                val gap = 8.dp
                val dockHeight = (maxHeight * if (isTall) 0.15f else 0.20f).coerceIn(112.dp, 280.dp)
                val portraitCellWidth = if (maxWidth >= 600.dp) 150.dp else 110.dp
                val open = { target: LauncherOverlay -> overlay = target }
                // The map stops tracking and redrawing while a full-screen overlay covers it.
                val mapActive = overlay == LauncherOverlay.None || overlay == LauncherOverlay.MultiView

                val dock = @Composable {
                    ControlDock(
                        onMultiView = { open(LauncherOverlay.MultiView) },
                        onAssistant = {
                            autoListen = false
                            open(LauncherOverlay.Assistant)
                        },
                        onDiagnostics = { open(LauncherOverlay.Diagnostics) },
                        onConfig = { open(LauncherOverlay.Config) },
                        modifier = Modifier.fillMaxWidth().height(dockHeight),
                        kittSpeaking = dash.kittSpeaking,
                        onTrip = { open(LauncherOverlay.Trips) },
                        metric = dash.metric
                    )
                }
                val grid = @Composable { gridModifier: Modifier, cellWidth: androidx.compose.ui.unit.Dp ->
                    StatusGridPane(
                        apps = apps,
                        onAppClick = onAppClick,
                        modifier = gridModifier,
                        minCellWidth = cellWidth,
                        gauges = dash.gauges,
                        metric = dash.metric,
                        onGaugeClick = { open(LauncherOverlay.Diagnostics) },
                        reading = dash.reading
                    )
                }
                val navPane = @Composable { paneModifier: Modifier ->
                    NavigationPane(
                        // Multi-View windows open over exactly this pane, leaving the rest of home usable.
                        paneModifier.onGloballyPositioned { mapArea = it.screenRect(view) },
                        dash.hud,
                        showMap = showMap,
                        mapActive = mapActive,
                        nav = nav
                    )
                }

                if (isTall) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        // 50 : 35 of the space above the dock keeps the 50/35/15 split
                        navPane(Modifier.fillMaxWidth().weight(0.50f))
                        grid(Modifier.fillMaxWidth().weight(0.35f), portraitCellWidth)
                        dock()
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            navPane(Modifier.fillMaxHeight().weight(0.60f))
                            grid(Modifier.fillMaxHeight().weight(0.40f), 120.dp)
                        }
                        dock()
                    }
                }

                dash.alert?.let { alert ->
                    AlertBanner(alert, onDismissAlert, Modifier.align(Alignment.TopCenter).fillMaxWidth())
                }

                val close = { overlay = LauncherOverlay.None }
                when (overlay) {
                    LauncherOverlay.None -> Unit
                    LauncherOverlay.MultiView -> MultiViewScreen(
                        apps = apps,
                        onDismiss = close,
                        modifier = Modifier.fillMaxSize(),
                        area = mapArea
                    )
                    LauncherOverlay.Assistant -> AssistantOverlay(
                        onDismiss = close,
                        modifier = Modifier.fillMaxSize(),
                        autoListen = autoListen
                    )
                    LauncherOverlay.Diagnostics -> DiagnosticsScreen(
                        onDismiss = close,
                        onAskKitt = askKitt,
                        modifier = Modifier.fillMaxSize()
                    )
                    LauncherOverlay.Config -> KittConfigScreen(onDismiss = close, modifier = Modifier.fillMaxSize())
                    LauncherOverlay.Trips -> TripScreen(
                        onDismiss = close,
                        onAskKitt = askKitt,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** This node's bounds in screen pixels, which is what freeform launch bounds are measured in. */
private fun LayoutCoordinates.screenRect(view: View): android.graphics.Rect {
    val origin = IntArray(2).also(view::getLocationOnScreen)
    val b = boundsInRoot()
    return android.graphics.Rect(
        origin[0] + b.left.roundToInt(),
        origin[1] + b.top.roundToInt(),
        origin[0] + b.right.roundToInt(),
        origin[1] + b.bottom.roundToInt()
    )
}

/** How much the night mode darkens the whole launcher. */
private const val NightDim = 0.45f

/** KITT speaking up: voice bars plus the message, tap to dismiss. */
@Composable
private fun AlertBanner(alert: KittAlert, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (alert.severe) ScannerRed else DashAmber
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(KittBlack.copy(alpha = 0.94f))
            .border(2.dp, color, MaterialTheme.shapes.medium)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VoiceModulator(active = true, modifier = Modifier.width(72.dp).height(44.dp), color = color)
        Text(
            text = alert.message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = DashWhite,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(name = "Portrait head unit 9:16", widthDp = 720, heightDp = 1280)
@Composable
private fun LauncherPortraitPreview() {
    KnightRiderLauncherTheme {
        LauncherRoot(apps = emptyList(), onAppClick = {})
    }
}

@Preview(name = "Landscape 16:9", widthDp = 1280, heightDp = 720)
@Composable
private fun LauncherLandscapePreview() {
    KnightRiderLauncherTheme {
        LauncherRoot(apps = emptyList(), onAppClick = {})
    }
}
