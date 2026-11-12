package com.killersparrow1.clashtrackers.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.material3.RadioButton
import com.killersparrow1.clashtrackers.locale.LanguageManager
import com.killersparrow1.clashtrackers.sync.SyncRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.killersparrow1.clashtrackers.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.killersparrow1.clashtrackers.sync.ClipboardAutoSync
import androidx.compose.runtime.rememberCoroutineScope
import com.killersparrow1.clashtrackers.sync.ClockTowerTracking
import com.killersparrow1.clashtrackers.sync.GoldPassStore
import com.killersparrow1.clashtrackers.core.GoldPassLogic
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.core.AppSettings
import com.killersparrow1.clashtrackers.core.BoostSettings
import com.killersparrow1.clashtrackers.core.GameIds
import com.killersparrow1.clashtrackers.core.VillageExport
import com.killersparrow1.clashtrackers.notify.Notifier

private const val H = 3600L

/** Settings retain their existing storage and ranges; only the editing surface changes. */
@Composable
fun SettingsSection(
    settings: AppSettings,
    export: VillageExport?,
    now: Long,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    selectedTag: String? = null,
) {
    val context = LocalContext.current
    val b = settings.boosts
    fun boosts(f: (BoostSettings) -> BoostSettings) = onChange { it.copy(boosts = f(it.boosts)) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val notificationsAllowed = remember(now, permissionTick) { Notifier.allowed(context) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings_autosave), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard(stringResource(R.string.settings_section_village)) {
            SettingRow(stringResource(R.string.settings_player_name), settings.displayName, stringResource(R.string.settings_player_name_desc)) { editName = true }
            var autoClip by remember { mutableStateOf(ClipboardAutoSync.enabled(context)) }
            SwitchRow(stringResource(R.string.settings_auto_clipboard), autoClip) { on ->
                autoClip = on
                ClipboardAutoSync.setEnabled(context, on)
            }
            Text(stringResource(R.string.settings_auto_clipboard_desc), style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(stringResource(R.string.settings_section_gold_pass)) {
            val gpScope = rememberCoroutineScope()
            var gpOpen by remember { mutableStateOf(false) }
            val gp = remember(selectedTag, GoldPassStore.version) { GoldPassStore.answer(context, selectedTag) }
            Text(
                if (gp.month == null) stringResource(R.string.gold_pass_not_set)
                else if (gp.gold) stringResource(R.string.gold_pass_summary_gold, gp.builder, gp.research)
                else stringResource(R.string.gold_pass_silver_summary),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { gpOpen = true }) { Text(stringResource(R.string.gold_pass_change)) }
            var gpAsk by remember { mutableStateOf(GoldPassStore.askMonthly(context)) }
            SwitchRow(stringResource(R.string.gold_pass_ask_monthly), gpAsk) { on -> gpAsk = on; GoldPassStore.setAskMonthly(context, on) }
            if (gpOpen) {
                GoldPassDialog(
                    initial = gp,
                    laterLabel = stringResource(android.R.string.cancel),
                    onSave = { gold, b, r ->
                        GoldPassStore.save(context, selectedTag, GoldPassLogic.monthKey(now), gold, b, r)
                        gpOpen = false
                        gpScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
                    },
                    onLater = { gpOpen = false },
                )
            }
        }
        SectionCard(stringResource(R.string.settings_section_clock_tower)) {
            val ctScope = rememberCoroutineScope()
            var ctTrack by remember { mutableStateOf(ClockTowerTracking.enabled(context)) }
            SwitchRow(stringResource(R.string.settings_clock_tower), ctTrack) { on ->
                ctTrack = on
                ClockTowerTracking.setEnabled(context, on)
                ctScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
            }
            Text(stringResource(R.string.settings_clock_tower_desc), style = MaterialTheme.typography.bodyMedium)
            var ctAlert by remember { mutableStateOf(ClockTowerTracking.alertEnabled(context)) }
            SwitchRow(stringResource(R.string.settings_clock_tower_alert), ctAlert) { on ->
                ctAlert = on
                ClockTowerTracking.setAlertEnabled(context, on)
                ctScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
            }
            Text(stringResource(R.string.settings_clock_tower_alert_desc), style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(stringResource(R.string.settings_language)) {
            LanguageSetting()
        }
        SectionCard(stringResource(R.string.settings_section_notifications)) {
            SwitchRow(stringResource(R.string.settings_finish_alerts), settings.notificationsEnabled) { on ->
                onChange { it.copy(notificationsEnabled = on) }
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.allowed(context)) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Text(stringResource(R.string.settings_alert_desc), style = MaterialTheme.typography.bodyMedium)
            var respectDnd by remember { mutableStateOf(Notifier.respectDnd(context)) }
            SwitchRow(stringResource(R.string.settings_follow_dnd), respectDnd) { on ->
                respectDnd = on
                Notifier.setRespectDnd(context, on)
            }
            Text(stringResource(R.string.settings_follow_dnd_desc), style = MaterialTheme.typography.bodyMedium)
            if (settings.notificationsEnabled && !notificationsAllowed) {
                Text(stringResource(R.string.settings_notif_blocked), color = MaterialTheme.colorScheme.error)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(onClick = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.settings_allow_notif))
                    }
                }
                OutlinedButton(onClick = { openNotificationSettings(context); permissionTick++ }) { Text(stringResource(R.string.settings_notif_settings)) }
            }
            Text(
                stringResource(R.string.settings_battery_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { openAppSettings(context) }) { Text(stringResource(R.string.settings_battery_btn)) }
        }
        SectionCard(stringResource(R.string.settings_section_builders)) {
            val derived = export?.derivedTotalBuilders ?: AppSettings.DEFAULT_TOTAL_BUILDERS
            NumberSetting(
                title = stringResource(R.string.settings_total_builders),
                value = settings.totalBuildersOverride,
                automatic = derived,
                range = 1..10,
                unit = stringResource(R.string.unit_builders),
                description = stringResource(R.string.settings_builders_desc),
                automaticLabel = stringResource(R.string.settings_auto_builders),
            ) { value -> onChange { it.copy(totalBuildersOverride = value) } }
            SwitchRow(stringResource(R.string.settings_boost_goblin), b.boostsApplyToGoblin) { on ->
                boosts { it.copy(boostsApplyToGoblin = on) }
            }
        }
        SectionCard(stringResource(R.string.settings_section_potions)) {
            Text(stringResource(R.string.settings_potions_desc), style = MaterialTheme.typography.bodyMedium)
            HoursSetting(stringResource(R.string.potion_builder), b.builderPotionSavingSec, 1..47) { value -> boosts { it.copy(builderPotionSavingSec = value) } }
            HoursSetting(stringResource(R.string.potion_bite), b.builderBiteSavingSec, 1..23) { value -> boosts { it.copy(builderBiteSavingSec = value) } }
            HoursSetting(stringResource(R.string.potion_soup), b.studySoupSavingSec, 1..23) { value -> boosts { it.copy(studySoupSavingSec = value) } }
            HoursSetting(stringResource(R.string.potion_research), b.researchPotionSavingSec, 1..47) { value -> boosts { it.copy(researchPotionSavingSec = value) } }
            HoursSetting(stringResource(R.string.potion_pet), b.petPotionSavingSec, 1..47) { value -> boosts { it.copy(petPotionSavingSec = value) } }
        }
        SectionCard(stringResource(R.string.settings_section_helpers)) {
            Text(stringResource(R.string.settings_helpers_desc), style = MaterialTheme.typography.bodyMedium)
            NumberSetting(
                title = stringResource(R.string.helper_apprentice),
                value = b.apprenticeSavingSecOverride?.div(H)?.toInt(),
                automatic = export?.helper(GameIds.HELPER_BUILDERS_APPRENTICE)?.level,
                range = 1..24,
                unit = stringResource(R.string.unit_hours_per_session),
                automaticLabel = stringResource(R.string.settings_auto_helper),
            ) { value -> boosts { it.copy(apprenticeSavingSecOverride = value?.times(H)) } }
            NumberSetting(
                title = stringResource(R.string.helper_lab_assistant),
                value = b.labAssistantSavingSecOverride?.div(H)?.toInt(),
                automatic = export?.helper(GameIds.HELPER_LAB_ASSISTANT)?.level,
                range = 1..24,
                unit = stringResource(R.string.unit_hours_per_session),
                automaticLabel = stringResource(R.string.settings_auto_helper),
            ) { value -> boosts { it.copy(labAssistantSavingSecOverride = value?.times(H)) } }
            NumberSetting(
                title = stringResource(R.string.settings_helper_interval),
                value = (b.helperPeriodSec / H).toInt(),
                range = 2..48,
                unit = stringResource(R.string.unit_hours),
                description = stringResource(R.string.settings_interval_desc),
            ) { value -> value?.let { hours -> boosts { it.copy(helperPeriodSec = hours * H) } } }
        }
        SectionCard(stringResource(R.string.settings_section_widget)) {
            WidgetThemeSetting(settings, onChange)
            SwitchRow(stringResource(R.string.settings_live_countdown), settings.liveCountdown) { on -> onChange { it.copy(liveCountdown = on) } }
            Text(
                stringResource(R.string.settings_live_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.settings_reset))
        }
    }
    if (editName) {
        PlayerNameDialog(
            current = settings.playerName,
            firstLaunch = false,
            onDone = { name -> onChange { it.copy(playerName = name) }; editName = false },
            onDismiss = { editName = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onChange { AppSettings(liveCountdown = it.liveCountdown, playerName = it.playerName) }
                    confirmReset = false
                }) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun HoursSetting(title: String, seconds: Long, range: IntRange, onSet: (Long) -> Unit) {
    NumberSetting(
        title = title,
        value = (seconds / H).toInt(),
        range = range,
        unit = stringResource(R.string.unit_hours_saved),
        description = stringResource(R.string.speed_x, seconds / H + 1),
    ) { value -> value?.let { onSet(it * H) } }
}

@Composable
private fun NumberSetting(
    title: String,
    value: Int?,
    range: IntRange,
    unit: String,
    description: String? = null,
    automatic: Int? = null,
    automaticLabel: String? = null,
    onSet: (Int?) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val valueLabel = when {
        value != null -> stringResource(R.string.value_unit, value, unit.lowercase())
        automatic != null -> stringResource(R.string.automatic_value, automatic, unit.lowercase())
        else -> stringResource(R.string.automatic_missing)
    }
    SettingRow(title, valueLabel, description) { editing = true }
    if (editing) {
        SettingValueDialog(
            title = title,
            initialValue = value ?: automatic ?: range.first,
            range = range,
            unit = unit,
            automaticLabel = automaticLabel,
            onSave = { onSet(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, description: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = stringResource(R.string.edit_title, title), onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}


/** Language picker: "System default" plus every language listed in res/xml/locales_config.xml. */
@Composable
private fun LanguageSetting() {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val systemLabel = stringResource(R.string.language_system)
    val languages = remember { LanguageManager.available(context) }
    val options = listOf(LanguageManager.SYSTEM to systemLabel) + languages
    val currentLabel = options.firstOrNull { LanguageManager.isSelected(context, it.first) }?.second ?: systemLabel
    SettingRow(stringResource(R.string.language_row), currentLabel, stringResource(R.string.language_desc)) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    options.forEach { (tag, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.RadioButton) {
                                    open = false
                                    chooseLanguage(context, tag)
                                },
                        ) {
                            RadioButton(selected = LanguageManager.isSelected(context, tag), onClick = null)
                            Text(name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun chooseLanguage(context: Context, tag: String) {
    val app = context.applicationContext
    LanguageManager.set(context, tag)
    // Android 13+ recreates the activity itself; older versions need it done here.
    if (Build.VERSION.SDK_INT < 33) {
        var c: Context = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        (c as? Activity)?.recreate()
    }
    // Redraw the home-screen widgets in the new language (after the new locale is in effect).
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        delay(600)
        runCatching { SyncRunner.refreshWidgets(app, "language") }
    }
}

@Composable
private fun WidgetThemeSetting(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val themes = listOf(
        "material_you" to stringResource(R.string.widget_theme_material_you),
        "clash" to stringResource(R.string.widget_theme_clash),
        "dark" to stringResource(R.string.widget_theme_dark),
        "light" to stringResource(R.string.widget_theme_light),
    )
    val currentLabel = themes.firstOrNull { it.first == settings.widgetTheme }?.second ?: stringResource(R.string.widget_theme_material_you)

    SettingRow(stringResource(R.string.widget_theme), currentLabel, null) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.widget_theme)) },
            text = {
                Column {
                    themes.forEach { (key, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.RadioButton) {
                                    open = false
                                    onChange { it.copy(widgetTheme = key) }
                                    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                                        delay(300)
                                        runCatching { SyncRunner.refreshWidgets(context, "theme") }
                                    }
                                },
                        ) {
                            RadioButton(selected = settings.widgetTheme == key, onClick = null)
                            Text(name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
