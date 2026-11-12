# Changelog

All notable changes to this project are listed here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

1.0.2 is the first version published on GitHub. Earlier versions were shared privately.

## [Unreleased]

### Added

- Gold Pass reminder on the Timers tab: on Day 4 and Day 8, a Gold Pass player below 15% or 20% gets a dismissible "update your builder boost?" banner (the unlock days are not confirmed against the game).
- Settings: removed the "Changes save automatically" line.
- Settings: the "Sync from clipboard when the app opens" switch moved to Developer options (still on by default), so the Your village card is gone.
- Idle reminder (off by default): a notification when a builder or the lab has sat free for 1 to 12 hours since your last sync.
- Timers: the village card shows the total builder work and lab time left.
- App icon shortcuts: long-press the icon for Sync from clipboard and Open game.
- Support tab: Recent activity moved into Developer options (the issue report still includes it); it now has Copy and Share buttons.
- Header button now shows the installed game's icon with "Open game" (plain arrow icon if the game is not installed).
- Widget theme: new "Material pitch dark" theme: your Material You colours on a true black panel.
- Settings: removed the Player name row; rename an account from the account drop-down on the Timers tab.
- Timers tab: the Boosts & Events card is replaced by three stat tiles (builders busy, next free builder, next lab) and nothing else (the Potions & helpers card was dropped).
- Easter egg: the title shakes for as long as the sound plays.
- Settings: Gold Pass and Clock Tower options now sit in their own cards.
- Clock Tower: detected from the export (building `1000039`, `boosts.clocktower_boost` and `boosts.clocktower_cooldown`). The widget's Builder Base line gets a read-only cooldown chip (List, Grid and Minimal) that counts down with the other pills; missing, zero or invalid values show nothing. A Settings switch turns it off. Pet Potion (`pet_boost`) was already detected.
- Clock Tower ready alert: a notification when the exported cooldown ends (same sound and Do Not Disturb choice as upgrade alerts; own switch in Settings; needs finish alerts on). It fires once per export.
- Support: Diagnostics now starts with a health checklist (saved village, notifications, exact alarms, battery, icons) and Copy / Share report buttons. Report an issue copies that report and opens a GitHub issue to paste it into. The report never includes the player tag or name. "See open issues" is now a button.
- Android 17: the widget's rows are now sent inside the widget instead of being fetched by the launcher through the list service (CT1.2, which had no service, drew correct icons on Android 17). Older Android versions are unchanged; if the rows are too large to send, it falls back to the service and logs that in Recent activity.
- Themed app icon: a monochrome icon layer, so on Android 13+ the icon follows your wallpaper colours when "Themed icons" is turned on in your launcher or wallpaper settings.
- Easter egg: hold the app title for 3 seconds.
- Gold Pass: the game's export has no Gold Pass data, so you set it yourself. A popup asks once a month (Gold Pass with its Builder and Research reductions of 10, 15 or 20%, or Silver/free), and Settings has the same choice any time plus a switch to stop the monthly question. It is used for each job's progress bar, since the game's exported times already include the discount and are not reduced again.
- Hidden developer options: tap the Support tab 11 times within 4 seconds. For now it can switch the app icon between "Follow my phone" (themed icons, the default) and "Always colour", and reset the Gold Pass popup for testing. Leaving the page hides it again.
- Clock Tower chip: once the cooldown is over (or the export shows none) it stays on the widget as a gold check mark next to the Clock Tower icon instead of disappearing.
- Header button (Timers tab, top right): opens Clash of Clans' More Settings, where Export Data is. Developer options also has an "Open a game link" box for trying other links.
- Study Soup: a **Soup** button on the widget's Lab line (List, Grid and Minimal) and an "active, time left" pill, and an active soup is read from the village export (`boosts.lab_consumable`). Its saving is adjustable in Settings.
- An active Builder Bite is now read from the village export (`boosts.builder_consumable`), like the potions, instead of only from the Bite button.
- A short animated start-up screen with the app icon, drawn over the theme's own background. Tap to skip; it plays only on a fresh launch.
- The system start-up screen no longer flashes a white icon before the animation; it shows only the theme background.
- Releases are automated: a push to `main` builds the APK, then waits for the maintainer's approval before creating the next `CT` tag and GitHub Release with the APK attached. The version number comes from the newest `CT` tag (CT2.3 → CT2.4 … CT2.9 → CT3.0).
- Support tab tidy-up: Last crash, Widget diagnostics, Saved village and Widget text preview now sit together in one collapsed Diagnostics card, and the About card has aligned, grouped sections.
- Sync from the clipboard automatically when the app opens (Settings → Village, on by default). Only a fresh Clash of Clans export is used, once.
- Recent activity keeps the latest 100 entries and drops older ones by itself.
- Support → Recent activity is collapsed behind a + / − header and has a Clear activity button.
- The "Next finish" line in the Timers summary card is now a highlighted chip.
- A highlighted completion-time chip on every running timer card in the Timers tab.
- The Timers account switcher and filter chips now fade out and collapse together while you scroll down the list and come back when you scroll up (the title stays).
- Support → About has a prominent Check for updates card plus Report an issue, Request a feature and See open issues buttons.
- Custom sound for finished upgrades.
- A **Check for updates** button in Support → About that opens the latest GitHub release in the browser (the app stays offline). The app now shows its version as `CT2.2`.
- A third widget style, Minimal: black card, white dot-matrix type (Doto, SIL OFL), thin progress lines and a red accent. It scrolls like the others and keeps the helper, Goblin and potion buttons.
- A "Follow Do Not Disturb" switch in Settings → Notifications (on by default): finish alerts stay quiet during Do Not Disturb and use the notification volume. Turn it off for the old alarm-style alert that rings through Do Not Disturb.
- The Timers account dropdown shows each account's Town Hall level next to its name, with a round picture of the Town Hall at that level.
- The small widget size now scrolls through every timer instead of showing 3 and "+N more".
- The List and Grid widgets now scroll, so every timer fits however small you make them.
- A second widget style, Grid: round progress rings with the building icon in the middle, grouped by Builders, Lab, Pets and Builder Base, with the helper button and the Goblin badge. Choose it when you add or reconfigure a widget; List stays the default.
- Language picker in Settings and a first Russian translation. More languages can be added with one XML file; see CONTRIBUTING.md.
- Small home-screen widget layouts with the soonest running upgrades.
- Widget themes: Material You, Clash, Dark and Light. Choose a default in Settings or override it per widget.
- Tablet and foldable layouts with a navigation rail and a two-column timers grid.
- In-app Help guide with step-by-step sync and widget setup instructions.
- Russian translations for the new widget theme, Help and small-widget texts.

### Fixed
- Widget progress bars now follow the chosen theme: Clash keeps the original green, Dark and Light use their own fixed colours (and a fresh neutral look), and only Material You follows the phone's accent. Fixes unreadable dark-on-dark rows in Light.

- When several upgrades finish together, only the first notification makes a sound.
- An active research potion is now read from the village export (`lab_boost`), like the builder potion. The pet potion is read too if the game names its field with "pet" and "boost".
- Small widget row count now follows the actual widget height.
- Widget setup screen no longer hides its title under the status bar, and the theme choices are listed vertically.
- The notification icon is now the new stopwatch image.
- The small widget's soonest-timers list only includes running upgrades, not completed rows.
- The widget "+X more" label now follows the selected language.

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
