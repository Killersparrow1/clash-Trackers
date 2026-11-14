package com.killersparrow1.clashtrackers.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The latest GitHub release: tag (`CT2.9`), version (`2.9`), notes and the APK to download. */
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
)

/** Reading GitHub's "latest release" answer and comparing versions. No Android or network code here, so it is unit tested. */
object UpdateCheck {
    private val DIGITS = Regex("""\d+(\.\d+)*""")

    /** `CT2.9` -> `2.9`; anything without a number is returned unchanged. */
    fun versionOf(tag: String): String = DIGITS.find(tag)?.value ?: tag

    private fun numbers(version: String): List<Int> =
        versionOf(version).split('.').mapNotNull { it.toIntOrNull() }

    /** True when [remote] is a higher version than [current] (`2.10` is higher than `2.9`; missing parts count as 0). */
    fun isNewer(current: String, remote: String): Boolean {
        val a = numbers(current)
        val b = numbers(remote)
        if (b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (y != x) return y > x
        }
        return false
    }

    /**
     * The release from `releases/latest`, or null when it has no APK. Only an `https://github.com/` asset link is accepted,
     * so the app never downloads from anywhere else.
     */
    fun parseLatest(json: String): ReleaseInfo? {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        if ((root["draft"] as? JsonPrimitive)?.contentOrNull == "true") return null
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val assets = (root["assets"] as? JsonArray) ?: return null
        val apk = assets.map { it as? JsonObject }.firstNotNullOfOrNull { a ->
            val name = a?.get("name")?.jsonPrimitive?.contentOrNull ?: return@firstNotNullOfOrNull null
            val url = a["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@firstNotNullOfOrNull null
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://github.com/")) Triple(name, url, a["size"]?.jsonPrimitive?.longOrNull ?: 0L) else null
        } ?: return null
        return ReleaseInfo(
            tag = tag,
            version = versionOf(tag),
            notes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
            apkName = apk.first.replace(Regex("""[^A-Za-z0-9._-]"""), "_"),
            apkUrl = apk.second,
            apkSize = apk.third,
        )
    }
}
