# Contributing

Thanks for helping out. Bug reports, new game IDs and small fixes are all welcome.

- **Bugs:** use the [bug report template](https://github.com/Kuswaa/clash-timers/issues/new/choose) and include the crash report from the app if there is one.
- **Larger changes:** open an issue first, so we can agree on the approach before you spend time on it.
- **Tests:** run `./gradlew :app:testDebugUnitTest` before opening a pull request, and add tests for changes to the parser or the timer engine.

A few rules keep the project working:

- **Never commit a real village export.** It contains your player tag. Test data goes in `app/src/test/resources/fixtures`, with the tag replaced by `#TEST000`.
- **Keep R8 off.** Code shrinking caused a start-up crash on Android 10 that the build checks didn't catch.
- **Widget layouts** can only use classes that RemoteViews allows. Anything else, even a plain `View`, stops the whole widget from loading.
- **No network access.** The app has no internet permission, and that won't change.

## Adding a language

The app is translated through normal Android string resources, so the core of a language takes three small steps and no Kotlin code. A full translation adds one optional JSON file.

1. **Copy the English file.** Create `app/src/main/res/values-xx/strings.xml`, where `xx` is the language code (`ru` for Russian, `es` for Spanish, `pt-rBR` for Brazilian Portuguese; see the [Android resource qualifiers](https://developer.android.com/guide/topics/resources/providing-resources#AlternativeResources)). Start from `app/src/main/res/values/strings.xml` and translate the text between the tags. This file covers the UI **and** the widget: countdown units (`1д 20:12:05`), footer labels ("Completed", "Helpers: …"), and the Support diagnostics all come out of it.
2. **Keep the placeholders.** Text like `%1$s`, `%1$d` and `%%` is filled in by the app. Keep every placeholder from the English text; you may reorder them (`%2$d ... %1$s`). Use `\'` for an apostrophe and `&amp;` for `&`. Leave `app_name` and `widget_name` out unless you really want to change them.
3. **Register the language.** Add one line to `app/src/main/res/xml/locales_config.xml`: `<locale android:name="xx" />`. This is what makes the language appear in **Settings > Language** (shown by its own name, for example "Русский") and in Android's own *App languages* screen.
4. **(Optional) Translate the game names.** The 250 upgrade, building, troop, pet, spell, equipment and trap names live in `app/src/main/assets/names.json` (English). Copy it to `names_xx.json` inside the same `assets/` folder and translate each `"name"` value — the numeric keys are the game's internal IDs and must stay untouched. Then add one line to the `LOCALIZED_FILES` map in `StateRepository.Names` (about 20 lines up in `app/src/main/java/com/killersparrow1/clashtrackers/data/StateRepository.kt`), for example `"es" to "names_es.json"`. If you skip this step, names fall back to English.

In Russian, a crafted-defense stat reads like `Урон · Торт-катапульта` (stat first, building second) because `widget_module_label` reorders the two placeholders — reorder other phrases the same way if your language needs it.

Strings you leave out fall back to English, so a partial translation is fine. `./gradlew :app:testDebugUnitTest` includes `StringsConsistencyTest`, which fails if a translation uses an unknown name, changes the placeholders, or is missing from `locales_config.xml`.

Translations written with AI help should say so in a comment at the top of the file, as `values-ru/strings.xml` and `names_ru.json` do, so native speakers know to review them. The Russian game-name draft uses community-checked terms (Ратуша, Швырятель, Потайная тесла), but specialists are still welcome to fix official wording.

Your contributions are licensed under the MIT License, the same as the project's code.
