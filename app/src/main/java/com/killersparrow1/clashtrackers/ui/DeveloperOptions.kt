@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.sync.AppIcon
import com.killersparrow1.clashtrackers.sync.GoldPassStore

/** Hidden developer options. Opened by tapping the Support tab 11 times in 4 seconds; leaving the page hides it again. */
@Composable
fun DeveloperOptionsScreen(selectedTag: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Developer options") },
                    navigationIcon = { TextButton(onClick = onClose) { Text("Back") } },
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "For testing. Some options are experimental and can restart the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                SectionCard("App icon") {
                    var themed by remember { mutableStateOf(AppIcon.themed(context)) }
                    fun pick(value: Boolean) {
                        if (value == themed) return
                        themed = value
                        runCatching { AppIcon.setThemed(context, value) }
                    }
                    IconChoice("Follow my phone (default)", "Uses your phone's themed icons setting (Android 13+).", themed) { pick(true) }
                    IconChoice("Always colour", "The full colour icon, never tinted.", !themed) { pick(false) }
                    Text(
                        "Your launcher may take a few seconds to update and can briefly show both icons or close the app.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                SectionCard("Gold Pass") {
                    Text("Forget the saved Gold Pass answer for this village and ask again, as at the start of a new month.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { GoldPassStore.reset(context, selectedTag) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reset Gold Pass popup")
                    }
                }

                SectionCard("Open a game link") {
                    var link by remember { mutableStateOf(CLASH_MORE_SETTINGS_LINK) }
                    var result by remember { mutableStateOf("") }
                    androidx.compose.material3.OutlinedTextField(
                        value = link,
                        onValueChange = { link = it; result = "" },
                        label = { Text("link.clashofclans.com link") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { result = if (openGameLink(context, link)) "Opened" else "Nothing could open this link" },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open") }
                    if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "To try other parameters, for example one that scrolls to Export Data. Only https links are opened.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Hide developer options") }
            }
        }
    }
}

@Composable
private fun IconChoice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
