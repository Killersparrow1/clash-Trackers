package com.killersparrow1.clashtrackers

import android.app.Application
import android.content.Context
import com.killersparrow1.clashtrackers.crash.CrashReporter

/**
 * Installs the crash catcher as early as possible: `attachBaseContext` runs before any
 * ContentProvider (androidx.startup) and before `onCreate`, so even start-up crashes are caught.
 * The `:crash` process (the crash screen itself) keeps the system handler, so a broken crash
 * screen can never loop.
 */
class ClashApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Event alerts keep one alarm for the next alert; make sure it exists (it is cleared by force-stop, not by a reboot).
        runCatching { if (!CrashReporter.isCrashProcess(this)) com.killersparrow1.clashtrackers.notify.EventAlerts.schedule(this) }
        runCatching { com.killersparrow1.clashtrackers.ui.ScrollBlurSetting.init(this) }
        runCatching { com.killersparrow1.clashtrackers.ui.GlassSetting.init(this) }
        // Optional Shizuku auto-import: reconnects whenever Shizuku (re)starts. A no-op when the user never turned it on.
        runCatching { if (!CrashReporter.isCrashProcess(this)) com.killersparrow1.clashtrackers.shizuku.ShizukuManager.init(this) }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // The crash catcher must never be the thing that stops the app from starting.
        try {
            if (!CrashReporter.isCrashProcess(this)) CrashReporter.install(this)
        } catch (_: Throwable) {
        }
    }
}
