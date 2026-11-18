package com.killersparrow1.clashtrackers.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.killersparrow1.clashtrackers.MainActivity
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.UpdateCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Looks for a new release about every 12 hours while the phone is online, even when the app is closed, and posts one
 * notification per new version. Uses Android's own JobScheduler (no extra library); the job survives reboots.
 * It follows the same on/off switch as the check when the app opens (Settings > Support > About).
 */
class UpdateCheckJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!UpdatePrefs.autoCheck(this)) return false
        running = scope.launch {
            var retry = false
            try {
                notifyIfNew(applicationContext)
            } catch (e: Exception) {
                retry = true // no network or GitHub unreachable: try again later
            }
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 4201
        private const val CHANNEL = "app_update_v1"
        private const val NOTIFICATION_ID = 4201
        private const val EVERY_MS = 12L * 60 * 60 * 1000

        /** Schedules the background check when it is switched on (once) and cancels it when it is off. */
        fun sync(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (!UpdatePrefs.autoCheck(context)) {
                js.cancel(JOB_ID)
                return
            }
            if (js.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(EVERY_MS)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(job) }
        }

        /** Posts a notification for the newest release when it is newer than the installed one and not announced yet. */
        internal suspend fun notifyIfNew(context: Context) {
            val info = AppUpdater.fetchLatest() ?: return
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            if (!UpdateCheck.isNewer(installed, info.version)) return
            if (UpdatePrefs.skipped(context, info.version) || UpdatePrefs.notified(context) == info.version) return
            if (!allowed(context)) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_update), NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val open = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(context.getString(R.string.notif_update_title, info.version))
                .setContentText(context.getString(R.string.notif_update_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            // Once per version; and let the app's own prompt (what is new, Install) show the next time it opens.
            UpdatePrefs.setNotified(context, info.version)
            UpdatePrefs.resetLastCheck(context)
        }

        private fun allowed(context: Context): Boolean {
            val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
