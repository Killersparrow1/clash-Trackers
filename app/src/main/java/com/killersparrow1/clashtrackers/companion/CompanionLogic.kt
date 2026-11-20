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
     * @param className class name the window reported: a real app screen reports its activity, while floating panels,
     *   bubbles and dialogs (game boosters, side panels, screen recorders) report a framework view such as
     *   android.widget.FrameLayout. Those do not mean the game was left.
     * @return the new "game active" state
     */
    fun next(packageName: String?, current: Boolean, own: String, keyboards: Set<String>, className: String? = null): Boolean {
        val pkg = packageName ?: return current
        if (pkg == GAME_PACKAGE) return true
        if (pkg == own || pkg in NEUTRAL || pkg in keyboards) return current
        if (className != null && className.startsWith("android.")) return current
        return false
    }
}
