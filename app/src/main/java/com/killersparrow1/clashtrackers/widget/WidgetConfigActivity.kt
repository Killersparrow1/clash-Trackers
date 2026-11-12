@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.widget

import com.killersparrow1.clashtrackers.R
import androidx.compose.ui.res.stringResource
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.core.AccountMeta
import com.killersparrow1.clashtrackers.data.StateRepository
import com.killersparrow1.clashtrackers.ui.theme.ClashTimerTheme
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.killersparrow1.clashtrackers.locale.LanguageManager.wrap(newBase))
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)

        setContent {
            ClashTimerTheme {
                Surface(Modifier.fillMaxSize()) {
                    WidgetConfigScreen(
                        appWidgetId = appWidgetId,
                        onSave = { tag, theme, style ->
                            val repo = StateRepository(this)
                            coroutineScope.launch {
                                repo.setWidgetAccount(appWidgetId, tag)
                                repo.setWidgetTheme(appWidgetId, theme)
                                repo.setWidgetStyle(appWidgetId, style)
                                WidgetUpdater.updateAll(this@WidgetConfigActivity, "config")
                                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                setResult(RESULT_OK, resultValue)
                                finish()
                            }
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
}

@Composable
private fun WidgetConfigScreen(appWidgetId: Int, onSave: (String, String, String) -> Unit, onCancel: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { StateRepository(context) }
    val accounts by repo.accounts.collectAsState(initial = emptyList())
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTheme by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedStyle by rememberSaveable { mutableStateOf(StateRepository.WIDGET_STYLE_LIST) }

    LaunchedEffect(appWidgetId) {
        selectedTheme = repo.getWidgetTheme(appWidgetId)
        selectedStyle = repo.getWidgetStyle(appWidgetId)
        selectedTag = repo.getWidgetAccount(appWidgetId)
    }
    LaunchedEffect(accounts) {
        if (selectedTag == null) selectedTag = accounts.firstOrNull()?.tag
    }

    val themes = listOf(
        "material_you" to stringResource(R.string.widget_theme_material_you),
        "clash" to stringResource(R.string.widget_theme_clash),
        "dark" to stringResource(R.string.widget_theme_dark),
        "black" to stringResource(R.string.widget_theme_black),
        "light" to stringResource(R.string.widget_theme_light),
    )

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.widget_choose_account), style = MaterialTheme.typography.titleLarge)
        if (accounts.isEmpty()) {
            Text(stringResource(R.string.widget_no_accounts))
            Button(onClick = onCancel) { Text(stringResource(R.string.action_close)) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(accounts) { acct ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedTag = acct.tag },
                        colors = CardDefaults.cardColors(
                            containerColor = if (acct.tag == selectedTag) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(acct.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(acct.tag, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.widget_style), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedStyle == StateRepository.WIDGET_STYLE_LIST,
                                onClick = { selectedStyle = StateRepository.WIDGET_STYLE_LIST },
                                label = { Text(stringResource(R.string.widget_style_list)) },
                            )
                            FilterChip(
                                selected = selectedStyle == StateRepository.WIDGET_STYLE_MINIMAL,
                                onClick = { selectedStyle = StateRepository.WIDGET_STYLE_MINIMAL },
                                label = { Text(stringResource(R.string.widget_style_minimal)) },
                            )
                            FilterChip(
                                selected = selectedStyle == StateRepository.WIDGET_STYLE_TILES,
                                onClick = { selectedStyle = StateRepository.WIDGET_STYLE_TILES },
                                label = { Text(stringResource(R.string.widget_style_tiles)) },
                            )
                        }
                        Text(stringResource(R.string.widget_theme), style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            themes.forEach { (key, label) ->
                                FilterChip(
                                    selected = selectedTheme == key,
                                    onClick = { selectedTheme = key },
                                    label = { Text(label) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = { selectedTag?.let { onSave(it, selectedTheme ?: "material_you", selectedStyle) } },
                    enabled = selectedTag != null,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
