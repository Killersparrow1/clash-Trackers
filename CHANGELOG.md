# Changelog

All notable changes to this project are listed here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

1.0.2 is the first version published on GitHub. Earlier versions were shared privately.

## [Unreleased]

### Added

- Road to max: the Builders time is now per builder count (work divided by your builders, in the card and the widget); the total work is shown under it.
- Timers: the village card and the sync card are one card now, with the Town Hall picture (one per level up to TH18) and the sync button under the next finish.
- Support diagnostics and the copied report now count the Road to max widget and list its size.
- Road to max widget: pick the account in its setup screen and show the account name in its title.
- Widget picker: both widgets show a preview with sample data (Road to max 4x2, timers 4x4) and a title ("Upgrade timers", "Road to max") with a description.
- README: "Get it on Obtainium" button above the screenshots that opens the add-app screen.

**Widget**
- Clock Tower cooldown chip on the Builder Base line (List, Grid and Minimal). It counts down with the other pills, is gold while the boost runs, and stays as a gold check mark once the tower is ready. Read from the export; a Settings switch turns it off.
- Clock Tower ready alert: a notification with its own sound when the cooldown ends (once per export; own switch; needs finish alerts on).
- Study Soup: a **Soup** button and an active "time left" pill on the Lab line, read from the export. Its saving is adjustable in Settings.
- Active Builder Bite, research potion and pet potion are now read from the village export, like the builder potion.
- Widget styles Grid (progress rings) and Minimal (black, dot-matrix type), plus small layouts. Every style scrolls, so every timer fits at any size.
- Widget themes: Material You, Clash, Dark, Light and **Material pitch dark** (Material You colours on a true black panel). Pick a default in Settings or override it per widget.

**App**
- **Road to max widget**: a second, resizable home-screen widget with the builder, lab and pet time (and, when wide, Gold, Elixir and Dark Elixir) to max your Town Hall. It uses the same five themes as the timer widget, follows the account selected in the app and refreshes on every sync.
- **Road to max** card on the Timers tab (collapsed): the builder, lab and pet time and the Gold, Elixir and Dark Elixir still needed to max your current Town Hall, with an optional walls total, each line with its building or resource icon. It is an estimate from the village export and bundled game data (base times and costs; no potions, helpers, events or Gold Pass). Regenerate the data with `tools/gen_max_data.cjs`.
- Three stat tiles under the village card: builders busy, next free builder and next lab. The village card also shows the total builder work left, and no longer repeats the tiles' builder and lab numbers.
- A highlighted "Next finish" chip, and a completion-time chip on every running timer card.
- Gold Pass: the export has no Gold Pass data, so you set it yourself in a popup that asks once a month (Gold with 10, 15 or 20% Builder and Research reductions, or Silver) and in Settings. It is used for each job's progress bar only, since exported times already include the discount. On Day 4 and Day 8 a dismissible banner asks whether your builder boost has gone up (the unlock days are not confirmed against the game).
- **Open game** button in the header (shows the installed game's icon) that opens Clash of Clans' More Settings, where Export Data is.
- App icon shortcuts: long-press the icon for Sync from clipboard and Open game.
- Idle reminder (off by default): a notification when a builder or the lab has sat free for 1 to 12 hours since your last sync.
- Themed app icon: on Android 13+ it follows your wallpaper colours when *Themed icons* is on.
- Sync from the clipboard when the app opens. Only a fresh Clash of Clans export is used, once.
- Notifications: custom finish sound, and a "Follow Do Not Disturb" switch (on by default).
- Support: Diagnostics starts with a health checklist and has Copy / Share report buttons. *Report an issue* copies the report (no player tag or name) and opens a GitHub issue. Also a **Check for updates** button (opens the latest release in the browser; the app stays offline) and *Request a feature* / *See open issues* buttons.
- README: how to get automatic updates with Obtainium.
- Tablet and foldable layouts, an in-app Help guide, a language picker with a first Russian translation, account Town Hall pictures in the switcher, and a short start-up animation.

### Changed

- The Boosts & Events card on the Timers tab is gone; potions, Hammer Jam and Bonanza come from the export. Account switcher and filter chips collapse while you scroll.
- Settings are grouped into cards (Gold Pass, Clock Tower, Notifications, Language). The Player name row was removed; rename an account from the account drop-down on the Timers tab.
- Support: Last crash, Widget diagnostics, Saved village and Widget text preview now sit in one collapsed Diagnostics card.
- Recent activity keeps the latest 100 entries and moved out of the Support tab.

### Fixed

- Android 17: the widget's rows are now sent inside the widget, which fixes wrong icons there (older Android is unchanged; it falls back to the old route and logs it if the rows are too large).
- Widget progress bars follow the chosen theme (fixes unreadable dark-on-dark rows in Light).
- When several upgrades finish together, only the first notification makes a sound.
- The small widget's row count follows its height and lists only running upgrades; its "+X more" label follows the language.
- The widget setup screen no longer hides its title under the status bar.
- The start-up screen no longer flashes a white icon before the animation.
- The notification icon is the new stopwatch image.

## [1.0.2] - 2026-11-03

### Fixed

- Fixed a crash at start-up on Android 10 that affected 1.0 and 1.0.1. It came from code optimization in the release build.

### Changed

- Release builds no longer use code shrinking or optimization, so the app runs reliably across phones and Android versions. The APK is larger as a result, about 9 MB instead of about 2 MB.

## 1.0.1 - 2026-11-03

### Added

- Crash reporting that stays on the phone: a crash screen, and a **Last crash** section in the app with Copy, Show and Clear.
- A notification that opens the crash screen when the app crashes in the background.

## 1.0 - 2026-11-03

First release.

### Added

- Home screen widget with builder, laboratory and pet house timers, live countdowns and progress bars.
- Sync from the game's village data export through the clipboard.
- Finish times that include Builder's Apprentice and Lab Assistant sessions, potions and Builder Bites.
- Support for the event Goblin Builder.
- Notifications when a builder, the lab or the pet house is free.
- Settings for boost strength, helper savings, helper interval and builder count.

[1.0.2]: https://github.com/Kuswaa/clash-timers/releases/tag/v1.0.2
