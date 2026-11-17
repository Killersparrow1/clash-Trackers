package com.killersparrow1.clashtrackers.update

import android.content.Context
import com.killersparrow1.clashtrackers.data.MaxDataStore
import com.killersparrow1.clashtrackers.widget.WidgetIcons
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Pictures from ClashKing's asset site (assets.clashk.ing). The game data says where each item's picture is; the app
 * downloads them into its own storage and uses them ahead of the icons built into the app (those stay as the
 * placeholder until a picture has arrived). Everything is fetched the first time, then refreshed about once a week.
 */
object RemoteIcons {
    private const val HOST = "https://assets.clashk.ing/"
    private const val MAX_BYTES = 600_000
    private const val RETRY_AFTER_MS = 6 * 3_600_000L
    private const val REFRESH_AFTER_MS = 7 * 24 * 3_600_000L
    private const val PREFS = "remote_icons"

    /** Downloads missing icons, and every icon again once a week. Returns how many were saved. Quiet on failure. */
    suspend fun sync(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dir = File(app.filesDir, "remote_icons").also { it.mkdirs() }
        val now = System.currentTimeMillis()
        val all = MaxDataStore.get(app).items.entries.filter { it.value.icon != null }
        val refreshAll = now - prefs.getLong("last_full", 0L) !in 0 until REFRESH_AFTER_MS
        val todo = all.filter { (id, _) -> refreshAll || !File(dir, "$id.webp").exists() }
        if (todo.isEmpty()) return@withContext 0
        if (!refreshAll && now - prefs.getLong("last_try", 0L) < RETRY_AFTER_MS) return@withContext 0
        prefs.edit().putLong("last_try", now).apply()

        val gate = Semaphore(4)
        val saved = coroutineScope {
            todo.map { (id, item) ->
                async {
                    gate.withPermit {
                        val path = item.icon ?: return@withPermit false
                        if (!safePath(path)) return@withPermit false
                        val bytes = runCatching { download(HOST + path) }.getOrNull() ?: return@withPermit false
                        val tmp = File(dir, "$id.tmp")
                        tmp.writeBytes(bytes)
                        tmp.renameTo(File(dir, "$id.webp"))
                    }
                }
            }.awaitAll().count { it }
        }
        // A full refresh only counts as done when (nearly) everything came through.
        if (refreshAll && saved >= todo.size * 0.9) prefs.edit().putLong("last_full", now).apply()
        if (saved > 0) {
            WidgetIcons.get(app).refreshRemote()
            MaxDataStore.touch()
            // Widgets drew the old pictures; draw them again.
            runCatching { com.killersparrow1.clashtrackers.widget.WidgetUpdater.updateAll(app, "icons") }
        }
        saved
    }

    /** Only plain relative paths made of letters, digits and `_ - . /` are allowed. */
    internal fun safePath(path: String): Boolean =
        path.isNotEmpty() && path.length < 200 && !path.contains("..") && !path.startsWith("/") &&
            path.all { it.isLetterOrDigit() || it in "_-./" } && path.endsWith(".webp")

    private fun download(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = false
        try {
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            // WebP files start with "RIFF....WEBP".
            val ok = bytes.size in 12..MAX_BYTES &&
                String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
            return bytes.takeIf { ok }
        } finally {
            conn.disconnect()
        }
    }
}
