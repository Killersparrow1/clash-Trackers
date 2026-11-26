package com.killersparrow1.clashtrackers.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.killersparrow1.clashtrackers.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs when the live builder notification is swiped away: it puts the notification straight back while the option is on. */
class LiveStatusReturnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetUpdater.updateAll(app, "live-status")
            } finally {
                pending.finish()
            }
        }
    }
}
