package com.killersparrow1.clashtrackers.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.killersparrow1.clashtrackers.core.ClashKingDates
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Raid weekend and season dates from ClashKing's API (https://api.clashk.ing, documented at
 * https://api.clashk.ing/swagger/index.html; the `/v2/dates` endpoints are public and need no key), kept in a file so
 * they show at once when the app opens and work offline.
 *
 * Refreshing: when the app opens, if the last good answer is older than [REFRESH_AFTER_MS] (the dates change at most
 * weekly) and the last attempt is older than [RETRY_AFTER_MS]; or on demand with the refresh button. A failed or
 * unusable answer never replaces the saved dates.
 */
object ClashKingDatesStore {
    private const val BASE = "https://api.clashk.ing"
    private const val PREFS = "clashking_dates"
    private const val REFRESH_AFTER_MS = 6 * 3_600_000L
    private const val RETRY_AFTER_MS = 15 * 60_000L
    private const val MAX_BYTES = 64 * 1024

    var dates by mutableStateOf<ClashKingDates?>(null)
        private set

    /** When the dates were last fetched successfully (epoch millis), 0 if never. */
    var updatedAt by mutableStateOf(0L)
        private set

    private var loaded = false

    private fun file(c: Context) = File(c.applicationContext.filesDir, "clashking_dates.json")
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the saved copy once. */
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        runCatching { file(c).takeIf { it.exists() }?.readText() }.getOrNull()
            ?.let { ClashKingDates.fromJson(it) }
            ?.let { dates = it; updatedAt = it.fetchedAt * 1000 }
    }

    /**
     * Fetches the dates now and saves them. Throws [IOException] when ClashKing cannot be reached or answers with
     * nothing usable; the saved dates stay as they were. What could be read is merged over what was saved, so one bad
     * endpoint does not throw away a good date from another.
     */
    suspend fun refresh(c: Context) {
        load(c)
        val now = System.currentTimeMillis() / 1000
        val old = dates
        val result = withContext(Dispatchers.IO) {
            val current = runCatching { get("/v2/dates/current") }
            val raid = current.getOrNull()?.let { ClashKingDates.parseRaidStart(it) }
            // A season is named by the month it ends in, so ask for the named one and the one after, and take the first
            // that has not ended.
            var seasonEnd: Long? = null
            var reached = current.isSuccess
            for (id in ClashKingDates.seasonCandidates(current.getOrNull()?.let { ClashKingDates.parseSeasonId(it) }, now)) {
                val bounds = runCatching { get("/v2/dates/season-start-end?season=$id") }
                reached = reached || bounds.isSuccess
                val end = bounds.getOrNull()?.let { ClashKingDates.parseSeasonBounds(it) }?.second
                if (end != null && end > now) { seasonEnd = end; break }
            }
            if (raid == null && seasonEnd == null) {
                throw IOException(if (reached) "ClashKing answered, but not with usable dates" else "ClashKing could not be reached")
            }
            ClashKingDates(
                fetchedAt = now,
                raidStart = raid ?: old?.raidStart,
                seasonEnd = seasonEnd ?: old?.seasonEnd?.takeIf { it > now },
            )
        }
        val target = file(c)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(result.toJson())
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
        prefs(c).edit().putLong("checked", System.currentTimeMillis()).apply()
        dates = result
        updatedAt = now * 1000
    }

    /** Called when the app opens: refreshes when switched on (Clash > Events) and due. Failures are silent. */
    suspend fun refreshIfDue(c: Context) {
        load(c)
        if (!EventsStore.auto(c)) return
        val now = System.currentTimeMillis()
        if (now - updatedAt in 0 until REFRESH_AFTER_MS) return
        if (now - prefs(c).getLong("checked", 0L) in 0 until RETRY_AFTER_MS) return
        prefs(c).edit().putLong("checked", now).apply()
        runCatching { refresh(c) }
    }

    private fun get(path: String): String {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "ClashTrackers-dates")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != 200) throw IOException("ClashKing answered ${conn.responseCode}")
            if (conn.contentLengthLong > MAX_BYTES) throw IOException("The answer is too large")
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) throw IOException("The answer is too large")
            return bytes.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
