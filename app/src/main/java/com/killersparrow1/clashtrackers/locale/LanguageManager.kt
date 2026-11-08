package com.killersparrow1.clashtrackers.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.killersparrow1.clashtrackers.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * In-app language choice.
 *
 * - The list of languages comes from `res/xml/locales_config.xml` (one `<locale android:name="xx"/>` per
 *   language), so adding a language means adding `res/values-xx/strings.xml` plus one line there.
 * - Android 13+: the choice is stored by the system (the same setting as phone Settings > App languages).
 * - Android 8-12: the choice is stored in a small SharedPreferences file and applied by [wrap].
 * - An empty tag ([SYSTEM]) means "follow the phone's language".
 */
object LanguageManager {
    const val SYSTEM = ""
    private const val PREFS = "language"
    private const val KEY = "tag"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** The language tag currently chosen by the user, or [SYSTEM]. */
    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            val list = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (list == null || list.isEmpty) SYSTEM else list[0].toLanguageTag()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM
        }

    /** Saves the choice. On Android 12 and older the caller must also recreate its activity. */
    fun set(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        }
    }

    /**
     * Use in `attachBaseContext` of every activity (and for text drawn outside an activity, such as the
     * widget). On Android 13+ the system already applies the language, so this returns [base] unchanged.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Languages offered in the picker: tag plus its own name, written in that language ("Русский"). */
    fun available(context: Context): List<Pair<String, String>> {
        val tags = mutableListOf<String>()
        runCatching {
            val parser = context.resources.getXml(R.xml.locales_config)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue(ANDROID_NS, "name")?.let(tags::add)
                }
            }
        }
        return tags.map { tag ->
            val locale = Locale.forLanguageTag(tag)
            tag to locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
        }
    }

    /** True if [tag] and the current choice are the same language (so "ru" matches "ru-RU"). */
    fun isSelected(context: Context, tag: String): Boolean {
        val chosen = current(context)
        if (chosen.isEmpty() || tag.isEmpty()) return chosen == tag
        return Locale.forLanguageTag(chosen).language == Locale.forLanguageTag(tag).language
    }
}
