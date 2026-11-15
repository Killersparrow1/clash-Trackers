package com.killersparrow1.clashtrackers.update

import android.content.Context
import com.killersparrow1.clashtrackers.core.MaxData
import com.killersparrow1.clashtrackers.data.MaxDataStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The newest game data file from the project's repository, checked before the app uses it. It is fetched quietly
 * when the app opens (at most once an hour, switchable in Settings) and installed when it is valid, different and
 * not for fewer Town Halls than the app already knows.
 */
object DataUpdater {
    private const val URL_TEXT = "https://raw.githubusercontent.com/Killersparrow1/clash-Trackers/main/app/src/main/assets/max_data.json"
    private const val MAX_BYTES = 5 * 1024 * 1024

    private const val PREFS = "game_data"
    private const val MIN_GAP_MS = 60L * 60 * 1000

    class Remote(val text: String, val data: MaxData)

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

    /** Installs [r] when it is different and not older (fewer Town Halls) than the data in use. True if it was installed. */
    fun installIfBetter(c: Context, r: Remote): Boolean {
        val current = MaxDataStore.get(c)
        if (r.data.maxTownHall < current.maxTownHall || r.text == currentText(c)) return false
        MaxDataStore.install(c, r.text)
        prefs(c).edit().putLong("installed", System.currentTimeMillis()).apply()
        return true
    }

    /** Called when the app opens. Quiet: any failure is ignored and the data in use stays. */
    suspend fun refreshIfDue(c: Context) {
        if (!auto(c)) return
        val now = System.currentTimeMillis()
        if (now - prefs(c).getLong("checked", 0L) in 0 until MIN_GAP_MS) return
        prefs(c).edit().putLong("checked", now).apply()
        runCatching { installIfBetter(c, fetch()) }
    }

    /** Downloads and parses the file. Throws [IOException] when it cannot be reached or is not usable game data. */
    suspend fun fetch(): Remote = withContext(Dispatchers.IO) {
        val c = URL(URL_TEXT).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "ClashTrackers-data-check")
            if (c.responseCode != 200) throw IOException("The data file answered ${c.responseCode}")
            if (c.contentLengthLong > MAX_BYTES) throw IOException("The data file is too large")
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) throw IOException("The data file is too large")
            val text = bytes.toString(Charsets.UTF_8)
            val data = runCatching { MaxData.fromJson(text) }.getOrNull()
            if (data == null || data.items.isEmpty() || data.maxTownHall < 1) throw IOException("The data file is not valid game data")
            Remote(text, data)
        } finally {
            c.disconnect()
        }
    }
}
