package com.killersparrow1.clashtrackers.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.R
import kotlinx.coroutines.delay
import java.time.LocalDate

private const val REPO_URL = "https://github.com/Killersparrow1/clash-Trackers/"
private const val REDDIT_URL = "https://www.reddit.com/"
private const val TELEGRAM_URL = "https://t.me/+KRUnoSeOBTpiMzQ9"

/** One bit per button, so the popup can stop once the person has used all of them. */
internal const val CLICK_STAR = 1
internal const val CLICK_SHARE = 2
internal const val CLICK_REDDIT = 4
internal const val CLICK_TELEGRAM = 8
internal const val CLICK_ALL = 15

/** What is stored on the phone for the monthly popup. Nothing here leaves the device. */
internal data class CommunityState(
    /** Year * 12 + month of the last time it was shown, or -1. */
    val shownMonth: Int = -1,
    /** Epoch day of the last time it was shown, or -1. */
    val lastShownDay: Long = -1,
    /** Month the chosen day belongs to, or -1. */
    val targetMonth: Int = -1,
    /** Chosen day of that month; 0 means no day of the month is eligible. */
    val targetDay: Int = 0,
    /** Buttons the person has pressed so far (CLICK_* bits). Once all are pressed the popup never comes back. */
    val clicked: Int = 0,
)

/** The scheduling rules, free of Android so they can be tested. */
internal object CommunitySchedule {
    const val COOLDOWN_DAYS = 14L

    fun monthKey(d: LocalDate) = d.year * 12 + (d.monthValue - 1)

    /** Picks this month's day once: a random one from the days that are still ahead and 14+ days after the last display. */
    fun plan(state: CommunityState, today: LocalDate, random: (Int) -> Int): CommunityState {
        val month = monthKey(today)
        if (state.targetMonth == month) return state
        val earliest = if (state.lastShownDay < 0) today else maxOf(today, LocalDate.ofEpochDay(state.lastShownDay + COOLDOWN_DAYS))
        val first = if (monthKey(earliest) == month) earliest.dayOfMonth else Int.MAX_VALUE
        val last = today.lengthOfMonth()
        val day = if (first > last) 0 else first + random(last - first + 1)
        return state.copy(targetMonth = month, targetDay = day)
    }

    /** True when the chosen day has come, this month has not shown it yet and the cooldown is over. */
    fun due(state: CommunityState, today: LocalDate): Boolean {
        val month = monthKey(today)
        return state.clicked != CLICK_ALL && state.targetMonth == month && state.targetDay in 1..today.dayOfMonth &&
            state.shownMonth != month &&
            (state.lastShownDay < 0 || today.toEpochDay() - state.lastShownDay >= COOLDOWN_DAYS)
    }

    fun shown(state: CommunityState, today: LocalDate) =
        state.copy(shownMonth = monthKey(today), lastShownDay = today.toEpochDay())
}

internal object CommunityStore {
    private const val PREFS = "community_prompt"

    fun load(context: Context): CommunityState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return CommunityState(
            p.getInt("shown_month", -1), p.getLong("last_shown_day", -1),
            p.getInt("target_month", -1), p.getInt("target_day", 0), p.getInt("clicked", 0),
        )
    }

    fun save(context: Context, s: CommunityState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("shown_month", s.shownMonth).putLong("last_shown_day", s.lastShownDay)
            .putInt("target_month", s.targetMonth).putInt("target_day", s.targetDay).putInt("clicked", s.clicked).apply()
    }

    /** Only one display per app session. */
    var shownThisSession = false
}

/**
 * The monthly "support the app" popup. It waits until the person has been using the app for a while, stays away from
 * the setup and other prompts ([blocked]), and counts a dismissal as shown. [test] shows it right away without
 * touching the saved schedule (Developer options).
 */
@Composable
internal fun CommunityPrompt(blocked: Boolean, test: Boolean, onTestDone: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var fromTest by remember { mutableStateOf(false) }

    LaunchedEffect(test) {
        if (test) { fromTest = true; open = true }
    }
    // A month's day is picked once; the popup then waits for calm use of the app, never the first moments after launch.
    LaunchedEffect(blocked) {
        if (blocked || CommunityStore.shownThisSession || open) return@LaunchedEffect
        val today = LocalDate.now()
        val state = CommunitySchedule.plan(CommunityStore.load(context), today) { kotlin.random.Random.nextInt(it) }
        CommunityStore.save(context, state)
        if (!CommunitySchedule.due(state, today)) return@LaunchedEffect
        delay(60_000)
        if (!CommunityStore.shownThisSession && !open) {
            CommunityStore.shownThisSession = true
            CommunityStore.save(context, CommunitySchedule.shown(state, today))
            open = true
        }
    }
    if (!open) return

    // A button press is remembered (never in a test); pressing every button means the person has done it all.
    val pressed = { bit: Int ->
        if (!fromTest) CommunityStore.load(context).let { CommunityStore.save(context, it.copy(clicked = it.clicked or bit)) }
    }
    val close = {
        open = false
        if (fromTest) { fromTest = false; onTestDone() }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.community_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.community_text))
                Button(onClick = { pressed(CLICK_STAR); openLink(context, REPO_URL); close() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.community_star))
                }
                OutlinedButton(onClick = { pressed(CLICK_SHARE); share(context); close() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.community_share))
                }
                OutlinedButton(onClick = { pressed(CLICK_REDDIT); openLink(context, REDDIT_URL); close() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.community_reddit))
                }
                OutlinedButton(onClick = { pressed(CLICK_TELEGRAM); openLink(context, TELEGRAM_URL); close() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.community_telegram))
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.perm_later)) } },
    )
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context) {
    val text = context.getString(R.string.community_share_text, context.getString(R.string.app_name), REPO_URL)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
