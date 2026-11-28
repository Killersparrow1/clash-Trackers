@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import com.killersparrow1.clashtrackers.core.ReleaseInfo
import com.killersparrow1.clashtrackers.core.UpdateCheck
import com.killersparrow1.clashtrackers.update.AppUpdater
import kotlinx.coroutines.launch
import com.killersparrow1.clashtrackers.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Supercell's required wording for fan content (supercell.com/fan-content-policy). */
const val FAN_CONTENT_DISCLAIMER =
    "This material is unofficial and is not endorsed by Supercell. For more information see " +
        "Supercell's Fan Content Policy: www.supercell.com/fan-content-policy."

/** About: version, offline note, Supercell's fan content disclaimer, credits and the font licence. */
@Composable
fun AboutSection(reportProvider: () -> String = { "" }) {
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val installedVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    fun check() {
        update = UpdateState.Checking
        scope.launch {
            update = try {
                val info = AppUpdater.fetchLatest()
                if (info != null && UpdateCheck.isNewer(installedVersion, info.version)) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: java.io.IOException) {
                UpdateState.Failed((e.message ?: e.javaClass.simpleName) + "\n\n" + AppUpdater.diagnose(context))
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
    fun install(info: ReleaseInfo) {
        scope.launch {
            try {
                update = UpdateState.Downloading(info, 0f)
                val apk = AppUpdater.download(context, info) { update = UpdateState.Downloading(info, it) }
                AppUpdater.install(context, apk)
                update = UpdateState.Available(info)
            } catch (e: Exception) {
                update = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // Every group starts at the card's left edge: same heading style, 4dp inside a group, 20dp between groups.
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.about_updates_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                when (val u = update) {
                    is UpdateState.Available -> {
                        Text(stringResource(R.string.update_available, u.info.version), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (AppUpdater.canInstall(context)) {
                            UpdateButton(R.string.update_download_install) { install(u.info) }
                        } else {
                            Text(stringResource(R.string.update_allow_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            UpdateButton(R.string.update_allow_install) { AppUpdater.openInstallSettings(context) }
                        }
                    }
                    is UpdateState.Downloading -> {
                        Text(stringResource(R.string.update_downloading, (u.progress.coerceAtLeast(0f) * 100).toInt()), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (u.progress >= 0f) LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    UpdateState.Checking -> Text(stringResource(R.string.update_checking), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    UpdateState.UpToDate -> {
                        Text(stringResource(R.string.update_up_to_date), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        UpdateButton(R.string.about_check_updates) { check() }
                    }
                    is UpdateState.Failed -> {
                        Text(stringResource(R.string.update_failed, u.message), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        UpdateButton(R.string.about_check_updates) { check() }
                    }
                    UpdateState.Idle -> UpdateButton(R.string.about_check_updates) { check() }
                }
                var auto by remember { mutableStateOf(com.killersparrow1.clashtrackers.update.UpdatePrefs.autoCheck(context)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.update_auto),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    androidx.compose.material3.Switch(checked = auto, onCheckedChange = {
                        auto = it
                        com.killersparrow1.clashtrackers.update.UpdatePrefs.setAutoCheck(context, it)
                        com.killersparrow1.clashtrackers.update.UpdateCheckJob.sync(context)
                    })
                }
                TextButton(onClick = { openUrl(context, RELEASES_URL) }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.update_releases_page), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        AboutGroup(stringResource(R.string.about_feedback_title)) {
            Text(stringResource(R.string.about_feedback_note), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { reportIssue(context, reportProvider()) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.about_report_issue))
                }
                FilledTonalButton(onClick = { openUrl(context, FEATURE_URL) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.about_request_feature))
                }
            }
            Text(stringResource(R.string.about_report_note), style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.OutlinedButton(
                onClick = { openUrl(context, ISSUES_LIST_URL) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.about_view_issues)) }
            androidx.compose.material3.OutlinedButton(
                onClick = { openUrl(context, TELEGRAM_URL) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.community_telegram)) }
        }
        var creditsOpen by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { creditsOpen = !creditsOpen },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("Credits", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(if (creditsOpen) "\u2212" else "+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (creditsOpen) {
            androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        CREDITS_FILE,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            }
        }
    }
}

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
private fun UpdateButton(label: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        Icon(painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(label), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
    }
}

/** A titled group inside About; headings share one style so everything lines up. */
@Composable
private fun AboutGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** Opens a GitHub page in the browser, so the app itself never needs the internet permission. */
private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Copies the diagnostic report, then opens a new GitHub issue with a short header filled in. The report is too long
 * for a link, so the user pastes it into the box (the app itself has no internet permission).
 */
private fun reportIssue(context: Context, report: String) {
    if (report.isNotEmpty()) copyReport(context, report)
    val body = "**What happened?**\n\n\n**Diagnostic report** (it is on your clipboard, paste it here)\n\n" +
        "App: ${appVersion(context)} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n"
    openUrl(context, ISSUES_URL + "?body=" + Uri.encode(body))
}

private const val RELEASES_URL = "https://github.com/Killersparrow1/clash-trackers/releases/latest"
private const val ISSUES_LIST_URL = "https://github.com/Killersparrow1/clash-Trackers/issues/"
private const val ISSUES_URL = "https://github.com/Killersparrow1/clash-Trackers/issues/new"
private const val TELEGRAM_URL = "https://t.me/+KRUnoSeOBTpiMzQ9"
private const val FEATURE_URL = "https://github.com/Killersparrow1/clash-Trackers/issues/new?title=Feature+request%3A+&labels=enhancement"

/** Every credit in one plain text block, shown in a monospace font like a file. */
private val CREDITS_FILE = """
CLASH TRACKERS
By Killersparrow1. MIT License (original source code only).

$FAN_CONTENT_DISCLAIMER

CLASHKING
Game data, names, upgrade times, pictures and event dates.
Thank you to the ClashKing team.
  https://clashk.ing
  https://github.com/ClashKingInc/ClashKingAssets
  https://api.clashk.ing/swagger/index.html

CLASH-OF-CLANS-DATA
Built-in icons and names, by Christopher Pezza (MIT).
  https://github.com/chiefpansancolt/clash-of-clans-data

SCROLL MOTION BLUR
Modelled on Essentials by Sameera Sandakelum (MIT).
  https://github.com/sameerasw/essentials

SHIZUKU AUTO-IMPORT
Shizuku API and provider by Rikka (Apache 2.0).
HiddenApiBypass by LSPosed (Apache 2.0).
The idea of reading the clipboard through Shizuku
comes from Clash Timers by memehammad (GPL-3.0).
No code from it is used.
  https://github.com/memehammad/clash-timers

FONTS
Lilita One by Juan Montoreano and Doto by The Doto
Project Authors (SIL Open Font License 1.1).

LIBRARIES
AndroidX, Jetpack Compose, Kotlin and
kotlinx.serialization (Apache 2.0).
""".trimIndent()

/** `CT2.2 (4)` from the installed package (releases are named CT + versionName). */
internal fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    "CT${info.versionName} ($code)"
}.getOrDefault("")

