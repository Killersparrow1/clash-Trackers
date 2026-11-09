package com.killersparrow1.clashtrackers.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.killersparrow1.clashtrackers.MainActivity
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.FinishNotice

/** "Builder free: Inferno Tower" / "Lab free" / "Pet house free". */
object Notifier {
    /** Plays on the alarm volume and breaks through Do Not Disturb (the original channel, kept for existing users). */
    const val CHANNEL_ID = "finished_v2"
    /** Plays on the notification volume and is silenced by Do Not Disturb. */
    const val CHANNEL_QUIET = "finished_v3"
    private const val PREFS = "notif_prefs"
    private const val KEY_RESPECT_DND = "respect_dnd"
    private const val OLD_CHANNEL_ID = "finished"
    private const val CLASH_PACKAGE = "com.supercell.clashofclans"

    /** Whether finish alerts follow Do Not Disturb (default yes, so they stay quiet overnight). */
    fun respectDnd(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RESPECT_DND, true)

    fun setRespectDnd(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_RESPECT_DND, on).apply()
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        if (nm.getNotificationChannel(CHANNEL_QUIET) == null) {
            val quiet = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val lctx = com.killersparrow1.clashtrackers.locale.LanguageManager.wrap(context)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_QUIET, lctx.getString(R.string.notif_channel_quiet), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = lctx.getString(R.string.notif_channel_quiet_desc)
                    setSound(Uri.parse("android.resource://${context.packageName}/${R.raw.finish_sound}"), quiet)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.finish_sound}")
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                com.killersparrow1.clashtrackers.locale.LanguageManager.wrap(context).getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = com.killersparrow1.clashtrackers.locale.LanguageManager.wrap(context).getString(R.string.notif_channel_desc)
                setSound(soundUri, audioAttributes)
            },
        )
    }

    /** True if the app may post notifications (runtime permission on Android 13+, and not blocked in settings). */
    fun allowed(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Posts one notification per finished job; returns false if notifications are blocked. */
    fun post(context: Context, notices: List<FinishNotice>, accountName: String?, accountTag: String?, multipleAccounts: Boolean): Boolean {
        if (!allowed(context)) return false
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        val channel = if (respectDnd(context)) CHANNEL_QUIET else CHANNEL_ID
        for ((index, n) in notices.withIndex()) {
            val titlePrefix = if (multipleAccounts && accountName != null) "$accountName ($accountTag): " else ""
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("$titlePrefix${n.title}")
                .setContentText(n.text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                // Several upgrades finishing together: only the first one makes a sound.
                .setSilent(index > 0)
                .setContentIntent(openIntent(context))
                .build()
            try {
                val id = n.jobKey.hashCode() xor (accountTag?.hashCode() ?: 0)
                nm.notify(id, notification)
            } catch (e: SecurityException) {
                return false
            }
        }
        return true
    }

    /** Tapping a notification opens Clash of Clans if it's installed, otherwise this app. */
    private fun openIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(CLASH_PACKAGE)
            ?: Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
