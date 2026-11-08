<p align="center">
  <img src="docs/screenshots/clash%20trackers%20icon.png" alt="Clash Trackers app icon" width="140">
</p>

<h1 align="center">Clash Trackers</h1>

<div align="center">

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Material 3 Expressive](https://img.shields.io/badge/Material_3-Expressive-6750A4?logo=googlematerial&logoColor=white)
![Fully Offline](https://img.shields.io/badge/Privacy-100%25%20Offline-00C853)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

A modern, offline Android application and home-screen widget for **Clash of Clans** that tracks your Home Village and Builder Base upgrade countdowns, potions, and special events in real time.

Redesigned with **Material 3 Expressive**, authentic Clash typography, an advanced boost calculation engine, and full Builder Base support.

> **Unofficial Fan Project**: This application is not affiliated with, endorsed, sponsored, or approved by Supercell. See the [Disclaimer](#disclaimer).

---

## Screenshots

<p align="center">
  <img src="docs/screenshots/potion-active.png" alt="Timers screen in the app, with building and hero icons on every card" width="270">
  &nbsp;&nbsp;
  <img src="docs/screenshots/hero.png" alt="Home-screen widget with live countdowns, potions and Builder Base timers" width="340">
</p>
<p align="center"><em>Left: the Timers screen. Right: the home-screen widget.</em></p>

---

## Highlights

### 🎨 Material 3 Expressive Interface
- **Timers Dashboard**: Unified view of all ongoing upgrades with expressive cards, category chips, and animated `LinearWavyProgressIndicator` bars.
- **Authentic Typography**: Built with the bundled **Lilita One** Clash font for headings, numbers, and badges, paired with clean, legible tabular system typography for body text.
- **Short Navigation Bar**: Fluid navigation between **Timers**, **Settings**, and **Support**.
- **Dynamic Theming**: Full support for Material You dynamic color extraction (Android 12+) with expressive light and dark palettes.

### 🏛️ Complete Village Coverage
- **Home Village**: Tracks regular Builders, the event Goblin Builder, the Laboratory, and the Pet House.
- **Builder Base**: Full support for Night Village builders and Star Laboratory troop research.
- **Level-Specific Art**: Displays visual building and trap upgrades matching the target upgrade level (`<id>_<level>.webp`).

### ⚡ Advanced Boost & Event Engine
- **Builder Base Clock Tower**: Tracks the 10× speed boost with durations calibrated to verified Clock Tower levels (Level 1: 14 min to Level 10: 32 min), including estimated time saved.
- **Clock Tower Potion**: 30-minute 10× speed boost for Builder Base construction and research.
- **Home Village Builder Potion & Snacks**: 10× Builder Potions, 2× Builder Bites, Research Potions, and Pet Potions with consecutive stacking rules.
- **Gold Pass Builder Boost**: Configurable 10%, 15%, or 20% time reduction perks.
- **Special Upgrade Events**:
  - **Hammer Jam**: Configurable 50% upgrade time and cost reduction for eligible Home Village construction.
  - **Builder Base Bonanza**: Independent event configuration with customizable time reductions for the Builder Base.
- **Time-Segment Simulation**: Uses a deterministic interval simulation algorithm that accurately factors in boost start, expiration, and overlapping intervals without floating-point drift.
- **State Persistence**: Active boosts, Clock Tower levels, and event schedules persist across device reboots and app restarts via Android DataStore.

### 📱 Live Home Screen Widget
- **Per-Second Countdowns**: Live ticking chronometers directly on your home screen.
- **Small and Large Layouts**: Use a compact widget with the soonest running upgrades or the full widget with every timer and progress bar.
- **Widget Themes**: Pick **Material You**, **Clash**, **Dark** or **Light** as the default, and override the theme for individual widgets.
- **Helper Integration**: Tap `+` to assign the Builder's Apprentice or Lab Assistant and see realistic predicted finish times.
- **Exact Alarms**: Accurate re-renders at completion edges with no background battery drain.
- **Per-Widget Account Binding**: Place the widget as many times as you like. Each time, a picker asks which account that widget should show, so you can keep one widget per account side by side. You can change a widget's account and theme later by reconfiguring it.

### 📐 Adaptive Layout
- **Phones and Tablets**: The app keeps the bottom navigation on phones and switches to a navigation rail on wider screens.
- **Two-Column Timers**: Wide screens show the Timers dashboard in two columns, centered at a comfortable reading width.

### ❓ Built-in Help
- **Step-by-Step Guide**: Open **Support → How to sync** or the link on the empty Timers screen for screenshots and instructions covering export, sync and widget setup.

### 👥 Multiple Accounts
- **Saved by Player Tag**: Every Clash of Clans export contains a player tag. Sync an export from another account and the app saves it as its own account; syncing the same account again updates only that account.
- **Account Switcher**: Switch between your accounts from the Timers screen. Timers, boosts, and events follow the account you pick.
- **Rename & Delete**: Give each account a name and remove accounts you no longer need (deleting asks for confirmation).
- **Per-Widget Accounts**: Different widgets can show different accounts at the same time, each with its own live countdowns.

### 🌍 Languages
- **In-app Language Picker**: Choose a language in **Settings → Language** (or use Android's *App languages* screen on Android 13+). The home-screen widget follows your choice.
- **Available now**: English and Russian (first Russian draft, native-speaker review welcome).
- **Add your language**: Copy one XML file and add one line, no coding needed. See [Adding a language](CONTRIBUTING.md#adding-a-language).
- **Still English**: Upgrade and building names, countdown units, the widget footer and some Support diagnostics are not translated yet.

### 🔒 100% Offline Privacy
- **Zero Internet Permission**: The app does not request `android.permission.INTERNET`. It cannot send or receive data over the network.
- **No Accounts & No Ads**: Completely private; your village data never leaves your device.
- **On-Demand Clipboard Sync**: The clipboard is read exclusively when you tap **Sync**.

---

## Quick Setup

1. **Get the APK**: Download the latest build or compile via GitHub Actions.
2. **Open Clash of Clans**: Go to **Settings → Data Export → Copy**.
3. **Open Clash Trackers**: Tap **Sync from clipboard**.
4. **Add the Widget**: Long-press your home screen, choose **Widgets → Clash Trackers**, and place it.
5. **(Optional) Add more accounts**: Switch to your other account in Clash of Clans, copy its export the same way, and tap **Sync from clipboard** again. Each player tag becomes its own account. When you place a widget, choose which account it should show.
6. **(Optional) Notifications**: Enable finish alerts in **Settings → Notifications** to receive reminders when a builder or lab is free.

---

## Architecture & Project Structure

```
clash-trackers/
├── app/
│   ├── src/main/java/com/killersparrow1/clashtrackers/
│   │   ├── core/           # Pure Kotlin domain models, parser, & simulation engines
│   │   │   ├── BoostEngine.kt       # BoostModifier, ActiveBoostState, & potion stacking
│   │   │   ├── ClockTowerData.kt    # Verified Clock Tower level durations & formulas
│   │   │   ├── EventEngine.kt       # Hammer Jam, BB Bonanza, & custom event mechanics
│   │   │   ├── ExportParser.kt      # Clash of Clans clipboard data parser
│   │   │   ├── Model.kt             # Village export, Job, Helper, & Category models
│   │   │   ├── TimerEngine.kt       # Multi-interval time-segment simulation engine
│   │   │   └── WidgetModel.kt       # Widget state preparation & formatting
│   │   ├── data/           # DataStore persistence (StateRepository, Names, Times)
│   │   ├── ui/             # Jetpack Compose Material 3 Expressive UI
│   │   │   ├── ClashTrackersContent.kt # Adaptive scaffold with bottom bar or navigation rail
│   │   │   ├── TimerDashboard.kt    # Upgrade cards, filters, & sync controls
│   │   │   ├── BoostEventSection.kt # Interactive Clock Tower & potion controls
│   │   │   ├── HelpDialog.kt        # Paged setup and sync guide
│   │   │   ├── SettingsSection.kt   # Grouped settings & dialog editors
│   │   │   ├── SupportScreen.kt     # Diagnostics, crash reports, & widget preview
│   │   │   └── theme/               # MaterialExpressiveTheme & Lilita One Typography
│   │   └── widget/         # Android RemoteViews widget provider, renderers, & WidgetConfigActivity (account picker)
│   └── src/test/java/com/killersparrow1/clashtrackers/  # Comprehensive unit test suite (30+ test cases)
└── tools/                  # Asset & icon generators from clash-of-clans-data
```

---

## Building from Source

### Cloud Build (GitHub Actions)
This repository includes an automated CI/CD workflow (`.github/workflows/build-apk.yml`). You can build debug APKs entirely in the cloud without using local memory:
1. Push your changes to your fork.
2. Open the **Actions** tab on GitHub.
3. Select **Build APK** and click **Run workflow**.
4. Download the compiled `ClashTrackers-debug-apk` from the workflow summary.

### Local Build
Requirements:
- Android SDK with Platform 37 and Build-Tools 36.
- JDK 21+ (JDK 25 recommended).

```sh
# Clone your fork
git clone https://github.com/Killersparrow1/clash-trackers.git
cd clash-trackers

# Run the unit test suite
./gradlew :app:testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

---

## Privacy & Permissions

| Permission | Purpose |
|---|---|
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Schedules exact alarms to wake and re-render the widget at upgrade completion times. |
| `POST_NOTIFICATIONS` | Alerts you the exact second an upgrade finishes so you can start the next one. |
| `RECEIVE_BOOT_COMPLETED` | Reschedules active timer alarms after device reboot. |
| `INTERNET` | **NOT REQUESTED**. The app cannot make network calls. |

---

## Acknowledgements & Credits

- **Original Project**: Built upon [Kuswaa/clash-timers](https://github.com/Kuswaa/clash-timers).
- **Material 3 UI Inspiration**: Adapted from [Hy4ri/clash-timers](https://github.com/Hy4ri/clash-timers/tree/feat/material-app-ui).
- **Font**: [Lilita One](app/src/main/res/font/lilita_one.ttf) by Juan Montoreano (SIL Open Font License 1.1).
- **Game Data & Art**: Extracted from [clash-of-clans-data](https://github.com/chiefpansancolt/clash-of-clans-data).
- **Finished Upgrade Sound**: Custom built-in sound (`finish_sound.ogg`). TODO: add sound source and licence.

---

## License

This project is licensed under the [MIT License](LICENSE). Game assets, names, and logos are property of Supercell.

## Disclaimer

This material is unofficial and is not endorsed by Supercell. For more information see Supercell's Fan Content Policy: [www.supercell.com/fan-content-policy](https://www.supercell.com/fan-content-policy).
