package com.killersparrow1.clashtrackers.update

import com.killersparrow1.clashtrackers.core.MaxData
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The newest game data file from the project's repository, checked before the app uses it. */
object DataUpdater {
    private const val URL_TEXT = "https://raw.githubusercontent.com/Killersparrow1/clash-Trackers/main/app/src/main/assets/max_data.json"
    private const val MAX_BYTES = 5 * 1024 * 1024

    class Remote(val text: String, val data: MaxData)

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
