package com.killersparrow1.clashtrackers.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.killersparrow1.clashtrackers.core.MaxData
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
            (if (useDownload) extra!! else own).also { cached = it }
        }
    }

    /** True when the data in use is a downloaded copy (not the one built into the app). */
    fun isDownloaded(context: Context): Boolean {
        get(context)
        return fromDownload
    }

    /** Saves [text] as the downloaded copy and starts using it. The caller has already checked that it parses. */
    fun install(context: Context, text: String) {
        downloadedFile(context).writeText(text)
        reset()
    }

    /** Goes back to the data built into the app. */
    fun useBundled(context: Context) {
        downloadedFile(context).delete()
        reset()
    }

    private fun reset() {
        synchronized(this) { cached = null }
        version++
    }
}
