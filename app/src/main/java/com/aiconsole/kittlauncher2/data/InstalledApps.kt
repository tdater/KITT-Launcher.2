package com.aiconsole.kittlauncher2.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LauncherApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap,
    val group: AppGroup = AppGroup.Other
) {
    val key: String get() = "$packageName/$activityName"
}

object InstalledApps {

    private const val ICON_SIZE_PX = 144

    /** Rendered icon + label per component, keyed by the APK path and icon pack so changes refresh it. */
    private class Cached(val apkPath: String, val pack: String, val label: String, val icon: ImageBitmap)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()

    /** All launchable apps; with [iconPack] (a package name), icons come from that pack where it has one. */
    suspend fun load(context: Context, iconPack: String = ""): List<LauncherApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        queryLauncherActivities(pm)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                val key = "${info.activityInfo.packageName}/${info.activityInfo.name}"
                val apkPath = info.activityInfo.applicationInfo.sourceDir.orEmpty()
                val cached = cache[key]?.takeIf { it.apkPath == apkPath && it.pack == iconPack } ?: Cached(
                    apkPath = apkPath,
                    pack = iconPack,
                    label = info.loadLabel(pm).toString(),
                    icon = (iconPack.takeIf { it.isNotEmpty() }?.let { IconPacks.icon(context, it, info.activityInfo.packageName, info.activityInfo.name) }
                        ?: info.loadIcon(pm)).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap()
                ).also { cache[key] = it }
                val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    info.activityInfo.applicationInfo.category.takeIf { it >= 0 }
                } else {
                    null
                }
                LauncherApp(
                    label = cached.label,
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name,
                    icon = cached.icon,
                    group = AppGroup.of(category, cached.label, info.activityInfo.packageName)
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launchIntent(app: LauncherApp): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun launch(context: Context, app: LauncherApp) {
        try {
            context.startActivity(launchIntent(app))
        } catch (_: ActivityNotFoundException) {
            // App was uninstalled since the list was loaded; next resume refreshes it.
        }
    }

    private fun queryLauncherActivities(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }
}
