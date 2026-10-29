from pathlib import Path

p = Path('app/src/main/java/com/aiconsole/kittlauncher2/ui/components/CockpitSkins.kt')
s = p.read_text()

def replace(old, new):
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'Hologram patch expected one anchor, found {count}: {old[:100]!r}')
    s = s.replace(old, new, 1)

replace('import androidx.compose.foundation.gestures.detectTapGestures\n', 'import androidx.compose.foundation.gestures.detectTapGestures\nimport androidx.compose.foundation.gestures.detectHorizontalDragGestures\n')
replace('    fun fresh(id: String) = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 15_000 }?.value', '    fun fresh(id: String) = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp in 0L..14_999L && it.value.isFinite() }?.value')
replace('CarReadouts.PIDS)\n        onDispose { obd.unwatch("vehicle_hud") }', 'CarReadouts.PIDS + PidCatalog.MAF)\n        onDispose { obd.unwatch("vehicle_hud") }')
replace('val statPids = remember { CarReadouts.PIDS + TireIds + PidCatalog.COOLANT + PidCatalog.MODULE_VOLTAGE + PidCatalog.RPM }', 'val statPids = remember { CarReadouts.PIDS + TireIds + PidCatalog.COOLANT + PidCatalog.MODULE_VOLTAGE + PidCatalog.RPM + PidCatalog.MAF }')
replace('    val turn = remember { mutableFloatStateOf(HOLO_REST_YAW) }', '    val turn = remember { mutableFloatStateOf(HOLO_REST_YAW) }\n    val dragging = remember { mutableStateOf(false) }')
replace('            turn.floatValue = Turntable.advance(turn.floatValue, elapsed)', '            if (!dragging.value) turn.floatValue = Turntable.advance(turn.floatValue, elapsed)')
replace('''        Callout("tire$i", "${skin.tireLabel} $name", tire(id), tireState(id), tireLevel(id), 4 + i, "w$i")''', '''        if (fresh(id) != null) Callout("tire$i", "${skin.tireLabel} $name", tire(id), tireState(id), tireLevel(id), 4 + i, "w$i")
        else holoFallbackCallout(i, metric, ::fresh)''')
replace('''            key.startsWith("tire") -> {''', '''            key.startsWith("fallback:") -> {
                val fallbackKey = key.substringAfter(":")
                val pid = holoFallbackPid(fallbackKey)
                CalloutDetail(c.title, c.value, c.level,
                    listOf("SOURCE PID" to pid) + lowHighAvg(pid) { holoFallbackText(fallbackKey, it, metric) },
                    holoFallbackAbout(fallbackKey))
            }
            key.startsWith("tire") -> {''')
replace('''            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { at ->''', '''            Modifier.fillMaxSize().pointerInput(lite) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging.value = true },
                    onDragEnd = { dragging.value = false },
                    onDragCancel = { dragging.value = false }
                ) { change, amount ->
                    change.consume()
                    turn.floatValue = Turntable.wrap(turn.floatValue + amount * 0.0085f)
                }
            }.pointerInput(Unit) {
                detectTapGestures { at ->''')
replace('            val yaw = if (lite) HOLO_REST_YAW else turn.floatValue', '            val yaw = turn.floatValue')
old = '            drawHologram(skin, book, image, second, mix, frame, ScannerRed, tireColor(TireIds[0]), tireColor(TireIds[1]), tireColor(TireIds[2]), tireColor(TireIds[3]), rpmFraction(), live, scanPhase.floatValue, callouts, selected)'
replace(old, '''            if (VehicleChoice.decode(vehicle) == VehicleChoice.Truck || skin !in listOf(CockpitSkin.Future, CockpitSkin.RoboCop)) {
                drawHoloWireframeVehicle(VehicleChoice.decode(vehicle), skin, yaw, scanPhase.floatValue, ScannerRed, callouts, selected)
            } else {
''' + old + '''
            }''')
p.write_text(s)

p = Path('app/src/main/java/com/aiconsole/kittlauncher2/ui/components/HoloWireframeRenderer.kt')
s = p.read_text()
replace('val mesh = if(truck)HoloWireMeshes.truck else HoloWireMeshes.coupe', 'val mesh = if(truck)ReferenceHoloMeshes.truck else ReferenceHoloMeshes.coupe')
replace('size.width*0.21f,boxes.bandHeight*0.40f', 'size.width*0.18f,boxes.bandHeight*0.34f')
replace('val wire = if(skin==CockpitSkin.Future || skin==CockpitSkin.RoboCop)secondary else accent', 'val wire = if(skin==CockpitSkin.Future || skin==CockpitSkin.RoboCop || (skin==CockpitSkin.Kitt && accent.red>0.7f && accent.green<0.4f))secondary else accent')
replace('val glow = if(skin==CockpitSkin.Future)Color(0xFFFF007F) else wire', 'val glow = if(skin==CockpitSkin.Future || (!truck && skin==CockpitSkin.Kitt && accent.red>0.7f && accent.green<0.4f))Color(0xFFFF007F) else wire')
replace('val floor = if(truck)-0.80f else -0.60f', 'val floor = if(truck)-0.83f else -0.66f')
replace('floor-(v.y-floor)*0.55f', 'floor-(v.y-floor)*0.75f')
replace('glow.copy(alpha=0.12f)', 'glow.copy(alpha=if(truck)0.12f else 0.18f)')
replace('else -> if(edge.part=="lights")Color.White else if(edge.part in listOf("interior","chassis"))secondary else wire', 'else -> if(edge.part=="lights")Color.White else if(!truck && edge.part.startsWith("w"))glow else if(edge.part in listOf("interior","chassis"))secondary else wire')
replace('base.copy(alpha=0.90f*strength)', 'base.copy(alpha=(0.82f*edge.weight).coerceIn(0.14f,0.95f)*strength)')
p.write_text(s)
