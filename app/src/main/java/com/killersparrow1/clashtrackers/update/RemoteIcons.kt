package com.killersparrow1.clashtrackers.update

import android.content.Context
import com.killersparrow1.clashtrackers.data.MaxDataStore
import com.killersparrow1.clashtrackers.widget.WidgetIcons
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pictures for items the app was built without (new buildings or troops in newer game data). The game data says where
 * each item's picture is on assets.clashk.ing (ClashKing's asset site); anything the app has no icon for is downloaded
 * once into the app's own storage and then used like a bundled icon. Never replaces a bundled icon.
 */
object RemoteIcons {
    private const val HOST = "https://assets.clashk.ing/"
    private const val MAX_BYTES = 600_000
    private const val RETRY_AFTER_MS = 6 * 3_600_000L
    private const val PREFS = "remote_icons"

    /** Downloads the missing icons (a few at a time). Returns how many arrived. Quiet on failure; retried later. */
    suspend fun fillMissing(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val icons = WidgetIcons.get(app)
        val have = icons.available
        val missing = MaxDataStore.get(app).items.entries
            .filter { (id, item) -> item.icon != null && "$id.webp" !in have && "$id.png" !in have }
        if (missing.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_try", 0L) < RETRY_AFTER_MS && prefs.getInt("last_missing", -1) == missing.size) return@withContext 0
        prefs.edit().putLong("last_try", now).putInt("last_missing", missing.size).apply()
        val dir = File(app.filesDir, "remote_icons").also { it.mkdirs() }
        var saved = 0
        for ((id, item) in missing.take(60)) {
            val path = item.icon ?: continue
            if (!safePath(path)) continue
            val bytes = runCatching { download(HOST + path) }.getOrNull() ?: continue
            File(dir, "$id.webp").writeBytes(bytes)
            saved++
        }
        if (saved > 0) {
            icons.refreshRemote()
            MaxDataStore.touch()
            // Widgets drew a placeholder for these; draw them again with the new pictures.
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
