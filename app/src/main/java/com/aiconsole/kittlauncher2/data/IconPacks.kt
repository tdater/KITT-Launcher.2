package com.aiconsole.kittlauncher2.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

/** An installed icon pack app. */
data class IconPack(val packageName: String, val label: String)

/** Plain-Kotlin parsing of an icon pack's appfilter.xml. */
object AppFilter {
    private val ITEM = Regex("""<item\b[^>]*?component\s*=\s*"([^"]+)"[^>]*?drawable\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val COMPONENT = Regex("""ComponentInfo\{([^/}]+)/([^}]+)\}""")

    /** "package/activity" (activity fully qualified) to drawable name. */
    fun parse(xml: String): Map<String, String> = ITEM.findAll(xml).mapNotNull { m ->
        key(m.groupValues[1])?.let { it to m.groupValues[2] }
    }.toMap()

    /** Normalises "ComponentInfo{pkg/.Cls}" to "pkg/pkg.Cls"; null if it isn't a component. */
    fun key(component: String): String? {
        val m = COMPONENT.find(component) ?: return null
        val pkg = m.groupValues[1]
        val cls = m.groupValues[2].let { if (it.startsWith(".")) pkg + it else it }
        return "$pkg/$cls"
    }
}

/**
 * Third-party icon packs in the common ADW / Nova format: the pack declares a theme intent and
 * maps each app's launcher component to one of its drawables in appfilter.xml.
 */
object IconPacks {

    private val THEME_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme"
    )

    private class Loaded(val resources: Resources, val map: Map<String, String>)

    private val loaded = ConcurrentHashMap<String, Loaded>()

    fun installed(context: Context): List<IconPack> {
        val pm = context.packageManager
        return THEME_ACTIONS.flatMap { action ->
            val intent = Intent(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        }.map { IconPack(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** The pack's icon for [packageName]/[activityName], or null to keep the app's own. */
    fun icon(context: Context, pack: String, packageName: String, activityName: String): Drawable? {
        val p = load(context, pack) ?: return null
        val name = p.map["$packageName/$activityName"] ?: return null
        @SuppressLint("DiscouragedApi") // Icon packs are looked up by name by design.
        val id = p.resources.getIdentifier(name, "drawable", pack)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(p.resources, id, null) }.getOrNull()
    }

    private fun load(context: Context, pack: String): Loaded? = loaded[pack] ?: try {
        val res = context.packageManager.getResourcesForApplication(pack)
        Loaded(res, readAppFilter(res, pack)).also { loaded[pack] = it }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Resources.NotFoundException) {
        Log.w("IconPacks", "Icon pack $pack has no appfilter", e)
        null
    }

    @SuppressLint("DiscouragedApi")
    private fun readAppFilter(res: Resources, pack: String): Map<String, String> {
        val xmlId = res.getIdentifier("appfilter", "xml", pack)
        if (xmlId != 0) {
            val map = mutableMapOf<String, String>()
            res.getXml(xmlId).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component") ?: continue
                        val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                        AppFilter.key(component)?.let { map[it] = drawable }
                    }
                }
            }
            return map
        }
        return res.assets.open("appfilter.xml").use { AppFilter.parse(it.readBytes().toString(Charsets.UTF_8)) }
    }
}
