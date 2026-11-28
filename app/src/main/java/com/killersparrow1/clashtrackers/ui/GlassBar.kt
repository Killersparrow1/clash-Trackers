package com.killersparrow1.clashtrackers.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity

/**
 * The glass look for the floating tab bar: what is behind the bar (the page, scrolling under it) is blurred and tinted, with
 * a light rim. It is the person's choice (Settings > Personalization): off by default, with three strengths. Needs Android 12
 * for the blur; the setting is hidden on older phones. Written for this app; the idea of a blurred, tinted bar with a lit
 * rim is common to glass designs, no code of any other project is used.
 */
object GlassSetting {
    const val LOW = 0
    const val MEDIUM = 1
    const val HIGH = 2

    private const val PREFS = "glass_look"

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var enabled by mutableStateOf(false)
        private set
    var strength by mutableIntStateOf(MEDIUM)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean("on", false)
        strength = p.getInt("strength", MEDIUM).coerceIn(LOW, HIGH)
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
    }

    fun setStrength(context: Context, value: Int) {
        strength = value.coerceIn(LOW, HIGH)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("strength", strength).apply()
    }

    /** Blur radius in dp, and how see-through the tint is, for each strength. */
    internal fun blurDp(level: Int) = floatArrayOf(8f, 16f, 28f)[level.coerceIn(LOW, HIGH)]
    internal fun tintAlpha(level: Int) = floatArrayOf(0.62f, 0.46f, 0.30f)[level.coerceIn(LOW, HIGH)]
}

/** The page layer the bar looks through; null when the glass look is off. */
internal val LocalGlassSource = compositionLocalOf<GraphicsLayer?> { null }

/** Records what is drawn into [layer] (and still draws it), so the bar can show a blurred copy of it. */
internal fun Modifier.glassSource(layer: GraphicsLayer, active: Boolean): Modifier =
    if (!active) this else drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }

/** Fills the bar with the blurred, tinted page behind it. Apply to a box that fills the bar. */
@Composable
internal fun Modifier.glassBackdrop(source: GraphicsLayer, level: Int, blurLayer: GraphicsLayer): Modifier {
    val density = LocalDensity.current
    val radius = with(density) { GlassSetting.blurDp(level).dp.toPx() }
    val effect = remember(radius) { BlurEffect(radius, radius, TileMode.Clamp) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = GlassSetting.tintAlpha(level))
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurLayer.renderEffect = effect
                blurLayer.record { translate(-origin.x, -origin.y) { drawLayer(source) } }
                drawLayer(blurLayer)
            }
            drawRect(tint)
            // Light falling on the top edge.
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), endY = size.height * 0.6f))
        }
}

/** A thin lit rim: brighter at the top, fading to the bottom. */
internal fun glassRim(): BorderStroke =
    BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.10f))))

private val Float.dp get() = androidx.compose.ui.unit.Dp(this)
