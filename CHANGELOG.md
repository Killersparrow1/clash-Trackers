# Changelog

All notable changes to this project are listed here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

1.0.2 is the first version published on GitHub. Earlier versions were shared privately.

## [Unreleased]

### Added

- Clash Companion: a draggable golden import button and a Paste JSON notification while Clash of Clans is open (accessibility service, window events only), with the permission popup now also asking for the accessibility service and Install unknown apps.
- Event alerts for Clan Games, Raid Weekend, CWL, season end and league reset, with per-event switches, a lead time and a custom sound.
- Timers calendar view (pinch or +/− zoom, hour labels, up to 12 suggested upgrades per slot) and Builder Base in Bedtime level up and the calendar.
- Dev builds use their own package (`.dev`, "Clash Trackers Dev") so they install next to the released app.

### Changed

- The Plan tab is now **Progress**.

### Changed

- Navigation motion: tabs now move on a spring and the page coming in scales from 0.96 to 1.0 as it settles; the page you are on plus one each side stay loaded, so swiping or jumping several tabs never shows a blank page. The header title (and the Open game button) slide and fade the same way the tabs moved, switching when the page passes the middle of a swipe.
- The bottom bar is now a floating pill (inset from the edges, fully rounded, solid colour, hairline border, soft shadow, no blur). The highlight slides between tabs on a spring. It slides down out of view when you scroll down a list and comes back on any upward scroll, when you reach the top, when scrolling stops for a moment, and whenever you swipe or tap a tab. Lists keep extra room at the bottom so nothing hides behind it. Wide screens keep the side rail.
- With the phone's "Remove animations" setting on: no scale, the header switches without sliding, the bar stays in place and the highlight moves without a spring.
- Plan > Road to max is now a set of cards: an overall progress summary, Time left (builders, laboratory, pet house), Resources left (Gold, Elixir, Dark Elixir, and Gold or Elixir for walls), and a card each for Buildings, Walls, Heroes, Lab and Pets with how much is done, the time left (or "No timer") and the cost left. The share done is the levels you have against the levels at your Town Hall maximum (a building or troop you do not have counts as level 0), and the overall figure is the average of the groups. The "Include walls" switch is gone because walls have their own card. Equipment and helpers are not included yet.
- Road to max builder time: work is still shared evenly between your builders, but never drops below the time the hero with the most work left needs (a hero is upgraded one level after another). The widget uses the same figure.
- New versions: the app now also checks GitHub for a new release in the background (about every 12 hours, only when online) and shows one notification per version, even when the app is closed. It follows the same switch as the check on open in Settings > Support > About.
- A short message when the app opens without internet, asking you to connect to get the latest game data and event dates.
- Settings > Game data now takes the game data straight from ClashKing: it downloads `static_data.json` (assets.clashk.ing, with the GitHub copy as a backup), asks first whether it changed (`Last-Modified`), converts it on the phone, checks it, and only then replaces the saved data (written to a temporary file and moved into place). Any failure leaves the data in use untouched. The project's own `data/max_data.json` on GitHub is no longer used.
- Clash > Events: the raid weekend and the season end come from the ClashKing API (`/v2/dates/current`, `/v2/dates/season-start-end`) when it is reachable and marked "ClashKing". The dates are saved on the phone, shown at once when the tab opens, work offline, refresh in the background (every 6 hours at most, on app open) and with the refresh button, and the last sync time is shown. Everything else, and anything the API cannot answer, still uses the existing schedule; times set in the schedule file win over the API. ClashKing says the season ends at 05:00 UTC; the old schedule assumed 08:00 UTC.
- Credits: ClashKing attribution with links to ClashKing, ClashKingAssets and the API documentation; the old package is credited only for the icons that are still built into the app.
- Everything now comes from ClashKing instead of the clash-of-clans-data package: names (English and Russian, the game's official wording; the Russian names are the game's own text now), upgrade times for the progress bars, costs and levels, and the list of where each picture is (`icon_paths.json`). The build no longer fetches the old package; the icons built into the app stay as placeholders. New tools: `gen_names_ck.cjs`, `gen_upgrade_times_ck.cjs`, `gen_icon_paths_ck.cjs`; the old generators are removed.
- The game data built into the app is now ClashKing's (Town Hall 19, 144 items), so new items such as the Squad Barracks have a name and get their picture instead of showing as "Unknown".
- Pictures now come from ClashKing's asset site (assets.clashk.ing): when the app opens it downloads each item's picture (all of them the first time, then again about once a week) and uses it ahead of the icon built into the app, which stays only as a placeholder until the download finishes. New buildings, troops and spells in newer game data get their name and picture by themselves.
- Cards give a very subtle giggle and a crisp haptic tick when you tap them (timers, village, sync, events, section cards). Scrolling and taps on buttons inside a card do not trigger it, and it follows the system touch feedback setting.
- A weekly GitHub Action checks ClashKing's game data (ClashKingAssets) for newer costs, times and Town Hall levels, and opens a pull request to main only when the numbers changed. `tools/gen_max_data_ck.cjs` converts it; the first pull request will bring Town Hall 19. ClashKing is credited under Support > Credits.
- Settings > Support: removed the "Works offline" card (the app now uses the internet for updates, events and game data). Credits are hidden behind a + / − header.

