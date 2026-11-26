package com.killersparrow1.clashtrackers.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.killersparrow1.clashtrackers.notify.Notifier
import com.killersparrow1.clashtrackers.sync.IdleAlert
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Four Material icons drawn from their path data, so the app does not need the large icon library.
private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

private val NotificationsIcon = icon("notifications", "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z")
private val BoltIcon = icon("bolt", "M11,21h-1l1,-7H7.5c-0.58,0 -0.57,-0.32 -0.38,-0.66 0.19,-0.34 0.05,-0.08 0.07,-0.12C8.48,10.94 10.42,7.54 13,3h1l-1,7h3.5c0.49,0 0.56,0.33 0.47,0.51l-0.07,0.15C12.96,17.55 11,21 11,21z")
private val AlarmIcon = icon("alarm", "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85zM12.5,8H11v6l4.75,2.85 0.75,-1.23 -4,-2.37V8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z")
private val DownloadIcon = icon("download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")

object QuickSetupPrefs {
    private const val PREFS = "quick_setup"
    fun done(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("done", false)
    fun setDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("done", true).apply()
    }
}

/** A rounded badge with scalloped edges, like a cookie. */
private class ScallopShape(private val lobes: Int = 8, private val depth: Float = 0.07f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val path = Path()
        val steps = 160
        for (i in 0..steps) {
            val a = 2.0 * PI * i / steps
            val rad = r * (1f - depth + depth * cos(lobes * a).toFloat())
            val x = c.x + rad * cos(a).toFloat()
            val y = c.y + rad * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    badge: Color,
    title: String,
    body: String,
    control: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(52.dp).clip(ScallopShape()).background(badge), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            control()
        }
    }
}

@Composable
private fun StatusOrAllow(on: Boolean, onAllow: () -> Unit) {
    if (on) {
        Text("On", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    } else {
        FilledTonalButton(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow") }
    }
}

/** First-run page: a few choices, then on to the village. Shown once; every choice can be changed later in Settings. */
@Composable
fun QuickSetup(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
        if (!Notifier.allowed(context)) {
            open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    val needs = remember(tick) { missing(context) }
    var idleOn by remember { mutableStateOf(IdleAlert.enabled(context)) }
    var idleHours by remember { mutableIntStateOf(IdleAlert.hours(context)) }

    fun fix(need: Need) = fixNeed(context, need, asked) {
        asked = true
        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.systemBarsPadding().padding(horizontal = 20.dp)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Quick setup", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "A few choices, then a short tour. You can change all of this later.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SetupCard(
                        NotificationsIcon, MaterialTheme.colorScheme.primaryContainer,
                        "Notifications", "Tells you the moment a builder, lab or pet upgrade finishes.",
                    ) { StatusOrAllow(Need.NOTIFICATIONS !in needs) { fix(Need.NOTIFICATIONS) } }
                    val reliable = needs.firstOrNull { it == Need.ALARMS } ?: needs.firstOrNull { it == Need.BATTERY }
                    SetupCard(
                        BoltIcon, MaterialTheme.colorScheme.tertiaryContainer,
                        "Reliable alerts", "Lets alarms fire on time and stops Android putting the app to sleep.",
                    ) { StatusOrAllow(reliable == null) { reliable?.let { fix(it) } } }
                    SetupCard(
                        AlarmIcon, MaterialTheme.colorScheme.secondaryContainer,
                        "Idle builder reminder", "Reminds you when a builder or the lab has sat free for a while.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !idleOn, onClick = {
                                idleOn = false
                                IdleAlert.setEnabled(context, false)
                            }, label = { Text("Off") })
                            listOf(1, 2, 3).forEach { h ->
                                FilterChip(selected = idleOn && idleHours == h, onClick = {
                                    idleOn = true
                                    idleHours = h
                                    IdleAlert.setEnabled(context, true)
                                    IdleAlert.setHours(context, h)
                                }, label = { Text("$h h") })
                            }
                        }
                    }
                    SetupCard(
                        DownloadIcon, MaterialTheme.colorScheme.primaryContainer,
                        "Auto-import", "Shows an import button while Clash of Clans is open, so syncing takes one tap.",
                    ) { StatusOrAllow(Need.ACCESSIBILITY !in needs) { fix(Need.ACCESSIBILITY) } }
                }
                Button(
                    onClick = {
                        QuickSetupPrefs.setDone(context)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(bottom = 16.dp),
                ) { Text("Continue") }
            }
        }
    }
}
