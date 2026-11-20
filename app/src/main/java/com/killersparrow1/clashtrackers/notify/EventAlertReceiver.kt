package com.killersparrow1.clashtrackers.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when the next event alert is due, and re-plans after a reboot, an app update or a clock/time-zone change. */
class EventAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { EventAlerts.fire(context) }
    }
}
