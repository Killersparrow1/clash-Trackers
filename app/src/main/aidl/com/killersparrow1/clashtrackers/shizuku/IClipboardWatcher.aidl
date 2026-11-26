package com.killersparrow1.clashtrackers.shizuku;

// The Shizuku user service that watches the clipboard while Clash of Clans is open.
interface IClipboardWatcher {
    // Tells the watcher which app and receiver to hand a village export to, and the secret that receiver checks.
    void configure(String appPackage, String token) = 1;

    // True while the watcher thread is alive and can read the clipboard.
    boolean isWatching() = 2;

    // Reserved transaction code Shizuku uses to stop a user service.
    void destroy() = 16777114;
}
