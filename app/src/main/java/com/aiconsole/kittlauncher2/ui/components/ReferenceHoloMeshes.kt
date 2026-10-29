package com.aiconsole.kittlauncher2.ui.components

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.exp
import kotlin.math.abs

/** Reference-inspired geometry, in the same coordinate system as the live HUD anchors. */
internal object ReferenceHoloMeshes {
    val coupe by lazy { build(false) }
    val truck by lazy { build(true) }

    private fun build(truck: Boolean): HoloWireMesh {
        val edges = ArrayList<HoloWireEdge>(3500)
        fun v(x: Float,y: Float,z: Float) = HoloVertex(x,y,z)
        fun line(a: HoloVertex,b: HoloVertex,part: String="body",weight: Float=0.45f) { edges += HoloWireEdge(a,b,part,weight) }
        fun poly(points: List<HoloVertex>,part: String="body",closed: Boolean=true,weight: Float=1.15f) {
            points.zipWithNext().forEach { (a,b) -> line(a,b,part,weight) }
            if(closed && points.size>2) line(points.last(),points.first(),part,weight)
        }
        fun panel(a: HoloVertex,b: HoloVertex,c: HoloVertex,d: HoloVertex,nu: Int=10,nv: Int=4,part: String="body") {
            fun point(u: Float,t: Float): HoloVertex {
                fun mix(aa: Float,bb: Float,cc: Float,dd: Float)=(1-u)*(1-t)*aa+u*(1-t)*bb+u*t*cc+(1-u)*t*dd
                return v(mix(a.x,b.x,c.x,d.x),mix(a.y,b.y,c.y,d.y),mix(a.z,b.z,c.z,d.z))
            }
            for(i in 0..nu) line(point(i.toFloat()/nu,0f),point(i.toFloat()/nu,1f),part)
            for(j in 0..nv) line(point(0f,j.toFloat()/nv),point(1f,j.toFloat()/nv),part)
            poly(listOf(a,b,c,d),part)
        }
        val length=if(truck)2.35f else 2.08f
        val wheelX=if(truck)1.42f else 1.24f
        val wheelY=if(truck)-0.36f else -0.28f
        val tireR=if(truck)0.46f else 0.37f
        val half=if(truck)0.82f else 0.75f
        fun width(x: Float): Float { val t=(abs(x)-wheelX)/0.6f; return half*(0.84f+0.16f*exp(-t*t)) }
        fun opening(x: Float,y: Float): Boolean = listOf(-wheelX,wheelX).any { c -> (x-c)*(x-c)+(y-wheelY)*(y-wheelY)<(tireR+0.06f)*(tireR+0.06f) && y>wheelY }
        for(side in listOf(-1f,1f)) {
            fun p(x: Float,y: Float,z: Float=half)=v(x,y,side*z)
            val stepY=if(truck)0.13f else 0.105f
            val bottom=if(truck)-0.21f else -0.22f
            for(i in 0 until 52) {
                val x=-length+2*length*i/52; val xx=-length+2*length*(i+1)/52
                for(j in 0..4) {
                    val y=bottom+j*stepY
                    if(opening(x,y)||opening(xx,y)) continue
                    if(!truck && x<0.55f && xx> -0.65f) continue
                    line(p(x,y,width(x)),p(xx,y,width(xx)))
                }
            }
            for(i in 0..52) {
                val x=-length+2*length*i/52
                if(!truck && x in -0.65f..0.55f) continue
                for(j in 0 until 4) {
                    val y=bottom+j*stepY; val yy=y+stepY
                    if(!opening(x,y)&&!opening(x,yy)) line(p(x,y,width(x)),p(x,yy,width(x)))
                }
            }
            for(cx in listOf(-wheelX,wheelX)) {
                poly((0..24).map { i -> val a=PI*i/24; p(cx+(tireR+0.06f)*cos(a).toFloat(),wheelY+(tireR+0.06f)*sin(a).toFloat(),half+0.03f) },closed=false)
            }
            if(truck) {
                poly(listOf(p(1.05f,0.47f,0.80f),p(0.60f,0.98f,0.68f),p(-0.55f,0.98f,0.68f),p(-0.60f,0.46f,0.80f)),closed=false)
                // Exactly two side window panes on each side, separated by a B pillar.
                val windows=listOf(listOf(1.0f to 0.49f,0.57f to 0.94f,0.05f to 0.94f,0.05f to 0.49f),listOf(-0.02f to 0.49f,-0.02f to 0.94f,-0.51f to 0.94f,-0.56f to 0.49f))
                for(window in windows) poly(window.map { (x,y) -> p(x,y,0.80f-(y-0.47f)*0.24f) })
                for(x in listOf(-0.60f,0.015f,1.05f)) line(p(x,0.46f,0.81f),p(x,-0.18f,0.81f),weight=1.1f)
                for(x in listOf(-0.42f,0.23f)) line(p(x,0.34f,0.83f),p(x+0.16f,0.34f,0.83f),weight=1.5f)
                poly(listOf(p(-0.63f,0.46f),p(-length,0.46f),p(-length,0.08f,0.68f),p(-0.63f,0.08f,0.68f)))
                poly(listOf(p(1.02f,0.46f),p(0.91f,0.56f,1.02f),p(0.71f,0.56f,1.02f),p(0.71f,0.36f,1.02f),p(0.91f,0.36f,1.02f)))
                poly(listOf(p(1.05f,-0.19f),p(0.99f,-0.26f,0.91f),p(-0.60f,-0.26f,0.91f),p(-0.65f,-0.19f)))
                poly(listOf(p(length,0.38f,0.59f),p(length,0.38f,0.76f),p(length,0.04f,0.76f),p(length,0.04f,0.59f)),"lights",false,1.4f)
                for(x in listOf(-wheelX,wheelX)) {
                    line(p(x,wheelY,0.60f),p(x-0.10f,-0.02f,0.48f),"chassis",1.2f)
                    for(j in 0..10) {
                        val y=wheelY+j*0.03f
                        line(p(x-0.12f,y,0.50f),p(x+0.02f,y+0.015f,0.50f),"chassis",0.7f)
                    }
                }
            } else {
                poly(listOf(p(0.62f,0.22f,0.69f),p(0.05f,0.65f,0.55f),p(-0.58f,0.65f,0.55f),p(-1.18f,0.29f,0.70f)),closed=false)
                poly(listOf(p(0.55f,0.18f,0.74f),p(0.48f,-0.17f,0.74f),p(-0.64f,-0.17f,0.76f),p(-0.64f,0.25f,0.76f)),closed=false)
                poly(listOf(p(1.98f,0.04f,0.45f),p(1.76f,0.16f,0.63f),p(1.63f,0.09f,0.69f),p(1.89f,-0.02f,0.54f)),"lights",weight=1.4f)
                poly(listOf(p(0.82f,0.12f,0.74f),p(0.61f,0.10f,0.76f),p(0.72f,-0.02f,0.76f)),closed=false)
                poly(listOf(p(-1.99f,0.23f,0.48f),p(-1.80f,0.27f,0.67f),p(-1.85f,0.16f,0.70f),p(-2.03f,0.17f,0.48f)),"lights")
            }
        }
        if(truck) {
            panel(v(1.05f,0.47f,-0.80f),v(2.30f,0.42f,-0.72f),v(2.30f,0.42f,0.72f),v(1.05f,0.47f,0.80f),16,10,"engine")
            panel(v(0.60f,0.98f,-0.68f),v(-0.55f,0.98f,-0.68f),v(-0.55f,0.98f,0.68f),v(0.60f,0.98f,0.68f),10,8)
            // Windshield is one continuous pane, with no central frame or divider.
            poly(listOf(v(1.05f,0.47f,-0.80f),v(0.60f,0.98f,-0.68f),v(0.60f,0.98f,0.68f),v(1.05f,0.47f,0.80f)))
            poly(listOf(v(-0.55f,0.98f,-0.68f),v(-0.60f,0.46f,-0.80f),v(-0.60f,0.46f,0.80f),v(-0.55f,0.98f,0.68f)))
            panel(v(-0.63f,0.08f,-0.68f),v(-length,0.08f,-0.68f),v(-length,0.08f,0.68f),v(-0.63f,0.08f,0.68f),14,8,"fuel")
            panel(v(length,0.05f,-0.58f),v(length,0.38f,-0.58f),v(length,0.38f,0.58f),v(length,0.05f,0.58f),6,14,"lights")
            panel(v(-length,0.08f,-0.79f),v(-length,0.46f,-0.79f),v(-length,0.46f,0.79f),v(-length,0.08f,0.79f),6,8)
            poly(listOf(v(length,-0.15f,-0.82f),v(length+0.07f,-0.15f,-0.42f),v(length+0.07f,-0.15f,0.42f),v(length,-0.15f,0.82f)),closed=false)
            for(z in listOf(-0.36f,0.36f)) line(v(-2.15f,-0.21f,z),v(2.15f,-0.21f,z),"chassis",1.1f)
            for(x in listOf(-wheelX,wheelX)) {
                line(v(x,wheelY,-0.72f),v(x,wheelY,0.72f),"chassis",1.4f)
                poly((0 until 12).map { i -> val a=2*PI*i/12; v(x+0.14f*cos(a).toFloat(),wheelY+0.14f*sin(a).toFloat(),0f) },"chassis")
            }
            line(v(-wheelX,wheelY,0f),v(wheelX,wheelY,0f),"chassis",1.2f)
        } else {
            panel(v(0.62f,0.22f,-0.69f),v(2.03f,0.07f,-0.59f),v(2.03f,0.07f,0.59f),v(0.62f,0.22f,0.69f),20,12,"engine")
            panel(v(0.05f,0.65f,-0.55f),v(-0.58f,0.65f,-0.55f),v(-0.58f,0.65f,0.55f),v(0.05f,0.65f,0.55f),10,8)
            poly(listOf(v(0.62f,0.22f,-0.69f),v(0.05f,0.65f,-0.55f),v(0.05f,0.65f,0.55f),v(0.62f,0.22f,0.69f)))
            panel(v(-1.18f,0.29f,-0.70f),v(-2.03f,0.20f,-0.65f),v(-2.03f,0.20f,0.65f),v(-1.18f,0.29f,0.70f),12,8,"fuel")
            for(z in listOf(-0.28f,0.28f)) {
                panel(v(0.03f,-0.12f,z-0.14f),v(0.03f,-0.12f,z+0.14f),v(-0.37f,-0.10f,z+0.14f),v(-0.37f,-0.10f,z-0.14f),4,4,"interior")
                panel(v(-0.37f,-0.10f,z-0.14f),v(-0.37f,-0.10f,z+0.14f),v(-0.53f,0.39f,z+0.13f),v(-0.53f,0.39f,z-0.13f),4,6,"interior")
            }
            val steering=(0 until 24).map { i -> val a=2*PI*i/24; v(0.29f,0.17f+0.105f*cos(a).toFloat(),0.28f+0.105f*sin(a).toFloat()) }
            poly(steering,"interior")
            line(v(0.29f,0.17f,0.28f),steering[0],"interior",1f)
            line(v(0.29f,0.17f,0.28f),steering[8],"interior",1f)
            line(v(0.29f,0.17f,0.28f),steering[16],"interior",1f)
            panel(v(0.10f,-0.10f,-0.07f),v(-0.50f,-0.10f,-0.07f),v(-0.50f,-0.10f,0.07f),v(0.10f,-0.10f,0.07f),6,2,"interior")
        }
        val anchors=mutableMapOf("engine" to v(1.25f,if(truck)0.46f else 0.18f,0f),"battery" to v(0.98f,if(truck)0.43f else 0.15f,0.43f),"fuel" to v(-1.40f,0.06f,0f),"roof" to v(0f,if(truck)0.98f else 0.65f,0f))
        for(i in 0..3) {
            val cx=if(i<2)wheelX else -wheelX
            val side=if(i%2==0)1f else -1f
            val cz=side*(half-0.03f)
            val part="w$i"
            anchors[part]=v(cx,wheelY,cz)
            val minor=if(truck)0.11f else 0.09f
            val major=tireR-minor
            fun tire(a: Double,b: Double): HoloVertex {
                val r=major+minor*cos(b).toFloat()
                return v(cx+r*cos(a).toFloat(),wheelY+r*sin(a).toFloat(),cz+minor*sin(b).toFloat())
            }
            for(j in 0 until 40) for(k in 0 until 6) {
                val a=2*PI*j/40; val b=2*PI*k/6
                line(tire(a,b),tire(a+2*PI/40,b),part,0.48f)
                line(tire(a,b),tire(a,b+2*PI/6),part,0.40f)
                if(truck && k%2==0 && j%2==0) line(tire(a,b),tire(a+PI/40,b+PI/6),part,0.70f)
            }
            val faceZ=cz+side*(minor+0.01f)
            fun circle(radius: Float,z: Float,weight: Float)= (0 until 40).map { j -> val a=2*PI*j/40; v(cx+radius*cos(a).toFloat(),wheelY+radius*sin(a).toFloat(),z) }.also { poly(it,part,weight=weight) }
            val rimR=if(truck)0.27f else 0.26f
            circle(rimR,faceZ,1.15f); circle(rimR*0.90f,faceZ,0.65f); circle(0.07f,faceZ,0.9f); circle(rimR*0.73f,cz,0.5f)
            val spokes=if(truck)8 else 10
            for(j in 0 until spokes) {
                val a=2*PI*j/spokes
                for(delta in listOf(-0.045,0.045)) line(v(cx+0.06f*cos(a).toFloat(),wheelY+0.06f*sin(a).toFloat(),faceZ),v(cx+rimR*cos(a+delta).toFloat(),wheelY+rimR*sin(a+delta).toFloat(),faceZ),part,0.9f)
            }
        }
        // A separate battery assembly gives voltage warnings actual geometry to illuminate.
        panel(v(0.85f,0.10f,0.30f),v(1.10f,0.10f,0.30f),v(1.10f,0.10f,0.50f),v(0.85f,0.10f,0.50f),3,2,"battery")
        return HoloWireMesh(edges,anchors)
    }
}
