# Changelog

All notable changes to this project are listed here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

1.0.2 is the first version published on GitHub. Earlier versions were shared privately.

## [Unreleased]

### Added

- Custom sound for finished upgrades.
- The small widget size now scrolls through every timer instead of showing 3 and "+N more".
- The List and Tiles widgets now scroll, so every timer fits however small you make them.
- A second widget style, Tiles: round progress rings with the building icon in the middle, grouped by Builders, Lab, Pets and Builder Base, with the helper button and the Goblin badge. Choose it when you add or reconfigure a widget; List stays the default.
- Language picker in Settings and a first Russian translation. More languages can be added with one XML file; see CONTRIBUTING.md.
- Small home-screen widget layouts with the soonest running upgrades.
- Widget themes: Material You, Clash, Dark and Light. Choose a default in Settings or override it per widget.
- Tablet and foldable layouts with a navigation rail and a two-column timers grid.
- In-app Help guide with step-by-step sync and widget setup instructions.
- Russian translations for the new widget theme, Help and small-widget texts.

### Fixed

- Small widget row count now follows the actual widget height.
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
