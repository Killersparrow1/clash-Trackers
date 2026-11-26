package com.killersparrow1.clashtrackers.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.killersparrow1.clashtrackers.companion.CompanionService
import com.killersparrow1.clashtrackers.core.ExportParser
import com.killersparrow1.clashtrackers.core.ParseResult
import com.killersparrow1.clashtrackers.sync.ClipboardAutoSync
import com.killersparrow1.clashtrackers.sync.SyncRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import java.security.SecureRandom

/**
 * Optional auto-import through Shizuku (works with wireless debugging, no root). It starts [ClipboardWatcherService]
 * as a Shizuku user service, keeps it connected, and passes what it finds to the app's normal import ([SyncRunner]).
 *
 * [status] is RUNNING only when Shizuku answers, this app has permission, the service is connected and its watcher is
 * alive. The Clash Companion's button is hidden exactly while that is true and comes back the moment it is not.
 */
object ShizukuManager {
    enum class Status {
        /** Shizuku is not installed or not started. */
        UNAVAILABLE,
        NEEDS_PERMISSION,
        /** Switched off in settings. */
        OFF,
        CONNECTING,
        RUNNING,
        FAILED,
    }

    private const val PREFS = "shizuku_import"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TOKEN = "token"
    private const val REQUEST_CODE = 4107
    private const val WATCHDOG_MS = 5_000L
    private const val MAX_RETRIES = 5

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status

    /** True only while everything works: the Companion button stays hidden for exactly this long. */
    val operational: Boolean get() = _status.value == Status.RUNNING

    private val main = Handler(Looper.getMainLooper())
    private val importLock = Mutex()
    private var app: Context? = null
    private var service: IClipboardWatcher? = null
    private var linked: IBinder? = null
    private var started = false
    private var retries = 0

