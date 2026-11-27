# Play Protect: review notes and appeal text

Google does not say why Play Protect flags an app, so nothing here is a confirmed cause. These are the things in the
build that are known to make Play Protect cautious, what was changed, and what was left on purpose.

## Findings

| Finding | Status |
|---|---|
| Releases were the **debug build** (debuggable) signed with a key that is public in the repository (`app/debug-signing.keystore`, password `android`). Anyone can build an APK that Android treats as an update of this app. | **Changed.** `main` now also builds `assembleRelease` (not debuggable). If the secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` exist it is signed with that key, otherwise with the key earlier releases used so it still installs over them. |
| Real fix for the public key | **Needs a decision from the owner.** A private key means existing installs must be uninstalled once (Android refuses an update signed with another key). Dev builds keep the public key. |
| `REQUEST_INSTALL_PACKAGES` (in-app update) | Kept. Used only when the person taps Update; it downloads the release APK from GitHub and installs it through `PackageInstaller`. Play Protect weighs this permission, as it does for any app that installs APKs. |
| Accessibility service | Kept, optional. `typeWindowStateChanged` only, `canRetrieveWindowContent="false"`, no gestures. Reminders can now be postponed or switched off. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, exact alarms, boot receiver, notifications | Kept. Needed for finish alerts; each is user-granted. |
| Exported components | Required ones only: launcher aliases, widget providers and configure activity, the optional Shizuku receiver (accepts only a broadcast carrying a per-install secret) and the Shizuku provider (protected by `INTERACT_ACROSS_USERS_FULL`). |
| Hidden API exemption library (`hiddenapibypass`) | Used only by the optional Shizuku clipboard watcher, inside the service process Shizuku starts, not in the app process. Reflection-based libraries like this can look unusual to scanners. |
| Network | HTTPS only: `api.github.com`, `github.com`, `raw.githubusercontent.com` (updates), `api.clashk.ing`, `assets.clashk.ing`, `clashk.ing` (game data and pictures). No ads, analytics or tracking SDKs. |
| `allowBackup="true"` | Unchanged (it holds no credentials). |
| Code shrinking (R8) | Off on purpose (an older R8 build crashed on Android 10). Unobfuscated code is easier for a scanner to read. |

## Text for the appeal ("Additional information to support your appeal")

> Clash Trackers is an open-source (MIT) upgrade-timer companion for the mobile game Clash of Clans. Source code and
> every release are public at https://github.com/Killersparrow1/clash-Trackers, and each release APK is built by GitHub
> Actions from that source and scanned with VirusTotal; the scan report is attached to the release.
>
> What the app does: it reads a village export that the user copies to the clipboard themselves, shows upgrade
> countdowns, draws home-screen widgets and posts a notification when an upgrade finishes. Game data and pictures are
> downloaded over HTTPS from a public Clash of Clans data site (clashk.ing). It has no ads, no analytics, no account
> and no tracking, and it sends no personal data anywhere.
>
> Permissions and why: notifications, exact alarms and boot completed (finish alerts and widget refresh); ignore battery
> optimisations (alerts on time); install packages (the optional in-app update installs the release APK from GitHub
> when the user taps Update). The accessibility service is optional and off by default: it receives window-change
> events only (`typeWindowStateChanged`, `canRetrieveWindowContent=false`) to show a small import button while the game
> is in front; it cannot read the screen or perform gestures. An optional Shizuku mode reads the clipboard only while the
> game is open, and only forwards text that is a valid village export to the same import code.
>
> The app does not load code at runtime, contains no obfuscated code and does not hide its behaviour. We believe the
> warning is a false positive caused by the combination of the accessibility, install-packages and battery
> permissions in an app distributed outside Google Play. We are happy to provide the APK, the source and the VirusTotal
> report for review.

Before sending: replace the repository link if it moved, and only keep the VirusTotal sentence once a release has been
published with the report attached.
