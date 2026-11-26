package com.killersparrow1.clashtrackers.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.companion.CompanionPrefs
import com.killersparrow1.clashtrackers.companion.CompanionService
import com.killersparrow1.clashtrackers.notify.Notifier
import com.killersparrow1.clashtrackers.widget.WidgetAlarm

internal enum class Need(@StringRes val title: Int, @StringRes val why: Int) {
    NOTIFICATIONS(R.string.perm_notifications, R.string.perm_notifications_why),
    ALARMS(R.string.perm_alarms, R.string.perm_alarms_why),
    BATTERY(R.string.perm_battery, R.string.perm_battery_why),
    UNUSED(R.string.perm_unused, R.string.perm_unused_why),
    ACCESSIBILITY(R.string.perm_accessibility, R.string.perm_accessibility_why),
    INSTALL_APPS(R.string.perm_install, R.string.perm_install_why),
}

/** What is switched off right now. Android never lets an app grant these itself, so each one opens its own screen. */
internal fun missing(context: Context): List<Need> = buildList {
    if (!Notifier.allowed(context)) add(Need.NOTIFICATIONS)
    if (!WidgetAlarm.canScheduleExact(context)) add(Need.ALARMS)
    val power = runCatching { context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) }.getOrNull()
    if (power == false) add(Need.BATTERY)
    // "Pause app activity if unused" (Android 11+) resets permissions and stops background work for an app that is not opened.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        runCatching { !context.packageManager.isAutoRevokeWhitelisted }.getOrDefault(false)
    ) add(Need.UNUSED)
    if (!CompanionPrefs.serviceEnabled(context)) add(Need.ACCESSIBILITY)
    if (!runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)) add(Need.INSTALL_APPS)
}

internal fun open(context: Context, intent: Intent) {
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure) {
        runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * Asks, every time the app opens, for whatever is still switched off: notifications, exact alarms, unrestricted battery,
 * "Pause app activity if unused", the Clash Companion accessibility service and "Install unknown apps". It checks again each time you come back from Android's settings, and "Later" only
 * hides it until the next launch.
 */
@Composable
fun PermissionPrompt(enabled: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    var later by remember { mutableStateOf(false) }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
        // Denied twice and Android stops showing its box: send the person to the notification settings instead.
        if (!Notifier.allowed(context)) {
            open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    val needs = remember(tick) { missing(context) }
    if (!enabled || later || needs.isEmpty()) return

    fun fix(need: Need) = fixNeed(context, need, asked) {
        asked = true
        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    AlertDialog(
        onDismissRequest = { later = true },
        title = { Text(stringResource(R.string.perm_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.perm_intro), style = MaterialTheme.typography.bodyMedium)
                needs.forEach { need ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(need.title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(need.why), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { fix(need) }) { Text(stringResource(R.string.perm_fix)) }
                            // The accessibility switch can be greyed out until "Allow restricted settings" is chosen in App info.
                            if (need == Need.ACCESSIBILITY) {
                                OutlinedButton(onClick = {
                                    open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                }) { Text(stringResource(R.string.perm_app_info)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { later = true }) { Text(stringResource(R.string.perm_later)) } },
    )
}

internal fun fixNeed(context: Context, need: Need, notificationsAsked: Boolean, askNotifications: () -> Unit) {
    val pkg = Uri.parse("package:${context.packageName}")
    when (need) {
        Need.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAsked) {
                askNotifications()
            } else {
                open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        Need.ALARMS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
        // The small "Allow app to always run in the background?" box for this app only, not the whole battery list.
        Need.BATTERY -> open(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        Need.ACCESSIBILITY -> {
            // Android never lets an app switch an accessibility service on itself. This opens the service's own page
            // where that is supported (Android 14+) and the accessibility list otherwise.
            CompanionPrefs.setEnabled(context, true)
            CompanionService.refresh()
            val service = ComponentName(context, CompanionService::class.java).flattenToString()
            val intent = if (Build.VERSION.SDK_INT >= 34) {
                Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, service)
            } else Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure) {
                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        Need.INSTALL_APPS -> open(context, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg))
        Need.UNUSED ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) open(context, Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, pkg))
    }
}
