package com.killersparrow1.clashtrackers.update

import android.content.Context
import com.killersparrow1.clashtrackers.core.ClashKingData
import com.killersparrow1.clashtrackers.core.MaxData
import com.killersparrow1.clashtrackers.data.MaxDataStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Game data straight from ClashKing: `static_data.json` from ClashKingAssets (1.7 MB, served from assets.clashk.ing,
 * with the GitHub copy as a second source) is downloaded, converted into the app's format by [ClashKingData], checked,
 * and only then installed. It is fetched quietly when the app opens (at most once an hour, switchable in Settings) and
 * installed when it is valid, different and not for fewer Town Halls than the app already knows. Whatever goes wrong,
 * the data already in use stays untouched.
 *
 * Telling whether there is something new: the asset site sends a `Last-Modified` header, which is remembered and sent
 * back as `If-Modified-Since`, so an unchanged file costs a tiny "304 Not Modified" answer instead of a download.
 */
object DataUpdater {
    internal val SOURCES = listOf(
        "https://assets.clashk.ing/static_data.json",
        "https://raw.githubusercontent.com/ClashKingInc/ClashKingAssets/main/assets/static_data.json",
    )
    private const val MAX_BYTES = 12 * 1024 * 1024

    private const val PREFS = "game_data"
    private const val MIN_GAP_MS = 60L * 60 * 1000

    /** [text] is the converted data in the app's format; [lastModified] is the source's `Last-Modified` header, if any. */
    class Remote(val text: String, val data: MaxData, val lastModified: String? = null)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun auto(c: Context) = prefs(c).getBoolean("auto", true)
    fun setAuto(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto", on).apply()

    /** When data was last downloaded and installed (epoch millis), 0 if never. */
    fun installedAt(c: Context): Long = prefs(c).getLong("installed", 0L)

    /** The game data file in use, to tell whether a download is actually different. */
    fun currentText(c: Context): String? = runCatching {
        if (MaxDataStore.isDownloaded(c)) MaxDataStore.downloadedFile(c).readText()
        else c.applicationContext.assets.open("max_data.json").bufferedReader().use { it.readText() }
    }.getOrNull()

    /** True when [r] is different from, and not older (fewer Town Halls) than, the data in use. */
    internal fun isBetter(r: Remote, current: MaxData, currentText: String?): Boolean =
        ClashKingData.isBetter(r.text, r.data, current, currentText)

    /** Installs [r] when it is different and not older than the data in use. True if it was installed. */
    fun installIfBetter(c: Context, r: Remote): Boolean {
        if (!isBetter(r, MaxDataStore.get(c), currentText(c))) return false
        if (!MaxDataStore.install(c, r.text)) return false
        prefs(c).edit().putLong("installed", System.currentTimeMillis()).apply()
        return true
    }

    /** Called when the app opens. Quiet: any failure is ignored and the data in use stays. */
    suspend fun refreshIfDue(c: Context) {
        if (!auto(c)) return
        val now = System.currentTimeMillis()
        if (now - prefs(c).getLong("checked", 0L) in 0 until MIN_GAP_MS) return
        prefs(c).edit().putLong("checked", now).apply()
        runCatching {
            val remote = fetch(c, ifModifiedSince = prefs(c).getString("last_modified", null)) ?: return@runCatching
            installIfBetter(c, remote)
            // Remembered even when nothing needed installing, so the same file is not downloaded again next time.
            remote.lastModified?.let { prefs(c).edit().putString("last_modified", it).apply() }
        }
    }

    /**
     * Downloads, converts and checks the data. Returns null only when [ifModifiedSince] was given and the source says
     * nothing changed. Throws [IOException] when no source can be reached or the answer is not usable game data.
     */
    suspend fun fetch(c: Context, ifModifiedSince: String? = null): Remote? = withContext(Dispatchers.IO) {
        var failure: IOException? = null
        for (url in SOURCES) {
            try {
                val (text, modified) = download(url, ifModifiedSince) ?: return@withContext null
                val converted = try {
                    ClashKingData.convert(text, MaxDataStore.get(c))
                } catch (e: Exception) {
                    throw IOException("The data file is not valid game data")
                }
                val data = runCatching { MaxData.fromJson(converted) }.getOrNull()
                if (data == null || data.items.isEmpty() || data.maxTownHall < 1) throw IOException("The data file is not valid game data")
                return@withContext Remote(converted, data, modified)
            } catch (e: IOException) {
                failure = failure ?: e
            }
        }
        throw failure ?: IOException("No data source could be reached")
    }

    /** The text and `Last-Modified` of [url], or null on "304 Not Modified". */
    private fun download(url: String, ifModifiedSince: String?): Pair<String, String?>? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "ClashTrackers-data-check")
            if (ifModifiedSince != null) c.setRequestProperty("If-Modified-Since", ifModifiedSince)
            when (val code = c.responseCode) {
                200 -> Unit
                304 -> return null
                else -> throw IOException("The data file answered $code")
            }
            if (c.contentLengthLong > MAX_BYTES) throw IOException("The data file is too large")
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) throw IOException("The data file is too large")
            return bytes.toString(Charsets.UTF_8) to c.getHeaderField("Last-Modified")
        } finally {
            c.disconnect()
        }
    }
}