    private val deathRecipient = IBinder.DeathRecipient { main.post { onServiceLost() } }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            main.post { onConnected(binder) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            main.post { onServiceLost() }
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (_status.value != Status.RUNNING) return
            evaluate()
            if (_status.value == Status.RUNNING) main.postDelayed(this, WATCHDOG_MS)
        }
    }

    // ---- settings -------------------------------------------------------------------------------------------------

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(c: Context): Boolean = prefs(c).getBoolean(KEY_ENABLED, false)

    /** A random secret for this install; the watcher sends it back so the exported receiver ignores anything else. */
    private fun token(c: Context): String {
        val p = prefs(c)
        p.getString(KEY_TOKEN, null)?.let { return it }
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val made = bytes.joinToString("") { "%02x".format(it) }
        p.edit().putString(KEY_TOKEN, made).apply()
        return made
    }

    fun tokenMatches(c: Context, given: String?): Boolean {
        if (given == null) return false
        val real = token(c)
        // Compare every character so the time taken does not reveal how much matched.
        var diff = given.length xor real.length
        for (i in 0 until minOf(given.length, real.length)) diff = diff or (given[i].code xor real[i].code)
        return diff == 0
    }

    // ---- life cycle -----------------------------------------------------------------------------------------------

    /** Called once when the app process starts. Shizuku hands over its binder again after every reboot or restart. */
    fun init(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky { retries = 0; main.post { evaluate() } }
            Shizuku.addBinderDeadListener { main.post { onServiceLost(); evaluate() } }
            Shizuku.addRequestPermissionResultListener { _, _ -> main.post { evaluate() } }
        } catch (_: Throwable) {
        }
        main.post { evaluate() }
    }

    fun setEnabled(context: Context, on: Boolean) {
        app = context.applicationContext
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        retries = 0
        if (!on) {
            unbind()
            set(Status.OFF)
            return
        }
        main.post {
            if (ping() && !hasPermission() && !isPreV11()) {
                try { Shizuku.requestPermission(REQUEST_CODE) } catch (_: Throwable) { }
            }
            evaluate()
        }
    }

    /** Asks Shizuku for permission (the system shows its own dialog) and then checks again. */
    fun requestPermission() {
        main.post {
            try { if (ping() && !hasPermission()) Shizuku.requestPermission(REQUEST_CODE) } catch (_: Throwable) { }
            evaluate()
        }
    }

    /** Looks at everything again and moves to the state that matches. Cheap; safe to call at any time. */
    fun evaluate() {
        val ctx = app ?: return
        if (!enabled(ctx)) {
            unbind()
            set(Status.OFF)
            return
        }
        if (!ping()) {
            dropService()
            set(Status.UNAVAILABLE)
            return
        }
        if (!hasPermission()) {
            unbind()
            set(Status.NEEDS_PERMISSION)
            return
        }
        val s = service
        if (s != null && s.asBinder().pingBinder()) {
            val watching = try { s.isWatching } catch (_: Throwable) { false }
            if (watching) {
                retries = 0
                set(Status.RUNNING)
                main.removeCallbacks(watchdog)
                main.postDelayed(watchdog, WATCHDOG_MS)
                return
            }
            onServiceLost()
            return
        }
        bind(ctx)
    }

    private fun bind(ctx: Context) {
        if (_status.value == Status.CONNECTING && linked != null) return
        set(Status.CONNECTING)
        try {
            Shizuku.bindUserService(userServiceArgs(ctx), connection)
        } catch (_: Throwable) {
            fail()
        }
    }

    private fun onConnected(binder: IBinder?) {
        val ctx = app ?: return
        if (binder == null || !binder.pingBinder()) {
            fail()
            return
        }
        try {
            linked?.unlinkToDeath(deathRecipient, 0)
        } catch (_: Throwable) { }
        try {
            binder.linkToDeath(deathRecipient, 0)
            linked = binder
            val s = IClipboardWatcher.Stub.asInterface(binder)
            s.configure(ctx.packageName, token(ctx))
            service = s
        } catch (_: Throwable) {
            fail()
            return
        }
        evaluate()
    }

    /** The service process died or disconnected: forget it and try again with a growing pause, a few times only. */
    private fun onServiceLost() {
        try { linked?.unlinkToDeath(deathRecipient, 0) } catch (_: Throwable) { }
        linked = null
        service = null
        main.removeCallbacks(watchdog)
        val ctx = app ?: return
        if (!enabled(ctx)) return
        fail()
    }

    private fun fail() {
        service = null
        main.removeCallbacks(watchdog)
        if (retries < MAX_RETRIES && app?.let { enabled(it) } == true && ping() && hasPermission()) {
            set(Status.CONNECTING)
            retries++
            main.postDelayed({ if (_status.value == Status.CONNECTING) evaluate() }, 2_000L * retries)
        } else {
            set(if (ping()) Status.FAILED else Status.UNAVAILABLE)
        }
    }

    private fun dropService() {
        try { linked?.unlinkToDeath(deathRecipient, 0) } catch (_: Throwable) { }
        linked = null
        service = null
        main.removeCallbacks(watchdog)
    }

    private fun unbind() {
        val ctx = app
        dropService()
        if (ctx == null) return
        try {
            if (ping()) Shizuku.unbindUserService(userServiceArgs(ctx), connection, true)
        } catch (_: Throwable) { }
    }

    private fun set(next: Status) {
        val before = operational
        _status.value = next
        // The Companion button must follow at once: hidden while running, back as soon as it is not.
        if (before != operational) CompanionService.refresh()
    }

    // ---- Shizuku helpers ------------------------------------------------------------------------------------------

    private fun userServiceArgs(ctx: Context): Shizuku.UserServiceArgs {
        val versionCode = try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
        } catch (_: Throwable) {
            1
        }
        return Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ClipboardWatcherService::class.java.name))
            .daemon(true) // keeps watching after the app process is stopped; the next copy wakes the app again
            .processNameSuffix("clipboard")
            .debuggable(false)
            .version(versionCode) // a new app version restarts the service with the new code
    }

    private fun ping(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    private fun isPreV11(): Boolean = try { Shizuku.isPreV11() } catch (_: Throwable) { true }

    private fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    // ---- importing ------------------------------------------------------------------------------------------------

    /**
     * Hands an export from the watcher to the app's normal import. Only real village exports are used, one at a time,
     * and an export that was already imported (here, by the open-app sync or by a manual Sync) is skipped.
     */
    suspend fun importClip(ctx: Context, json: String) {
        if (!enabled(ctx)) return
        importLock.withLock {
            if (ClipboardAutoSync.isUsed(ctx, json)) return
            if (ExportParser.parse(json) !is ParseResult.Success) return
            ClipboardAutoSync.markUsed(ctx, json)
            SyncRunner.sync(ctx, json, "shizuku")
        }
    }
}
