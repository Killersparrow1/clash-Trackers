package com.killersparrow1.clashtrackers.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.shizuku.ShizukuManager
import com.killersparrow1.clashtrackers.shizuku.ShizukuManager.Status

/** Settings card for the optional Shizuku auto-import: an enable box, the connection status and what to do next. */
@Composable
internal fun ShizukuCard() {
    val context = LocalContext.current
    val status by ShizukuManager.status.collectAsState()
    var on by remember { mutableStateOf(ShizukuManager.enabled(context)) }
    // Look again whenever the screen comes back, e.g. after granting permission in Shizuku.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) ShizukuManager.evaluate() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SectionCard(stringResource(R.string.shizuku_title)) {
        Text(stringResource(R.string.shizuku_desc), style = MaterialTheme.typography.bodyMedium)
        CheckRow(stringResource(R.string.shizuku_enable), stringResource(R.string.shizuku_enable_desc), on) { v ->
            on = v
            ShizukuManager.setEnabled(context, v)
        }
        if (on) {
            Text(
                stringResource(
                    when (status) {
                        Status.UNAVAILABLE -> R.string.shizuku_status_unavailable
                        Status.NEEDS_PERMISSION -> R.string.shizuku_status_permission
                        Status.OFF -> R.string.shizuku_status_off
                        Status.CONNECTING -> R.string.shizuku_status_connecting
                        Status.RUNNING -> R.string.shizuku_status_running
                        Status.FAILED -> R.string.shizuku_status_failed
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                color = if (status == Status.RUNNING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    Status.UNAVAILABLE -> {
                        FilledTonalButton(onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            context.startActivity(
                                (launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }) { Text(stringResource(R.string.shizuku_open)) }
                        OutlinedButton(onClick = { ShizukuManager.evaluate() }) { Text(stringResource(R.string.shizuku_check_again)) }
                    }
                    Status.NEEDS_PERMISSION ->
                        FilledTonalButton(onClick = { ShizukuManager.requestPermission() }) { Text(stringResource(R.string.shizuku_allow)) }
                    Status.FAILED ->
                        FilledTonalButton(onClick = { ShizukuManager.setEnabled(context, true) }) { Text(stringResource(R.string.shizuku_retry)) }
                    else -> Unit
                }
            }
            if (status == Status.RUNNING) {
                Text(stringResource(R.string.shizuku_running_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
