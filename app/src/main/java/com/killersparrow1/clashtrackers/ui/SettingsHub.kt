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

/** One page of the Settings tab: 0 Settings, 1 Personalization, 2 Support. */
@Composable
internal fun SettingsPage(
    page: Int,
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
    when (page) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { SettingsSection(settings, export, now, onSettingsChange, selectedTag) }
                    item { GameDataCard() }
                }
                1 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { item { PersonalizationSection(settings, onSettingsChange) } }
                else -> SupportScreen(state, model, log, timesCount, now)
    }
}
