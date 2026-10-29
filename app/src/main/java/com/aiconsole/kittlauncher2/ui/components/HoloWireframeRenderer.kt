package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import com.aiconsole.kittlauncher2.obd.PidCatalog
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

internal fun holoFallbackPid(key: String): String = when (key) {
    "maf" -> PidCatalog.MAF
    "rpm" -> PidCatalog.RPM
    else -> requireNotNull(CarReadouts.byKey(key)).pid
}

internal fun holoFallbackText(key: String, value: Double?, metric: Boolean): String {
    if (value == null || !value.isFinite()) return "--"
    return when (key) {
        "maf" -> "%.1f g/s".format(java.util.Locale.US, value)
        "rpm" -> "${value.roundToInt()} RPM"
        else -> CarReadouts.text(key, value, metric)
    }
}

internal fun holoFallbackAbout(key: String): String =
    (CarReadouts.byKey(key)?.about ?: when (key) {
        "maf" -> "Mass airflow measured by the engine computer, in grams per second."
        else -> "Engine speed reported by the engine computer."
    }) + " This slot shows live engine telemetry because this tire has no fresh TPMS reading. It is not a tire-pressure estimate."

internal fun holoFallbackCallout(index: Int, metric: Boolean, value: (String) -> Double?): Callout {
    require(index in 0..3)
    fun fresh(key: String) = value(holoFallbackPid(key))?.takeIf { it.isFinite() }
    val key = if (index < 3) listOf("load", "throttle", "intake")[index] else when {
        fresh("flow") != null -> "flow"
        fresh("maf") != null -> "maf"
        else -> "rpm"
    }
    val v = fresh(key)
    val title = when (key) {
        "maf" -> "AIRFLOW"
        "rpm" -> "ENGINE RPM"
        else -> requireNotNull(CarReadouts.byKey(key)).label
    }
    val level = if (v == null) ReadoutLevel.Unknown else CarReadouts.level(key, v)
    return Callout("fallback:$key", title, holoFallbackText(key, v, metric), "NO TPMS", level, 4 + index, if (key == "flow") "fuel" else "engine")
}

internal data class HoloVertex(val x: Float, val y: Float, val z: Float)
internal data class HoloWireEdge(val a: HoloVertex, val b: HoloVertex, val part: String = "body", val weight: Float = 1f)
internal data class HoloWireMesh(val edges: List<HoloWireEdge>, val anchors: Map<String, HoloVertex>)

internal object HoloWireMeshes {
    val coupe by lazy { make(false) }
    val truck by lazy { make(true) }

