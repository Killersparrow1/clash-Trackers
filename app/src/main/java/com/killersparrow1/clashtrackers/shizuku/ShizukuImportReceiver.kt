package com.killersparrow1.clashtrackers.shizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives a village export from the Shizuku clipboard watcher. It has to be exported, because the watcher sends it as
 * the shell user, so it only accepts a broadcast that carries this install's secret token (see [ShizukuManager]).
 */
class ShizukuImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ClipboardWatcherService.ACTION) return
        val app = context.applicationContext
        val json = intent.getStringExtra(ClipboardWatcherService.EXTRA_JSON)
        if (json.isNullOrBlank() || !ShizukuManager.tokenMatches(app, intent.getStringExtra(ClipboardWatcherService.EXTRA_TOKEN))) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ShizukuManager.importClip(app, json)
            } finally {
                pending.finish()
            }
        }
    }
}
