package com.killersparrow1.clashtrackers.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.killersparrow1.clashtrackers.core.MaxData
import com.killersparrow1.clashtrackers.core.NameMap
import java.io.File

/**
 * The game data behind Road to max and Bedtime: the bundled `assets/max_data.json`, or a newer copy downloaded in
 * Clash > Data. A downloaded copy is dropped once the app itself is updated (the new app ships its own data), so the
 * bundled data never ends up older than what the app knows.
 */
object MaxDataStore {
    @Volatile private var cached: MaxData? = null
    @Volatile private var fromDownload = false

    /** Bumps whenever the data is replaced, so open screens read it again. */
    var version by mutableIntStateOf(0)
        private set

    fun downloadedFile(context: Context): File = File(context.applicationContext.filesDir, "max_data_downloaded.json")

    private fun bundled(context: Context): MaxData = runCatching {
        context.applicationContext.assets.open("max_data.json").bufferedReader().use { MaxData.fromJson(it.readText()) }
    }.getOrDefault(MaxData.EMPTY)

    private fun downloaded(context: Context): MaxData? = runCatching {
        val file = downloadedFile(context)
        if (!file.exists()) return null
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (file.lastModified() < installedAt) {
            file.delete()
            return null
        }
        MaxData.fromJson(file.readText()).takeIf { it.items.isNotEmpty() }
    }.getOrNull()

    fun get(context: Context): MaxData = cached ?: synchronized(this) {
        cached ?: run {
            val own = bundled(context)
            val extra = downloaded(context)
            // A downloaded copy is used only when it knows at least as many Town Halls as the bundled one.
            val useDownload = extra != null && extra.maxTownHall >= own.maxTownHall
            fromDownload = useDownload
            (if (useDownload) extra!! else own).also {
                cached = it
                NameMap.extra = it.items.mapNotNull { (id, item) -> item.name?.let { n -> id to n } }.toMap()
            }
        }
    }

    /** True when the data in use is a downloaded copy (not the one built into the app). */
    fun isDownloaded(context: Context): Boolean {
        get(context)
        return fromDownload
    }

    /**
     * Saves [text] as the downloaded copy and starts using it. The text is parsed first and written to a temporary file
     * that is then moved into place, so a bad download or an interrupted write never damages the copy in use.
     * Returns false (and changes nothing) when [text] is not usable game data.
     */
    fun install(context: Context, text: String): Boolean {
        val ok = runCatching { MaxData.fromJson(text) }.getOrNull()?.let { it.items.isNotEmpty() && it.maxTownHall >= 1 } == true
        if (!ok) return false
        val target = downloadedFile(context)
        val tmp = File(target.parentFile, target.name + ".tmp")
        return runCatching {
            tmp.writeText(text)
            if (!tmp.renameTo(target)) { target.delete(); check(tmp.renameTo(target)) }
            reset()
            true
        }.getOrElse { tmp.delete(); false }
    }

    /** Goes back to the data built into the app. */
    fun useBundled(context: Context) {
        downloadedFile(context).delete()
        reset()
    }

    /** Tells open screens to redraw (for example after icons were downloaded). */
    fun touch() {
        version++
    }

    private fun reset() {
        synchronized(this) { cached = null }
        version++
    }
}