### Fixed

- Road to max widget: opening its setup no longer crashes (`ClassNotFoundException`); the setup screen had been cut out of the code by an earlier edit and is back.
- App look: Material light is now light even when the phone is in dark mode (the window background, splash and system bars follow the chosen look too). Material You still follows the phone.
- Widget: the Sync button is back to starting the quiet Sync screen (no app window). The stray launches were Back Tap set to the app's Sync shortcut, which is also allowed through the launch guard now.
- Personalization > Widget theme now changes every widget. A widget that had its own theme from its settings kept it and ignored the app-wide choice; picking a theme in the app now replaces those.
- With "Sync from clipboard when the app opens" switched off, the app no longer reads the clipboard at all (it used to read it and only skip the import, which still triggered Android's "pasted from clipboard" notice). The widget Sync button still syncs, since you asked for it.
- Road to max is always open on its own Plan sub-tab (no more + / − toggle).
- Widget: the Sync button now opens the app, which reads the copied village data when it opens. The invisible Sync screen is no longer started from the widget, because some ROMs sent it stray taps.
- Widget: only the Sync button starts a sync now (the title row no longer does), and a repeated Sync screen launch within 4 seconds is ignored, so stray launches stop covering other apps with "Sync failed".

### Added

- Clash > Events now refreshes its schedule quietly in the background every time the app opens (switchable) from `data/events.json` in the project's repository. The file can change the usual event times and add one-off events; the last good copy is kept for offline use.
- New **Clash** tab (4 tabs in total) with **Events**: countdowns to Clan Games, Raid Weekend, CWL sign-up and war days, the season end and the league reset, worked out offline from the usual schedule.
- Game data (levels, costs and times behind Road to max and Bedtime) now updates by itself: when the app opens (at most hourly, switchable in Settings) it downloads the project's `max_data.json`, checks it, and uses it when it is different and not for fewer Town Halls. An app update replaces downloaded data with its own. A card in Settings shows what is in use and has a manual check.
- App look in Settings > Personalization: Material You (follows the phone), Material light, Material dark, Clean, Classic and Game. The status and navigation bar icons follow the chosen look.
- Settings now has three sub-tabs: **Settings**, **Personalization** (the widget look and live countdown, with more looks planned) and **Support** (moved from its own tab). The hidden developer options now open by tapping the Settings tab 11 times quickly.
- Swipe sideways to move between tabs (Timers, Plan with its two sub-tabs, Settings, Support), and the page follows your finger as you swipe.
- Plan tab has two sub-tabs: **Road to max** and **Bedtime level up**. Bedtime can be filtered by Defence, Permanent (traps, guardians), Army (army buildings, troops, spells, siege, heroes, pets), Resource and New (new buildings), by Gold, Elixir or Dark Elixir, and sorted by soonest done, highest or lowest cost.
- In-app update: a dialog offers new releases when the app opens (every 12 hours at most, can be switched off in About); Support > About checks the latest GitHub release, downloads the APK and installs it through Android's installer. Adds the INTERNET and REQUEST_INSTALL_PACKAGES permissions, used only for this.
- Road to max: the Builders time is now per builder count (work divided by your builders, in the card and the widget); the total work is shown under it.
- New **Plan** tab (second tab). It holds Road to max (moved out of Timers) and **Bedtime**: pick when you sleep and see what to start in each free builder, lab or pet slot so it runs through the night and ends while you are awake. Estimates from base times.
- Timers: the account button opens an Accounts sheet (Town Hall picture, name, tag, tick on the current one, Rename and Delete, how to add another).
- Town Hall pictures: kept in their own folder (assets/townhall) at 384 px so the CI icon step no longer shrinks them to 64 px.
- Widget list rows are now embedded on Android 16 too (they were wrong icons from the list service on some phones).
- Town Hall pictures are now 384 px for a sharp card.
- Header: the Open game button is just the game icon now (no pill or text).
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
- Widget themes: Material You, Classic, Dark, Light and **Material dark** (Material You colours on a true black panel). Pick a default in Settings or override it per widget.

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
