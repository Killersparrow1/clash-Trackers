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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    private val LEVEL_FILE = Regex("^(\\d+)_(\\d+)\\.webp$")
    private val LEVEL_PATH = Regex("^((?:buildings|traps)/.+/)level_\\d+\\.webp$")

    private fun fullRefreshDue(prefs: android.content.SharedPreferences, now: Long) =
        now - prefs.getLong("last_full", 0L) !in 0 until REFRESH_AFTER_MS

    /** The path of [level] of a building or trap, from the path of its newest picture; null for anything else. */
    internal fun levelPath(path: String, level: Int): String? =
        LEVEL_PATH.matchEntire(path)?.let { "${it.groupValues[1]}level_$level.webp" }

    private val tried = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val levelGate = Semaphore(4)
    @Volatile private var redrawJob: kotlinx.coroutines.Job? = null

    /**
     * Fetches the picture of [level] of building/trap [id] in the background, once (again after six hours if it
     * did not come). Widgets and screens draw what they have meanwhile and are redrawn when it arrives.
     */
    fun ensureLevel(context: Context, id: Int, level: Int) {
        if (level < 1) return
        val key = "${id}_$level"
        val now = System.currentTimeMillis()
        val last = tried[key]
        if (last != null && now - last < RETRY_AFTER_MS) return
        tried[key] = now
        val app = context.applicationContext
        scope.launch {
            val path = paths(app)[id.toString()]?.let { levelPath(it, level) } ?: return@launch
            if (!safePath(path)) return@launch
            val bytes = levelGate.withPermit { runCatching { download(HOST + path) }.getOrNull() } ?: return@launch
            val dir = File(app.filesDir, "remote_icons").also { it.mkdirs() }
            val tmp = File(dir, "$key.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(File(dir, "$key.webp"))) return@launch
            // Several arrive together: redraw once, shortly after the last one.
            redrawJob?.cancel()
            redrawJob = scope.launch {
                kotlinx.coroutines.delay(1_500)
                WidgetIcons.get(app).refreshRemote()
                MaxDataStore.touch()
                runCatching { com.killersparrow1.clashtrackers.widget.WidgetUpdater.updateAll(app, "icons") }
            }
        }
    }

    /** Downloads missing icons, and every icon again once a week. Returns how many were saved. Quiet on failure. */
    suspend fun sync(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dir = File(app.filesDir, "remote_icons").also { it.mkdirs() }
        val now = System.currentTimeMillis()
        val known = paths(app)
        val all = known.entries.toList()
        // Pictures of single levels fetched earlier (`<id>_<level>`) are refreshed with the rest.
        val levelled = if (!fullRefreshDue(prefs, now)) emptyList() else (dir.list() ?: emptyArray()).mapNotNull { file ->
            val m = LEVEL_FILE.matchEntire(file) ?: return@mapNotNull null
            val path = known[m.groupValues[1]]?.let { levelPath(it, m.groupValues[2].toInt()) } ?: return@mapNotNull null
            java.util.AbstractMap.SimpleEntry("${m.groupValues[1]}_${m.groupValues[2]}", path)
        }
        val refreshAll = fullRefreshDue(prefs, now)
        val todo = (all + levelled).filter { (name, _) -> refreshAll || !File(dir, "$name.webp").exists() }
        if (todo.isEmpty()) return@withContext 0
        if (!refreshAll && now - prefs.getLong("last_try", 0L) < RETRY_AFTER_MS) return@withContext 0
        prefs.edit().putLong("last_try", now).apply()

        val gate = Semaphore(4)
        val saved = coroutineScope {
            todo.map { (name, path) ->
                async {
                    gate.withPermit {
                        if (!safePath(path)) return@withPermit false
                        val bytes = runCatching { download(HOST + path) }.getOrNull() ?: return@withPermit false
                        val tmp = File(dir, "$name.tmp")
                        tmp.writeBytes(bytes)
                        tmp.renameTo(File(dir, "$name.webp"))
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

    /**
     * Icon name (a game data id such as `1000008`, or widget artwork such as `builder_potion`) -> path on the asset site.
     * The list built into the app (`assets/icon_paths.json`), plus the paths the game data names for newer items.
     */
    internal fun paths(context: Context): Map<String, String> {
        val out = linkedMapOf<String, String>()
        runCatching {
            context.assets.open("icon_paths.json").bufferedReader().use { text ->
                kotlinx.serialization.json.Json.parseToJsonElement(text.readText()).jsonObject["paths"]!!.jsonObject
                    .forEach { (k, v) -> out[k] = v.jsonPrimitive.content }
            }
        }
        MaxDataStore.get(context).items.forEach { (id, item) -> item.icon?.let { out[id.toString()] = it } }
        return out
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
