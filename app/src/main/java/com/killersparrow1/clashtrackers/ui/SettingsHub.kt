package com.killersparrow1.clashtrackers.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.AppSettings
import com.killersparrow1.clashtrackers.core.VillageExport
import com.killersparrow1.clashtrackers.core.WidgetModel
import com.killersparrow1.clashtrackers.core.AppState
import com.killersparrow1.clashtrackers.core.SyncLog

/** The Settings tab: Settings, Personalization and Support as sub-tabs (0, 1, 2). */
@Composable
internal fun SettingsHub(
    tab: Int,
    onTab: (Int) -> Unit,
    settings: AppSettings,
    export: VillageExport?,
    now: Long,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    selectedTag: String?,
    state: AppState,
    model: WidgetModel,
    log: SyncLog,
    timesCount: Int,
) {
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { onTab(0) }, text = { Text(stringResource(R.string.nav_settings)) })
            Tab(selected = tab == 1, onClick = { onTab(1) }, text = { Text(stringResource(R.string.settings_tab_personalization)) })
            Tab(selected = tab == 2, onClick = { onTab(2) }, text = { Text(stringResource(R.string.nav_support)) })
        }
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = { slideTransition(forward = targetState > initialState) },
            label = "settingsTab",
        ) { page ->
            when (page) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { item { SettingsSection(settings, export, now, onSettingsChange, selectedTag) } }
                1 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { item { PersonalizationSection(settings, onSettingsChange) } }
                else -> SupportScreen(state, model, log, timesCount, now)
            }
        }
    }
}