    private fun make(truck: Boolean): HoloWireMesh {
        val edges = mutableListOf<HoloWireEdge>()
        fun line(a: HoloVertex, b: HoloVertex, part: String = "body", weight: Float = 1f) { edges += HoloWireEdge(a, b, part, weight) }
        fun loop(points: List<HoloVertex>, part: String = "body", closed: Boolean = true, weight: Float = 1f) {
            points.zipWithNext().forEach { (a, b) -> line(a, b, part, weight) }
            if (closed && points.size > 2) line(points.last(), points.first(), part, weight)
        }
        fun ring(cx: Float, cy: Float, cz: Float, radius: Float, part: String, weight: Float = 1f) =
            (0 until 24).map { i -> val a = 2 * PI * i / 24; HoloVertex(cx + radius * cos(a).toFloat(), cy + radius * sin(a).toFloat(), cz) }.also { loop(it, part, weight = weight) }
        val wx = if (truck) 1.28f else 1.15f
        val wy = if (truck) -0.36f else -0.24f
        val wz = if (truck) 0.72f else 0.67f
        val radius = if (truck) 0.43f else 0.35f
        val half = if (truck) 0.76f else 0.72f
        for (s in listOf(-1f, 1f)) {
            fun p(x: Float, y: Float, z: Float = half) = HoloVertex(x, y, s * z)
            val belt = if (truck) listOf(p(1.98f,0.32f),p(1.18f,0.38f),p(0.68f,0.36f),p(-0.45f,0.37f),p(-1.94f,0.39f)) else listOf(p(1.90f,-0.06f,0.58f),p(1.60f,0.08f),p(0.60f,0.16f),p(-0.50f,0.20f),p(-1.65f,0.21f),p(-1.90f,0.02f,0.62f))
            loop(belt, closed = false, weight = 1.3f)
            val roof = if (truck) listOf(p(1.16f,0.38f),p(0.68f,0.76f,0.64f),p(-0.42f,0.76f,0.64f),p(-0.45f,0.38f)) else listOf(p(0.55f,0.16f),p(-0.08f,0.52f,0.54f),p(-0.58f,0.52f,0.54f),p(-1.45f,0.24f))
            loop(roof, closed = false, weight = 1.3f)
            loop(listOf(p(1.90f,-0.15f),p(1.72f,-0.22f),p(0.72f,-0.22f),p(-0.72f,-0.22f),p(-1.72f,-0.22f),p(-1.94f,-0.12f)),closed=false)
            for (x in listOf(wx,-wx)) loop((0..12).map { i -> val a = PI*i/12; p(x + (radius+0.08f)*cos(a).toFloat(),wy+(radius+0.08f)*sin(a).toFloat(),half+0.07f) },closed=false,weight=1.3f)
            if (truck) {
                // Two side windows and two doors per side; one undivided windshield.
                loop(listOf(p(1.10f,0.39f),p(0.65f,0.72f,0.65f),p(0.16f,0.72f,0.65f),p(0.16f,0.39f)))
                loop(listOf(p(0.08f,0.39f),p(0.08f,0.72f,0.65f),p(-0.38f,0.72f,0.65f),p(-0.41f,0.39f)))
                for (x in listOf(0.12f,-0.44f,0.76f)) line(p(x,0.36f),p(x,-0.16f))
                for (x in listOf(0.28f,-0.26f)) line(p(x,0.28f,0.78f),p(x+0.15f,0.28f,0.78f),weight=1.4f)
                loop(listOf(p(0.74f,-0.19f),p(0.70f,-0.24f,0.87f),p(-0.44f,-0.24f,0.87f),p(-0.48f,-0.19f)))
                loop(listOf(p(0.68f,0.42f),p(0.64f,0.46f,0.96f),p(0.50f,0.46f,0.96f),p(0.50f,0.33f,0.96f),p(0.64f,0.33f,0.96f)))
                loop(listOf(p(-0.48f,0.39f),p(-0.48f,0.14f,0.64f),p(-1.92f,0.14f,0.64f),p(-1.94f,0.39f)),closed=false)
                loop(listOf(p(1.98f,0.33f,0.52f),p(1.98f,0.33f,0.74f),p(1.98f,0.06f,0.74f),p(1.98f,0.06f,0.52f)),part="lights",closed=false,weight=1.5f)
            } else {
                loop(listOf(p(0.54f,0.14f),p(0.48f,-0.17f),p(-0.48f,-0.17f),p(-0.45f,0.19f)),closed=false)
                loop(listOf(p(1.82f,-0.02f,0.42f),p(1.62f,0.06f,0.66f),p(1.52f,0.01f,0.68f),p(1.74f,-0.07f,0.46f)),"lights",weight=1.4f)
            }
            loop(listOf(p(-1.92f,0.22f,0.62f),p(-1.92f,0.02f,0.62f),p(-1.92f,0.02f,0.72f),p(-1.92f,0.22f,0.72f)),"lights")
        }
        fun cross(x: Float,y: Float,z: Float,part: String="body") = line(HoloVertex(x,y,-z),HoloVertex(x,y,z),part)
        if (truck) {
            for (y in listOf(0.06f,0.13f,0.20f,0.27f,0.33f)) cross(1.99f,y,0.50f,"lights")
            for (z in listOf(-0.48f,-0.32f,-0.16f,0f,0.16f,0.32f,0.48f)) line(HoloVertex(1.99f,0.05f,z),HoloVertex(1.99f,0.33f,z),"lights")
            cross(1.16f,0.38f,0.76f); cross(0.68f,0.76f,0.64f); cross(-0.42f,0.76f,0.64f); cross(-0.45f,0.38f,0.76f)
            cross(-1.94f,0.39f,0.76f); cross(-1.94f,-0.14f,0.76f)
            for (x in listOf(wx,-wx)) cross(x,wy,0.72f,"chassis")
            line(HoloVertex(wx,wy,0f),HoloVertex(-wx,wy,0f),"chassis")
        } else {
            cross(0.55f,0.16f,0.72f); cross(-0.08f,0.52f,0.54f); cross(-0.58f,0.52f,0.54f); cross(-1.45f,0.24f,0.72f)
            for (z in listOf(-0.28f,0.28f)) loop(listOf(HoloVertex(0.18f,-0.14f,z-0.15f),HoloVertex(0.18f,-0.14f,z+0.15f),HoloVertex(-0.18f,-0.14f,z+0.15f),HoloVertex(-0.34f,0.34f,z+0.13f),HoloVertex(-0.34f,0.34f,z-0.13f),HoloVertex(-0.18f,-0.14f,z-0.15f)),"interior")
        }
        for (x in listOf(0.8f,1.1f,1.4f,1.7f)) cross(x,if(truck)0.38f else 0.12f,if(truck)0.74f else 0.65f,"engine")
        val anchors = mutableMapOf("engine" to HoloVertex(1.1f,0.10f,0f),"battery" to HoloVertex(0.90f,0.14f,0.42f),"fuel" to HoloVertex(-1.05f,0.02f,0f),"roof" to HoloVertex(0f,if(truck)0.77f else 0.54f,0f))
        for (i in 0..3) {
            val x = if(i<2)wx else -wx; val z = if(i%2==0)wz else -wz; val part = "w$i"
            anchors[part] = HoloVertex(x,wy,z)
            val outerZ = z + if(z>0)0.13f else -0.13f
            val innerZ = z - if(z>0)0.13f else -0.13f
            val outer = ring(x,wy,outerZ,radius,part,1.3f)
            val inner = ring(x,wy,innerZ,radius,part,0.8f)
            val rim = ring(x,wy,outerZ,0.26f,part,1.2f)
            ring(x,wy,z,0.18f,part,0.6f)
            for (j in 0 until 24) {
                line(outer[j],inner[j],part,0.6f)
                if(j%3==0) line(HoloVertex(x,wy,outerZ),rim[j],part)
            }
        }
        return HoloWireMesh(edges,anchors)
    }
}

