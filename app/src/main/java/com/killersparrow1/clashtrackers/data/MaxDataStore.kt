package com.killersparrow1.clashtrackers.data

import android.content.Context
import com.killersparrow1.clashtrackers.core.MaxData

/** Bundled `assets/max_data.json` (levels, times, costs and Town Hall limits for the "Road to max" card), loaded once. */
object MaxDataStore {
    @Volatile private var cached: MaxData? = null

    fun get(context: Context): MaxData = cached ?: synchronized(this) {
        cached ?: runCatching {
            context.applicationContext.assets.open("max_data.json").bufferedReader().use { MaxData.fromJson(it.readText()) }
        }.getOrDefault(MaxData.EMPTY).also { cached = it }
    }
}
