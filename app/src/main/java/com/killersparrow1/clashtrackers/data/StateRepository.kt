package com.killersparrow1.clashtrackers.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.killersparrow1.clashtrackers.core.AccountMeta
import com.killersparrow1.clashtrackers.core.AccountSettings
import com.killersparrow1.clashtrackers.core.AccountsIndex
import com.killersparrow1.clashtrackers.core.AppSettings
import com.killersparrow1.clashtrackers.core.AppState
import com.killersparrow1.clashtrackers.core.GlobalSettings
import com.killersparrow1.clashtrackers.core.NameMap
import com.killersparrow1.clashtrackers.core.SyncLog
import com.killersparrow1.clashtrackers.core.SyncLogEntry
import com.killersparrow1.clashtrackers.core.UpgradeTimes
import com.killersparrow1.clashtrackers.core.normalizeTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clash_trackers")

/**
 * All persisted state lives in one Preferences DataStore supporting multi-account storage and global settings.
 */
class StateRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private suspend fun ensureMigrated(prefs: MutablePreferences) {
        if (prefs[ACCOUNTS_INDEX] != null) return
        val oldStateText = prefs[OLD_STATE]
        val oldSettingsText = prefs[OLD_SETTINGS]
        val oldState = AppState.decode(oldStateText)
        val oldSettings = AppSettings.decode(oldSettingsText)

        val global = GlobalSettings(
            notificationsEnabled = oldSettings.notificationsEnabled,
            liveCountdown = oldSettings.liveCountdown,
            widgetTheme = oldSettings.widgetTheme,
        )
        prefs[GLOBAL_SETTINGS] = global.encode()

