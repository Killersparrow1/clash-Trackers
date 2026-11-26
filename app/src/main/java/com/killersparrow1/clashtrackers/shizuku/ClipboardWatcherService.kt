package com.killersparrow1.clashtrackers.shizuku

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.IBinder
import android.os.Process
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Shizuku user service. It runs in its own process with shell (ADB) rights, which Android allows to read the
 * clipboard in the background. It checks the clipboard's timestamp every couple of seconds (metadata only). Only when
 * that changes, and Clash of Clans is the app in front, does it read the text; if the text looks like a village export
 * it is handed to [ShizukuImportReceiver] with `am broadcast`. Everything after that is the app's normal import.
 *
 * This class runs inside the Shizuku process, so it must not touch the app's own classes, preferences or Android
 * context: it only knows the package name and secret it is given through [configure].
 */
class ClipboardWatcherService : IClipboardWatcher.Stub() {

    @Volatile private var appPackage: String? = null
    @Volatile private var token: String? = null
    @Volatile private var worker: Thread? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // The clipboard interface is hidden API; the shell process may not reflect on it otherwise.
            try { HiddenApiBypass.addHiddenApiExemptions("L") } catch (_: Throwable) { }
        }
        worker = thread(name = "clash-clipboard-watch", isDaemon = true) { watch() }
    }

    override fun configure(appPackage: String?, token: String?) {
        this.appPackage = appPackage
        this.token = token
    }

    override fun isWatching(): Boolean = worker?.isAlive == true

    override fun destroy() {
        exitProcess(0)
    }

    private fun watch() {
        val clipboard = try {
            HiddenClipboard()
        } catch (e: Throwable) {
            System.err.println("Clash Trackers watcher: clipboard not available: $e")
            return
        }
        var lastStamp = clipboard.stamp()
        var lastSent = 0
        while (true) {
            try {
                Thread.sleep(POLL_MS)
                val stamp = clipboard.stamp() ?: continue
                if (stamp == lastStamp) continue
                lastStamp = stamp
                // The text is only read when something new was copied AND the game is in front.
                if (!gameInFront()) continue
                val text = clipboard.text()?.trim() ?: continue
                if (text.length !in MIN_LENGTH..MAX_LENGTH || !text.startsWith("{")) continue
                val hash = text.hashCode()
                if (hash == lastSent) continue
                lastSent = hash
                forward(text)
            } catch (_: InterruptedException) {
                return
            } catch (e: Throwable) {
                System.err.println("Clash Trackers watcher: $e")
            }
        }
    }

    private fun forward(text: String) {
        val pkg = appPackage ?: return
        val secret = token ?: return
        val process = ProcessBuilder(
            "am", "broadcast", "--user", "0",
            "-n", "$pkg/$RECEIVER",
            "-a", ACTION,
            "--es", EXTRA_TOKEN, secret,
            "--es", EXTRA_JSON, text,
        ).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor()
    }

    /** True only when Clash of Clans is positively seen as the resumed app; unknown counts as "not in front". */
    private fun gameInFront(): Boolean = try {
        val process = ProcessBuilder("dumpsys", "activity", "activities").redirectErrorStream(true).start()
        val resumed = process.inputStream.bufferedReader().useLines { lines ->
            lines.filter { "ResumedActivity" in it }.take(4).toList()
        }
        process.destroy()
        resumed.any { GAME_PACKAGE in it }
    } catch (_: Throwable) {
        false
    }

    /** The system clipboard service, called as the shell package. The argument list differs between Android versions. */
    private class HiddenClipboard {
        private val service: Any
        private val describe: Method
        private val read: Method
        private val userId = Process.myUid() / 100000

        init {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "clipboard") as IBinder
            service = Class.forName("android.content.IClipboard\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)!!
            describe = service.javaClass.methods.first { it.name == "getPrimaryClipDescription" }
            read = service.javaClass.methods.first { it.name == "getPrimaryClip" }
        }

        /** Fills (callingPackage, attributionTag, userId, deviceId) by parameter type, in that order. */
        private fun arguments(m: Method): Array<Any?> {
            var strings = 0
            var ints = 0
            return m.parameterTypes.map { type ->
                when (type) {
                    String::class.java -> if (strings++ == 0) SHELL_PACKAGE else null
                    Int::class.javaPrimitiveType -> if (ints++ == 0) userId else 0 // the user first, then the default device
                    else -> null
                }
            }.toTypedArray()
        }

        /** When the clip last changed, or -1 for a clip that is not text, or null when there is none. */
        fun stamp(): Long? {
            val description = describe.invoke(service, *arguments(describe)) as ClipDescription? ?: return null
            return if (description.hasMimeType("text/*")) description.timestamp else -1L
        }

        fun text(): String? {
            val clip = read.invoke(service, *arguments(read)) as ClipData? ?: return null
            return if (clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
        }
    }

    companion object {
        private const val POLL_MS = 2000L
        private const val MIN_LENGTH = 50
        private const val MAX_LENGTH = 120_000 // stays well below the limit for one command-line argument
        private const val SHELL_PACKAGE = "com.android.shell"
        private const val GAME_PACKAGE = "com.supercell.clashofclans"
        private const val RECEIVER = "com.killersparrow1.clashtrackers.shizuku.ShizukuImportReceiver"
        const val ACTION = "com.killersparrow1.clashtrackers.action.SHIZUKU_IMPORT"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_JSON = "json"
    }
}
