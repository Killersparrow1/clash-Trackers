# Changelog

All notable changes to this project are listed here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

1.0.2 is the first version published on GitHub. Earlier versions were shared privately.

## [Unreleased]

### Added

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