        val tag = oldState.export?.tag?.let { normalizeTag(it) }
        if (tag != null) {
            prefs[stateKey(tag)] = oldState.encode()
            val acctSettings = AccountSettings(
                totalBuildersOverride = oldSettings.totalBuildersOverride,
                boosts = oldSettings.boosts,
                playerName = oldSettings.playerName,
            )
            prefs[settingsKey(tag)] = acctSettings.encode()
            val index = AccountsIndex(
                accounts = listOf(AccountMeta(tag, oldSettings.playerName)),
                selectedTag = tag,
            )
            prefs[ACCOUNTS_INDEX] = index.encode()
        } else {
            prefs[ACCOUNTS_INDEX] = AccountsIndex().encode()
        }
    }

    val accounts: Flow<List<AccountMeta>> = store.data.map { prefs ->
        val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
        if (index.accounts.isEmpty()) {
            val oldState = AppState.decode(prefs[OLD_STATE])
            val tag = oldState.export?.tag?.let { normalizeTag(it) }
            if (tag != null) {
                val oldSettings = AppSettings.decode(prefs[OLD_SETTINGS])
                listOf(AccountMeta(tag, oldSettings.playerName))
            } else emptyList()
        } else {
            index.accounts
        }
    }.distinctUntilChanged()

    val selectedAccountTag: Flow<String?> = store.data.map { prefs ->
        val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
        val tag = index.selectedTag ?: index.accounts.firstOrNull()?.tag
        if (tag == null) {
            val oldState = AppState.decode(prefs[OLD_STATE])
            oldState.export?.tag?.let { normalizeTag(it) }
        } else {
            tag
        }
    }.distinctUntilChanged()

    val state: Flow<AppState> = store.data.map { prefs ->
        val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
        val tag = index.selectedTag ?: index.accounts.firstOrNull()?.tag
        if (tag != null) {
            AppState.decode(prefs[stateKey(tag)])
        } else {
            AppState.decode(prefs[OLD_STATE])
        }
    }.distinctUntilChanged()

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        val global = GlobalSettings.decode(prefs[GLOBAL_SETTINGS])
        val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
        val tag = index.selectedTag ?: index.accounts.firstOrNull()?.tag
        val acctSettings = if (tag != null) {
            AccountSettings.decode(prefs[settingsKey(tag)])
        } else {
            val oldSettings = AppSettings.decode(prefs[OLD_SETTINGS])
            AccountSettings(
                totalBuildersOverride = oldSettings.totalBuildersOverride,
                boosts = oldSettings.boosts,
                playerName = oldSettings.playerName,
            )
        }
        AppSettings(
            totalBuildersOverride = acctSettings.totalBuildersOverride,
            boosts = acctSettings.boosts,
            notificationsEnabled = global.notificationsEnabled,
            liveCountdown = global.liveCountdown,
            playerName = acctSettings.playerName,
            widgetTheme = global.widgetTheme,
        )
    }.distinctUntilChanged()

    val syncLog: Flow<SyncLog> = store.data.map { SyncLog.decode(it[SYNC_LOG]) }.distinctUntilChanged()

    suspend fun currentState(): AppState {
        val prefs = store.data.first()
        store.edit { ensureMigrated(it) }
        val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
        val tag = index.selectedTag ?: index.accounts.firstOrNull()?.tag
        return tag?.let { AppState.decode(prefs[stateKey(it)]) } ?: AppState.decode(prefs[OLD_STATE])
    }

    suspend fun currentSettings(): AppSettings {
        val prefs = store.data.first()
        store.edit { ensureMigrated(it) }
        val global = GlobalSettings.decode(prefs[GLOBAL_SETTINGS])
        val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
        val tag = index.selectedTag ?: index.accounts.firstOrNull()?.tag
        val acct = tag?.let { AccountSettings.decode(prefs[settingsKey(it)]) }
            ?: AccountSettings.decode(prefs[OLD_SETTINGS])
        return AppSettings(
            totalBuildersOverride = acct.totalBuildersOverride,
            boosts = acct.boosts,
            notificationsEnabled = global.notificationsEnabled,
            liveCountdown = global.liveCountdown,
            playerName = acct.playerName,
            widgetTheme = global.widgetTheme,
        )
    }

    suspend fun selectAccount(tag: String) {
        val norm = normalizeTag(tag)
        store.edit { prefs ->
            ensureMigrated(prefs)
            val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
            if (index.accounts.any { it.tag == norm }) {
                prefs[ACCOUNTS_INDEX] = index.copy(selectedTag = norm).encode()
            }
        }
    }

    suspend fun ensureAccountAndSelect(tag: String) {
        val norm = normalizeTag(tag)
        store.edit { prefs ->
            ensureMigrated(prefs)
            val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
            val exists = index.accounts.any { it.tag == norm }
            val updatedAccounts = if (exists) index.accounts else index.accounts + AccountMeta(norm, null)
            prefs[ACCOUNTS_INDEX] = index.copy(accounts = updatedAccounts, selectedTag = norm).encode()
        }
    }

    suspend fun renameAccount(tag: String, newName: String) {
        val norm = normalizeTag(tag)
        val cleaned = AppSettings.cleanName(newName)
        store.edit { prefs ->
            ensureMigrated(prefs)
            val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
            val updatedAccounts = index.accounts.map { if (it.tag == norm) it.copy(name = cleaned) else it }
            prefs[ACCOUNTS_INDEX] = index.copy(accounts = updatedAccounts).encode()
            val acct = AccountSettings.decode(prefs[settingsKey(norm)])
            prefs[settingsKey(norm)] = acct.copy(playerName = cleaned).encode()
        }
    }

    suspend fun deleteAccount(tag: String) {
        val norm = normalizeTag(tag)
        store.edit { prefs ->
            ensureMigrated(prefs)
            val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
            val remaining = index.accounts.filter { it.tag != norm }
            val nextSelected = if (index.selectedTag == norm) remaining.firstOrNull()?.tag else index.selectedTag
            prefs[ACCOUNTS_INDEX] = AccountsIndex(accounts = remaining, selectedTag = nextSelected).encode()
            prefs.remove(stateKey(norm))
            prefs.remove(settingsKey(norm))

            val mapJson = prefs[WIDGET_MAPPING]
            if (mapJson != null) {
                val map = (runCatching { AppState.JSON.decodeFromString<Map<String, String>>(mapJson) }.getOrNull() ?: emptyMap()).toMutableMap()
                val keysToRemove = map.filterValues { it == norm }.keys
                if (keysToRemove.isNotEmpty()) {
                    map.keys.removeAll(keysToRemove)
                    prefs[WIDGET_MAPPING] = AppState.JSON.encodeToString(map)
                }
            }
        }
    }

    suspend fun getAccountState(tag: String): AppState {
        val norm = normalizeTag(tag)
        val prefs = store.data.first()
        store.edit { ensureMigrated(it) }
        return AppState.decode(prefs[stateKey(norm)])
    }

    suspend fun updateAccountState(tag: String, transform: (AppState) -> AppState) {
        val norm = normalizeTag(tag)
        store.edit { prefs ->
            ensureMigrated(prefs)
            val current = AppState.decode(prefs[stateKey(norm)])
            prefs[stateKey(norm)] = transform(current).encode()
        }
    }

    suspend fun getAccountSettings(tag: String): AccountSettings {
        val norm = normalizeTag(tag)
        val prefs = store.data.first()
        store.edit { ensureMigrated(it) }
        return AccountSettings.decode(prefs[settingsKey(norm)])
    }

    suspend fun updateAccountSettings(tag: String, transform: (AccountSettings) -> AccountSettings) {
        val norm = normalizeTag(tag)
        store.edit { prefs ->
            ensureMigrated(prefs)
            val current = AccountSettings.decode(prefs[settingsKey(norm)])
            val updated = transform(current)
            prefs[settingsKey(norm)] = updated.encode()
            val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
            val updatedAccounts = index.accounts.map { if (it.tag == norm) it.copy(name = updated.playerName) else it }
            prefs[ACCOUNTS_INDEX] = index.copy(accounts = updatedAccounts).encode()
        }
    }

    suspend fun getWidgetAccount(appWidgetId: Int): String? {
        val prefs = store.data.first()
        store.edit { ensureMigrated(it) }
        val mapJson = prefs[WIDGET_MAPPING] ?: return null
        val map = runCatching { AppState.JSON.decodeFromString<Map<String, String>>(mapJson) }.getOrNull() ?: emptyMap()
        return map[appWidgetId.toString()]
    }

    suspend fun setWidgetAccount(appWidgetId: Int, tag: String) {
        val norm = normalizeTag(tag)
        store.edit { prefs ->
            ensureMigrated(prefs)
            val mapJson = prefs[WIDGET_MAPPING]
            val map = (mapJson?.let { runCatching { AppState.JSON.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()).toMutableMap()
            map[appWidgetId.toString()] = norm
            prefs[WIDGET_MAPPING] = AppState.JSON.encodeToString(map)
        }
    }

    suspend fun removeWidgetAccount(appWidgetId: Int) {
        store.edit { prefs ->
            ensureMigrated(prefs)
            val mapJson = prefs[WIDGET_MAPPING] ?: return@edit
            val map = (runCatching { AppState.JSON.decodeFromString<Map<String, String>>(mapJson) }.getOrNull() ?: emptyMap()).toMutableMap()
            if (map.remove(appWidgetId.toString()) != null) {
                prefs[WIDGET_MAPPING] = AppState.JSON.encodeToString(map)
            }
        }
    }

    suspend fun getAllWidgetMappings(): Map<Int, String> {
        val prefs = store.data.first()
        store.edit { ensureMigrated(it) }
        val mapJson = prefs[WIDGET_MAPPING] ?: return emptyMap()
        val map = runCatching { AppState.JSON.decodeFromString<Map<String, String>>(mapJson) }.getOrNull() ?: emptyMap()
        return map.mapKeys { it.key.toIntOrNull() ?: 0 }.filterKeys { it != 0 }
    }

    suspend fun getWidgetTheme(appWidgetId: Int): String {
        store.edit { ensureMigrated(it) }
        val prefs = store.data.first()
        val global = GlobalSettings.decode(prefs[GLOBAL_SETTINGS])
        val mapJson = prefs[WIDGET_THEME] ?: return global.widgetTheme
        val map = runCatching { AppState.JSON.decodeFromString<Map<String, String>>(mapJson) }.getOrNull() ?: emptyMap()
        return map[appWidgetId.toString()] ?: global.widgetTheme
    }

    suspend fun setWidgetTheme(appWidgetId: Int, theme: String) {
        store.edit { prefs ->
            ensureMigrated(prefs)
            val mapJson = prefs[WIDGET_THEME]
            val map = (mapJson?.let { runCatching { AppState.JSON.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()).toMutableMap()
            map[appWidgetId.toString()] = theme
            prefs[WIDGET_THEME] = AppState.JSON.encodeToString(map)
        }
    }

    /** Atomically read-modify-write the app state of the selected account (or create/save if new account). */
    suspend fun <R> updateState(transform: (AppState) -> Pair<AppState, R>): R {
        var result: R? = null
        store.edit { prefs ->
            ensureMigrated(prefs)
            val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
            val tag = index.selectedTag ?: index.accounts.firstOrNull()?.tag
            val key = tag?.let { stateKey(it) } ?: OLD_STATE
            val (next, r) = transform(AppState.decode(prefs[key]))
            prefs[key] = next.encode()
            result = r
        }
        @Suppress("UNCHECKED_CAST")
        return result as R
    }

    /** Appends to the on-screen sync log; never throws (it is diagnostics only). */
    suspend fun log(source: String, ok: Boolean?, message: String) {
        runCatching {
            store.edit { prefs ->
                ensureMigrated(prefs)
                val entry = SyncLogEntry(System.currentTimeMillis() / 1000, source, ok, message)
                prefs[SYNC_LOG] = SyncLog.decode(prefs[SYNC_LOG]).add(entry).encode()
            }
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            ensureMigrated(prefs)
            val global = GlobalSettings.decode(prefs[GLOBAL_SETTINGS])
            val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
            val tag = index.selectedTag ?: index.accounts.firstOrNull()?.tag
            val acctKey = tag?.let { settingsKey(it) } ?: OLD_SETTINGS
            val acct = tag?.let { AccountSettings.decode(prefs[settingsKey(it)]) }
                ?: AccountSettings.decode(prefs[OLD_SETTINGS])

            val currentAppSet = AppSettings(
                totalBuildersOverride = acct.totalBuildersOverride,
                boosts = acct.boosts,
                notificationsEnabled = global.notificationsEnabled,
                liveCountdown = global.liveCountdown,
                playerName = acct.playerName,
                widgetTheme = global.widgetTheme,
            )
            val next = transform(currentAppSet)

            prefs[GLOBAL_SETTINGS] = GlobalSettings(
                notificationsEnabled = next.notificationsEnabled,
                liveCountdown = next.liveCountdown,
                widgetTheme = next.widgetTheme,
            ).encode()

            val updatedAcct = AccountSettings(
                totalBuildersOverride = next.totalBuildersOverride,
                boosts = next.boosts,
                playerName = next.playerName,
            )
            prefs[acctKey] = updatedAcct.encode()
            if (tag != null) {
                val updatedAccounts = index.accounts.map { if (it.tag == tag) it.copy(name = next.playerName) else it }
                prefs[ACCOUNTS_INDEX] = index.copy(accounts = updatedAccounts).encode()
            }
        }
    }

    suspend fun modifyState(transform: (AppState) -> AppState) {
        store.edit { prefs ->
            ensureMigrated(prefs)
            val index = AccountsIndex.decode(prefs[ACCOUNTS_INDEX])
            val tag = index.selectedTag ?: index.accounts.firstOrNull()?.tag
            val key = tag?.let { stateKey(it) } ?: OLD_STATE
            prefs[key] = transform(AppState.decode(prefs[key])).encode()
        }
    }

    companion object {
        val OLD_STATE = stringPreferencesKey("state_json")
        val OLD_SETTINGS = stringPreferencesKey("settings_json")
        val ACCOUNTS_INDEX = stringPreferencesKey("accounts_index_json")
        val GLOBAL_SETTINGS = stringPreferencesKey("global_settings_json")
        val WIDGET_MAPPING = stringPreferencesKey("widget_mapping_json")
        val WIDGET_THEME = stringPreferencesKey("widget_theme_json")
        val SYNC_LOG = stringPreferencesKey("sync_log_json")

        fun stateKey(tag: String) = stringPreferencesKey("state_json_$tag")
        fun settingsKey(tag: String) = stringPreferencesKey("settings_json_$tag")
    }
}

/** Bundled `assets/names.json`, loaded once per process. */
object Names {
    @Volatile private var cached: NameMap? = null

    fun get(context: Context): NameMap = cached ?: synchronized(this) {
        cached ?: context.applicationContext.assets.open("names.json").bufferedReader().use {
            NameMap.fromJson(it.readText())
        }.also { cached = it }
    }
}

/** Bundled `assets/upgrade_times.json` (full upgrade durations for the progress bars), loaded once. */
object Times {
    @Volatile private var cached: UpgradeTimes? = null

    fun get(context: Context): UpgradeTimes = cached ?: synchronized(this) {
        cached ?: runCatching {
            context.applicationContext.assets.open("upgrade_times.json").bufferedReader().use {
                UpgradeTimes.fromJson(it.readText())
            }
        }.getOrDefault(UpgradeTimes.EMPTY).also { cached = it }
    }
}
