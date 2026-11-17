package com.killersparrow1.clashtrackers.core

/** Raw `data` IDs from the village export that the app logic depends on. */
object GameIds {
    const val TOWN_HALL = 1000001
    const val BUILDERS_HUT = 1000015
    const val BOBS_HUT = 1000064
    /** Builder Base Clock Tower (`buildings2`). */
    const val CLOCK_TOWER = 1000039

    // Helper IDs, as listed in ClashKing's game data (assets.clashk.ing static_data.json).
    const val HELPER_BUILDERS_APPRENTICE = 93000000
    const val HELPER_LAB_ASSISTANT = 93000001
    const val HELPER_ALCHEMIST = 93000002
    const val HELPER_PROSPECTOR = 93000003
}
