package com.killersparrow1.clashtrackers.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens Clash of Clans' More Settings, where Export Data is. */
const val CLASH_MORE_SETTINGS_LINK = "https://link.clashofclans.com/en/?action=OpenMoreSettings"

/** Opens a link (the game picks it up when installed, otherwise the browser). False if nothing could open it. */
fun openGameLink(context: Context, url: String): Boolean {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
    if (uri.scheme != "https" && uri.scheme != "http") return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
