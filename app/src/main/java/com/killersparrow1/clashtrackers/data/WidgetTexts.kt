package com.killersparrow1.clashtrackers.data

import android.content.Context
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.core.WidgetText
import com.killersparrow1.clashtrackers.locale.LanguageManager

/** Builds the localized [WidgetText] used by the widget model, from string resources. */
object WidgetTexts {
    fun get(context: Context): WidgetText {
        val r = LanguageManager.wrap(context).resources
        return WidgetText(
            day = r.getString(R.string.time_day_short),
            hour = r.getString(R.string.time_hour_short),
            minute = r.getString(R.string.time_minute_short),
            completed = r.getString(R.string.widget_completed),
            idle = r.getString(R.string.widget_idle),
            builders = r.getString(R.string.widget_builders),
            goblinSuffix = r.getString(R.string.widget_goblin_suffix),
            bite = r.getString(R.string.widget_bite),
            plusOne = r.getString(R.string.widget_plus_one),
            syncFailed = r.getString(R.string.widget_sync_failed),
            noData = r.getString(R.string.widget_no_data),
            synced = r.getString(R.string.widget_synced),
            helpers = r.getString(R.string.widget_helpers),
            apprenticeName = r.getString(R.string.widget_apprentice_name),
            assistantName = r.getString(R.string.widget_assistant_name),
            apprenticeIdle = r.getString(R.string.widget_apprentice_idle),
            assistantIdle = r.getString(R.string.widget_assistant_idle),
            laboratory = r.getString(R.string.widget_laboratory),
            petHouse = r.getString(R.string.widget_pet_house),
            newSuffix = r.getString(R.string.widget_new),
            unknownName = r.getString(R.string.widget_unknown_name),
            craftedDefense = r.getString(R.string.widget_crafted_defense),
            moduleLabel = r.getString(R.string.widget_module_label),
            townHallTag = r.getString(R.string.widget_th_tag),
        )
    }
}
