package com.killersparrow1.clashtrackers.companion

/** Pure decision logic for the Clash Companion: which window event means "the game is in front". */
object CompanionLogic {
    const val GAME_PACKAGE = "com.supercell.clashofclans"

    /** Packages that appear on top of the game without meaning the player left it (our own paste screen, system UI, keyboards). */
    private val NEUTRAL = setOf("android", "com.android.systemui")

    /**
     * @param packageName package of the window that just came to the front
     * @param current whether the game is currently considered active
     * @param own this app's package
     * @param keyboards installed keyboard packages
     * @return the new "game active" state
     */
    fun next(packageName: String?, current: Boolean, own: String, keyboards: Set<String>): Boolean {
        val pkg = packageName ?: return current
        if (pkg == GAME_PACKAGE) return true
        if (pkg == own || pkg in NEUTRAL || pkg in keyboards) return current
        return false
    }
}
