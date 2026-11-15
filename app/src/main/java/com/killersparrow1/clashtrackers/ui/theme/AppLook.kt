package com.killersparrow1.clashtrackers.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * How the app's own screens look. [dark] is null for "follow the phone". [dynamic] looks use the wallpaper colours on
 * Android 12+. The others are fixed palettes drawn for this app, using the game's browns, golds and greens.
 */
enum class AppLook(val key: String, val dark: Boolean?, val dynamic: Boolean, val swatch: List<Long>) {
    MATERIAL_YOU("material_you", null, true, listOf(0xFF6650A4, 0xFFD0BCFF, 0xFFEFB8C8)),
    MATERIAL_LIGHT("material_light", false, true, listOf(0xFFFFFBFE, 0xFF6650A4, 0xFF7D5260)),
    MATERIAL_DARK("material_dark", true, true, listOf(0xFF141218, 0xFFD0BCFF, 0xFFEFB8C8)),
    CLEAN("clean", true, false, listOf(0xFF121417, 0xFF1C1F24, 0xFFFFC93C)),
    CLASSIC("classic", true, false, listOf(0xFF2A1D14, 0xFF3D2B1F, 0xFFFFC93C)),
    GAME("game", true, false, listOf(0xFF3D2B1F, 0xFF5C4232, 0xFF4E9A26));

    /** The fixed palette for the looks that have one; null for the Material looks. */
    fun colorScheme(): ColorScheme? = when (this) {
        CLEAN -> clean
        CLASSIC -> classic
        GAME -> game
        else -> null
    }

    companion object {
        val DEFAULT = MATERIAL_YOU
        fun fromKey(key: String?): AppLook = entries.firstOrNull { it.key == key } ?: DEFAULT

        private val clean = darkColorScheme(
            primary = Color(0xFFFFC93C), onPrimary = Color(0xFF121417),
            primaryContainer = Color(0xFF4A3A0A), onPrimaryContainer = Color(0xFFFFE08A),
            secondary = Color(0xFF7BD144), onSecondary = Color(0xFF0F2A05),
            secondaryContainer = Color(0xFF22301A), onSecondaryContainer = Color(0xFFC5F0A4),
            tertiary = Color(0xFF6CCBFF), onTertiary = Color(0xFF00344D),
            tertiaryContainer = Color(0xFF004B6E), onTertiaryContainer = Color(0xFFC3E8FF),
            background = Color(0xFF121417), onBackground = Color(0xFFF3F4F6),
            surface = Color(0xFF121417), onSurface = Color(0xFFF3F4F6),
            surfaceVariant = Color(0xFF262A31), onSurfaceVariant = Color(0xFFAEB4BE),
            surfaceDim = Color(0xFF0E1013), surfaceBright = Color(0xFF2E333B),
            surfaceContainerLowest = Color(0xFF0E1013), surfaceContainerLow = Color(0xFF181B1F),
            surfaceContainer = Color(0xFF1C1F24), surfaceContainerHigh = Color(0xFF262A31),
            surfaceContainerHighest = Color(0xFF2E333B),
            outline = Color(0xFF6B727D), outlineVariant = Color(0xFF2A2F37),
        )

        private val classic = darkColorScheme(
            primary = Color(0xFFFFC93C), onPrimary = Color(0xFF2A1D14),
            primaryContainer = Color(0xFF6E5214), onPrimaryContainer = Color(0xFFFFE08A),
            secondary = Color(0xFF8EE04A), onSecondary = Color(0xFF1E3A08),
            secondaryContainer = Color(0xFF33461F), onSecondaryContainer = Color(0xFFC8F0A0),
            tertiary = Color(0xFF6CCBFF), onTertiary = Color(0xFF00344D),
            tertiaryContainer = Color(0xFF004B6E), onTertiaryContainer = Color(0xFFC3E8FF),
            background = Color(0xFF2A1D14), onBackground = Color(0xFFF5EBDD),
            surface = Color(0xFF2A1D14), onSurface = Color(0xFFF5EBDD),
            surfaceVariant = Color(0xFF4A3426), onSurfaceVariant = Color(0xFFE6D6C3),
            surfaceDim = Color(0xFF24170F), surfaceBright = Color(0xFF5C4232),
            surfaceContainerLowest = Color(0xFF24170F), surfaceContainerLow = Color(0xFF33241A),
            surfaceContainer = Color(0xFF3D2B1F), surfaceContainerHigh = Color(0xFF4A3426),
            surfaceContainerHighest = Color(0xFF5C4232),
            outline = Color(0xFF9C826B), outlineVariant = Color(0xFF5C4232),
        )

        private val game = darkColorScheme(
            primary = Color(0xFF4E9A26), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF33461F), onPrimaryContainer = Color(0xFFC8F0A0),
            secondary = Color(0xFFFFC93C), onSecondary = Color(0xFF3D2B1F),
            secondaryContainer = Color(0xFF7A5A12), onSecondaryContainer = Color(0xFFFFE08A),
            tertiary = Color(0xFF6CCBFF), onTertiary = Color(0xFF00344D),
            tertiaryContainer = Color(0xFF004B6E), onTertiaryContainer = Color(0xFFC3E8FF),
            background = Color(0xFF3D2B1F), onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF3D2B1F), onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF4F3929), onSurfaceVariant = Color(0xFFE6D6C3),
            surfaceDim = Color(0xFF33241A), surfaceBright = Color(0xFF785842),
            surfaceContainerLowest = Color(0xFF33241A), surfaceContainerLow = Color(0xFF45311F),
            surfaceContainer = Color(0xFF5C4232), surfaceContainerHigh = Color(0xFF6A4D3A),
            surfaceContainerHighest = Color(0xFF785842),
            outline = Color(0xFFB59A82), outlineVariant = Color(0xFF24170F),
        )
    }
}

/** The chosen look, kept on the phone and observable so every screen redraws the moment it changes. */
object AppLookStore {
    private const val PREFS = "app_look"
    private val state = mutableStateOf<AppLook?>(null)

    fun get(context: Context): AppLook {
        val current by state
        current?.let { return it }
        val loaded = AppLook.fromKey(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("look", null))
        state.value = loaded
        return loaded
    }

    fun set(context: Context, look: AppLook) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("look", look.key).apply()
        state.value = look
    }
}
