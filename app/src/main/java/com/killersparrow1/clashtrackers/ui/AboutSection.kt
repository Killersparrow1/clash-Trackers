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
        Text(stringResource(R.string.about_updates_note), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { openReleases(context) }) { Text(stringResource(R.string.about_check_updates)) }
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

/** Opens the GitHub Releases page in the browser, so the app itself never needs the internet permission. */
private fun openReleases(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
    }
}

private const val RELEASES_URL = "https://github.com/Killersparrow1/clash-trackers/releases/latest"

/** `CT2.2 (4)` from the installed package (releases are named CT + versionName). */
private fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    "CT${info.versionName} ($code)"
}.getOrDefault("")

private fun readAsset(context: Context, path: String): String =
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("Licence file missing.")
