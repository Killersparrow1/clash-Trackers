package com.killersparrow1.clashtrackers.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.killersparrow1.clashtrackers.core.ReleaseInfo
import com.killersparrow1.clashtrackers.core.UpdateCheck
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app update from the GitHub releases: look up the latest release, download its APK, hand it to Android's installer.
 * This is the only networking in the app, and it only runs when the user taps a button in Support > About.
 * Android itself checks that the new APK is signed with the same key as the installed app.
 */
object AppUpdater {
    private const val API_URL = "https://api.github.com/repos/Killersparrow1/clash-Trackers/releases/latest"
    private const val USER_AGENT = "ClashTrackers-update-check"

    /** The latest release, or null when the repository has no release with an APK yet. Throws [IOException] on network problems. */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val c = URL(API_URL).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", USER_AGENT)
            when (val code = c.responseCode) {
                200 -> UpdateCheck.parseLatest(c.inputStream.bufferedReader().use { it.readText() })
                404 -> null
                else -> throw IOException("GitHub answered $code")
            }
        } finally {
            c.disconnect()
        }
    }

    /** Downloads the APK to the app's cache and returns it; [onProgress] gets 0..1 (or -1 when the size is unknown). */
    suspend fun download(context: Context, info: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, info.apkName)
        val c = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            if (c.responseCode != 200) throw IOException("Download answered ${c.responseCode}")
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.apkSize
            var done = 0L
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                    }
                }
            }
            if (info.apkSize > 0 && out.length() != info.apkSize) {
                out.delete()
                throw IOException("The download is incomplete. Try again.")
            }
            out
        } finally {
            c.disconnect()
        }
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" screen for this app. */
    fun openInstallSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands [apk] to the system installer; Android asks the user to confirm (see [UpdateInstallReceiver]). */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callback = PendingIntent.getBroadcast(
                context, id,
                Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }
}