internal fun DrawScope.drawHoloWireframeVehicle(choice: VehicleChoice, skin: CockpitSkin, yaw: Float, phase: Float, accent: Color, callouts: List<Callout>, selected: String?) {
    val truck = choice == VehicleChoice.Truck
    val mesh = if(truck)HoloWireMeshes.truck else HoloWireMeshes.coupe
    val boxes = CalloutLayout.boxes(size.width,size.height,density)
    val cy = (boxes.bandTop+boxes.bandBottom)/2f
    val scale = minOf(size.width*0.21f,boxes.bandHeight*0.40f)
    val secondary = when(skin) { CockpitSkin.Future -> Color(0xFF00E5FF); CockpitSkin.RoboCop -> Color(0xFF40C4FF); else -> Color(0xFF00D9EF) }
    val wire = if(skin==CockpitSkin.Future || skin==CockpitSkin.RoboCop)secondary else accent
    val glow = if(skin==CockpitSkin.Future)Color(0xFFFF007F) else wire
    val floor = if(truck)-0.80f else -0.60f
    fun project(v: HoloVertex, reflection: Boolean=false): Offset {
        val y = if(reflection)floor-(v.y-floor)*0.55f else v.y
        val x = v.x*cos(yaw)+v.z*sin(yaw); val z = -v.x*sin(yaw)+v.z*cos(yaw)
        val py = y*cos(-0.14f)-z*sin(-0.14f)
        val depth = z*cos(-0.14f)+y*sin(-0.14f)
        val perspective = 4.6f/(4.6f-depth*0.42f).coerceAtLeast(1.8f)
        return Offset(size.width/2+x*scale*perspective,cy-py*scale*perspective)
    }
    val levels = callouts.groupBy { it.anchor }.mapValues { (_,v) -> v.maxBy { it.level.ordinal }.level }
    val pulse = 0.55f+0.45f*kotlin.math.abs(sin(phase*2f*PI.toFloat()))
    clipRect(top=boxes.bandTop,bottom=boxes.bandBottom) {
        val floorY = cy-floor*scale
        drawOval(Brush.radialGradient(listOf(glow.copy(alpha=0.38f),Color.Transparent),Offset(size.width/2,floorY),scale*1.8f),Offset(size.width/2-scale*1.8f,floorY-scale*0.26f),Size(scale*3.6f,scale*0.52f))
        drawFloorGrid(wire,floorY)
        for(edge in mesh.edges) {
            val a=project(edge.a,true); val b=project(edge.b,true)
            drawLine(glow.copy(alpha=0.12f),a,b,density*edge.weight,StrokeCap.Round)
        }
        for(edge in mesh.edges) {
            val level=levels[edge.part]
            val base=when(level) { ReadoutLevel.Bad -> levelColor(ReadoutLevel.Bad); ReadoutLevel.Warn -> levelColor(ReadoutLevel.Warn); ReadoutLevel.Unknown -> levelColor(ReadoutLevel.Unknown); else -> if(edge.part=="lights")Color.White else if(edge.part in listOf("interior","chassis"))secondary else wire }
            val strength=if(level==ReadoutLevel.Bad || level==ReadoutLevel.Warn)pulse else 1f
            val a=project(edge.a); val b=project(edge.b)
            drawLine(base.copy(alpha=0.20f*strength),a,b,3.2f*density*edge.weight,StrokeCap.Round)
            drawLine(base.copy(alpha=0.90f*strength),a,b,1.05f*density*edge.weight,StrokeCap.Round)
        }
    }
    drawCallouts(callouts,boxes,{ key -> project(mesh.anchors[key] ?: mesh.anchors.getValue("roof")) },wire,selected,::levelColor)
}
