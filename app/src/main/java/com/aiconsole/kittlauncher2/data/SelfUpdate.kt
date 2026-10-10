package com.aiconsole.kittlauncher2.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.aiconsole.kittlauncher2.assistant.assistantHttpClient
import com.aiconsole.kittlauncher2.assistant.await
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

/** A published release and its APK. */
data class ReleaseInfo(val tag: String, val apkUrl: String, val notes: String)

/** Plain-Kotlin version comparison for update checks ("v1.0.42" vs "1.0.37"). */
object Versions {
    fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').mapNotNull { it.toIntOrNull() }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun isValidRepo(repo: String): Boolean = repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))
}

/**
 * Checks the project's GitHub Releases for a newer signed APK and installs it through Android's
 * package installer (the driver confirms). Android only accepts an update signed with the same
 * key as the installed app, so this works for release builds from the project's CI.
 */
class SelfUpdate(private val context: Context) {

    private val http by lazy { assistantHttpClient() }

    suspend fun latest(repo: String): ReleaseInfo = withContext(Dispatchers.IO) {
        if (!Versions.isValidRepo(repo)) throw IOException("Enter the repository as owner/name")
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(request).await().use { r ->
            if (r.code == 404) throw IOException("No releases published yet")
            if (!r.isSuccessful) throw IOException("GitHub returned HTTP ${r.code}")
            r.body.string()
        }
        try {
            val json = JSONObject(body)
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?: throw IOException("The latest release has no APK")
            ReleaseInfo(json.getString("tag_name"), apk.getString("browser_download_url"), json.optString("body"))
        } catch (e: JSONException) {
            throw IOException("Unexpected response from GitHub", e)
        }
    }

    /** Downloads [release] and hands it to the package installer, which asks the driver to confirm. */
    suspend fun install(release: ReleaseInfo) {
        val apk = withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "update.apk")
            http.newCall(Request.Builder().url(release.apkUrl).build()).await().use { r ->
                if (!r.isSuccessful) throw IOException("Download failed: HTTP ${r.code}")
                file.outputStream().use { out -> r.body.byteStream().copyTo(out) }
            }
            file
        }
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("kitt-update.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                registerStatusReceiver()
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val intent = PendingIntent.getBroadcast(context, sessionId, Intent(ACTION).setPackage(context.packageName), flags)
                session.commit(intent.intentSender)
            }
        }
    }

    /** The installer reports back here; when it needs the driver's confirmation, show its screen. */
    private fun registerStatusReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                    c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private companion object {
        const val ACTION = "com.aiconsole.kittlauncher2.UPDATE_STATUS"
    }
}
