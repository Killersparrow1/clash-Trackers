package com.killersparrow1.clashtrackers.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.killersparrow1.clashtrackers.core.GoldPassReduction

/** The player's Gold Pass answer, per village, and whether to ask every month. */
object GoldPassStore {
    private const val PREFS = "gold_pass"
    private const val KEY_ASK = "ask_monthly"

    /** Bumped on every save so screens that read it redraw. */
    var version by mutableIntStateOf(0)
        private set

    data class Answer(val month: String?, val gold: Boolean, val builder: Int, val research: Int)

    private fun key(tag: String?, name: String) = "${tag.orEmpty()}:$name"

    fun answer(context: Context, tag: String?): Answer {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Answer(
            month = p.getString(key(tag, "month"), null),
            gold = p.getBoolean(key(tag, "gold"), false),
            builder = p.getInt(key(tag, "builder"), 0),
            research = p.getInt(key(tag, "research"), 0),
        )
    }

    /** What the timers should use: the reductions when the answer is Gold, otherwise none. */
    fun reduction(context: Context, tag: String?): GoldPassReduction {
        val a = answer(context, tag)
        return if (a.gold) GoldPassReduction(a.builder, a.research) else GoldPassReduction.NONE
    }

    fun save(context: Context, tag: String?, month: String, gold: Boolean, builder: Int, research: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(tag, "month"), month)
            .putBoolean(key(tag, "gold"), gold)
            .putInt(key(tag, "builder"), if (gold) builder else 0)
            .putInt(key(tag, "research"), if (gold) research else 0)
            .apply()
        version++
    }

    /** Forget this village's answer and turn the monthly question back on, so the popup shows again (testing). */
    fun reset(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(tag, "month")).remove(key(tag, "gold")).remove(key(tag, "builder")).remove(key(tag, "research"))
            .putBoolean(KEY_ASK, true)
            .apply()
        version++
    }

    fun askMonthly(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASK, true)

    fun setAskMonthly(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASK, on).apply()
        version++
    }
}
