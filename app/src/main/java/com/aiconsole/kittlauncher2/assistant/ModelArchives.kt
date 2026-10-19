package com.aiconsole.kittlauncher2.assistant

import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Downloads a model archive (.tar.bz2 from sherpa-onnx's GitHub releases) and unpacks it into
 * [into], dropping the archive's top folder and refusing paths that climb out. The whole download
 * is checked against [sha256] (when given) before anything counts as installed. Blocking: call
 * from Dispatchers.IO. Shared by the offline voice and the offline wake word.
 */
object ModelArchives {

    fun download(http: OkHttpClient, url: String, sha256: String, expectedBytes: Long, into: File, onProgress: (Int) -> Unit) {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
            val body = r.body
            val total = body.contentLength().takeIf { it > 0 } ?: expectedBytes.coerceAtLeast(1)
            val digest = MessageDigest.getInstance("SHA-256")
            val counted = Counting(DigestInputStream(body.byteStream(), digest)) { read -> onProgress((read * 100 / total).toInt().coerceIn(0, 99)) }
            val raw = counted.buffered()
            val tar = TarArchiveInputStream(BZip2CompressorInputStream(raw))
            try {
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val path = LocalVoices.safeEntry(entry.name)?.let(LocalVoices::stripTop) ?: continue
                    val out = File(into, path)
                    if (!out.canonicalPath.startsWith(into.canonicalPath)) continue
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                    }
                }
                // The checksum covers the whole download: read what the unpacker left, then compare.
                val rest = ByteArray(8192)
                while (raw.read(rest) >= 0) Unit
                val sum = digest.digest().joinToString("") { "%02x".format(it) }
                if (sha256.isNotEmpty() && !sum.equals(sha256, ignoreCase = true)) {
                    throw IOException("The download was damaged or cut off (checksum didn't match)")
                }
            } finally {
                tar.close()
            }
        }
    }

    /** Downloads a single model file to [target] (via a .part file), checked against [sha256]. */
    fun downloadFile(http: OkHttpClient, url: String, sha256: String, expectedBytes: Long, target: File, onProgress: (Int) -> Unit) {
        val part = File(target.path + ".part")
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
                val body = r.body
                val total = body.contentLength().takeIf { it > 0 } ?: expectedBytes.coerceAtLeast(1)
                val digest = MessageDigest.getInstance("SHA-256")
                Counting(DigestInputStream(body.byteStream(), digest)) { read -> onProgress((read * 100 / total).toInt().coerceIn(0, 99)) }
                    .use { input -> part.outputStream().use { input.copyTo(it, 64 * 1024) } }
                val sum = digest.digest().joinToString("") { "%02x".format(it) }
                if (sha256.isNotEmpty() && !sum.equals(sha256, ignoreCase = true)) {
                    throw IOException("The download was damaged or cut off (checksum didn't match)")
                }
            }
            target.delete()
            if (!part.renameTo(target)) throw IOException("Couldn't save the model")
        } finally {
            part.delete()
        }
    }

    private class Counting(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L
        private var lastReport = 0L

        override fun read(): Int = super.read().also { if (it >= 0) bump(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bump(it.toLong()) }

        private fun bump(n: Long) {
            count += n
            if (count - lastReport >= 256 * 1024) {
                lastReport = count
                onRead(count)
            }
        }
    }
}
