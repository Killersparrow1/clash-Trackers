@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.killersparrow1.clashtrackers.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Supercell's required wording for fan content (supercell.com/fan-content-policy). */
const val FAN_CONTENT_DISCLAIMER =
    "This material is unofficial and is not endorsed by Supercell. For more information see " +
        "Supercell's Fan Content Policy: www.supercell.com/fan-content-policy."

/** About: version, offline note, Supercell's fan content disclaimer, credits and the font licence. */
@Composable
fun AboutSection() {
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    var showLicence by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.about_version, version))
        // Prominent update card: tonal container with a full-width filled button.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.about_updates_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Button(
                    onClick = { openUrl(context, RELEASES_URL) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.about_check_updates),
                        style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                    )
                }
            }
        }
        Text(stringResource(R.string.about_feedback_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.about_feedback_note), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { openUrl(context, ISSUES_URL) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.about_report_issue))
            }
            FilledTonalButton(onClick = { openUrl(context, FEATURE_URL) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.about_request_feature))
            }
        }
        TextButton(onClick = { openUrl(context, ISSUES_LIST_URL) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.about_view_issues))
        }
        Text("Works fully offline", fontWeight = FontWeight.Bold)
        Text(
            "The app has no internet permission. It reads only the village data you copy in Clash of Clans, " +
                "keeps it on this phone and sends nothing anywhere. No account, no ads, no tracking.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(FAN_CONTENT_DISCLAIMER, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Clash of Clans names and images are the property of Supercell. Names, upgrade times and images " +
                "come from the clash-of-clans-data package. Font: Lilita One by Juan Montoreano, " +
                "SIL Open Font License 1.1.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { showLicence = !showLicence }) {
            Text(if (showLicence) "Hide font licence" else "Show font licence")
        }
        if (showLicence) {
            val licence = remember { readAsset(context, "licenses/LilitaOne-OFL.txt") }
            Text(licence, style = MaterialTheme.typography.bodySmall)
        }
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

private const val RELEASES_URL = "https://github.com/Killersparrow1/clash-trackers/releases/latest"
private const val ISSUES_LIST_URL = "https://github.com/Killersparrow1/clash-Trackers/issues/"
private const val ISSUES_URL = "https://github.com/Killersparrow1/clash-Trackers/issues/new"
private const val FEATURE_URL = "https://github.com/Killersparrow1/clash-Trackers/issues/new?title=Feature+request%3A+&labels=enhancement"

/** `CT2.2 (4)` from the installed package (releases are named CT + versionName). */
private fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    "CT${info.versionName} ($code)"
}.getOrDefault("")

private fun readAsset(context: Context, path: String): String =
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("Licence file missing.")
