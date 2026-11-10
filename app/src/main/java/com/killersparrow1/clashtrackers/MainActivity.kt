@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.saveable.rememberSaveable
import com.killersparrow1.clashtrackers.ui.SplashOverlay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.killersparrow1.clashtrackers.core.AppSettings
import com.killersparrow1.clashtrackers.core.AppState
import com.killersparrow1.clashtrackers.core.SyncLog
import com.killersparrow1.clashtrackers.core.WidgetModelBuilder
import com.killersparrow1.clashtrackers.data.Names
import com.killersparrow1.clashtrackers.data.StateRepository
import com.killersparrow1.clashtrackers.data.Times
import com.killersparrow1.clashtrackers.data.WidgetTexts
import com.killersparrow1.clashtrackers.sync.SyncRunner
import com.killersparrow1.clashtrackers.ui.ClashTrackersContent
import com.killersparrow1.clashtrackers.ui.PlayerNameDialog
import com.killersparrow1.clashtrackers.sync.ClipboardAutoSync
import androidx.lifecycle.lifecycleScope
import com.killersparrow1.clashtrackers.ui.theme.ClashTimerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.killersparrow1.clashtrackers.locale.LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The start-up animation plays on a fresh launch only (not after a rotation or a process restore).
        val playSplash = savedInstanceState == null
        setContent {
            ClashTimerTheme {
                var splash by rememberSaveable { mutableStateOf(playSplash) }
                Box(Modifier.fillMaxSize()) {
                    ClashTimersApp()
                    AnimatedVisibility(visible = splash, exit = fadeOut()) { SplashOverlay { splash = false } }
                }
            }
        }
    }

    /** Android 10+ only lets the focused app read the clipboard, so the check waits for window focus. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val clip = ClipboardAutoSync.pending(this, SyncRunner.readClipboard(this)) ?: return
        ClipboardAutoSync.markUsed(this, clip)
        lifecycleScope.launch { runCatching { SyncRunner.sync(this@MainActivity, clip, "auto") } }
    }
}

private data class ScreenData(val state: AppState, val settings: AppSettings, val log: SyncLog)
private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: ScreenData) : LoadState
    data class Failed(val message: String) : LoadState
}

@Composable
private fun ClashTimersApp() {
    val context = LocalContext.current
    val repo = remember { StateRepository(context) }
    val names = remember { Names.get(context) }
    val times = remember { Times.get(context) }
    val widgetText = remember { WidgetTexts.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var retry by remember { mutableIntStateOf(0) }

    // Accounts synced before the Town Hall picture existed get their level filled in once.
    LaunchedEffect(Unit) { repo.backfillTownHall() }
    val accounts by repo.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedTag by repo.selectedAccountTag.collectAsStateWithLifecycle(initialValue = null)

    val dataFlow = remember(repo, retry) {
        combine(repo.state, repo.settings, repo.syncLog, ::ScreenData)
            .map<ScreenData, LoadState> { LoadState.Ready(it) }
            .catch { emit(LoadState.Failed(it.message ?: "Could not read saved village data.")) }
    }
    val loadState by dataFlow.collectAsStateWithLifecycle(initialValue = LoadState.Loading)
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis() / 1000
                delay(1_000)
            }
        }
    }
    var busy by remember { mutableStateOf(false) }

    when (val loaded = loadState) {
        LoadState.Loading -> Surface {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoadingIndicator()
                Text("Loading your village…", Modifier.padding(top = 16.dp))
            }
        }
        is LoadState.Failed -> Surface {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text("Could not load your village", style = MaterialTheme.typography.titleLarge)
                Text(loaded.message)
                Button(onClick = { retry++ }) { Text("Try again") }
            }
        }
        is LoadState.Ready -> {
            val (state, settings, log) = loaded.data
            val model = remember(state, settings, now) { WidgetModelBuilder.build(state, settings, names, times, now, selectedTag ?: "", text = widgetText) }
            val changeSettings: ((AppSettings) -> AppSettings) -> Unit = { change ->
                scope.launch {
                    try {
                        repo.updateSettings(change)
                        SyncRunner.refreshWidgets(context, "app")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Could not finish updating settings: ${e.message}")
                    }
                }
            }
            val changeState: ((AppState) -> AppState) -> Unit = { change ->
                scope.launch {
                    try {
                        repo.modifyState(change)
                        SyncRunner.refreshWidgets(context, "app")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Could not update boosts or events: ${e.message}")
                    }
                }
            }
            if (settings.needsPlayerName && state.export != null) {
                val answer = { name: String -> changeSettings { it.copy(playerName = name) } }
                PlayerNameDialog(null, true, onDone = answer, onDismiss = { answer("") })
            }
            ClashTrackersContent(
                state = state,
                settings = settings,
                model = model,
                log = log,
                accounts = accounts,
                selectedTag = selectedTag,
                now = now,
                timesCount = times.size,
                busy = busy,
                onSync = {
                    if (!busy) {
                        busy = true
                        val clipboard = SyncRunner.readClipboard(context)
                        ClipboardAutoSync.markUsed(context, clipboard)
                        scope.launch {
                            try {
                                SyncRunner.sync(context, clipboard, "app")
                                now = System.currentTimeMillis() / 1000
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Could not finish syncing: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                onSettingsChange = changeSettings,
                onStateChange = changeState,
                onSelectAccount = { tag -> scope.launch { repo.selectAccount(tag) } },
                onRenameAccount = { tag, name -> scope.launch { repo.renameAccount(tag, name) } },
                onDeleteAccount = { tag -> scope.launch { repo.deleteAccount(tag); SyncRunner.refreshWidgets(context, "app") } },
                snackbarHost = snackbar,
            )
        }
    }
}
