package com.killersparrow1.clashtrackers.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.core.ReleaseInfo
import com.killersparrow1.clashtrackers.core.UpdateCheck
import com.killersparrow1.clashtrackers.update.AppUpdater
import com.killersparrow1.clashtrackers.update.UpdatePrefs
import kotlinx.coroutines.launch

/** When the app opens (at most every 12 hours, if switched on in About) looks for a newer release and offers it in a dialog. */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!UpdatePrefs.dueNow(context)) return@LaunchedEffect
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val info = runCatching { AppUpdater.fetchLatest() }.getOrNull() ?: return@LaunchedEffect
        if (UpdateCheck.isNewer(installed, info.version) && !UpdatePrefs.skipped(context, info.version)) release = info
    }

    val info = release ?: return
    val busy = progress != null
    AlertDialog(
        onDismissRequest = { if (!busy) release = null },
        title = { Text("Update available: CT${info.version}") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (info.notes.isNotBlank()) Text(info.notes.take(800), style = MaterialTheme.typography.bodyMedium)
                progress?.let {
                    LinearProgressIndicator(progress = { it.coerceAtLeast(0f) }, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (!AppUpdater.canInstall(context)) {
                    Text("Android will ask once to allow installs from this app.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (!AppUpdater.canInstall(context)) {
                    AppUpdater.openInstallSettings(context)
                    return@TextButton
                }
                error = null
                scope.launch {
                    try {
                        progress = 0f
                        val apk = AppUpdater.download(context, info) { progress = it }
                        AppUpdater.install(context, apk)
                        release = null
                    } catch (e: Exception) {
                        error = e.message ?: e.javaClass.simpleName
                    }
                    progress = null
                }
            }) { Text(if (AppUpdater.canInstall(context)) "Update" else "Allow installs") }
        },
        dismissButton = {
            if (!busy) Row2(
                later = { release = null },
                skip = { UpdatePrefs.skip(context, info.version); release = null },
            )
        },
    )
}

@Composable
private fun Row2(later: () -> Unit, skip: () -> Unit) {
    androidx.compose.foundation.layout.Row {
        TextButton(onClick = skip) { Text("Skip this version") }
        TextButton(onClick = later) { Text("Later") }
    }
}
