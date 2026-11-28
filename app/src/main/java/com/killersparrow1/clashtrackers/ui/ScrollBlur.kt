package com.killersparrow1.clashtrackers.ui

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlin.math.abs

/*
 * The directional blur and the way its strength follows the real scroll speed are modelled on the scroll motion blur of
 * Essentials by Sameera Sandakelum (https://github.com/sameerasw/essentials, MIT License, Copyright (c) 2025). This is
 * a separate implementation; the one difference that matters is that the per-frame loop only runs while something moves.
 */

/** Optional motion blur while a list scrolls or the pages are swiped (Settings > Personalization). Off by default. */
object ScrollBlurSetting {
    const val OFF = 0
    const val LOW = 1
    const val HIGH = 2

    private const val PREFS = "scroll_blur"
    private const val KEY = "level"

    /** Needs Android 13 (a small shader). On older phones the option is hidden and nothing is drawn. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

    /** 0 off, 1 low, 2 high. Read by the lists, so they react as soon as it changes. */
    var level by mutableIntStateOf(OFF)
        private set

    fun init(context: Context) {
        level = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, OFF).coerceIn(OFF, HIGH)
    }

    fun set(context: Context, value: Int) {
        level = value.coerceIn(OFF, HIGH)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, level).apply()
    }

    internal fun scaleFor(level: Int): Float = when (level) {
        LOW -> 0.5f
        HIGH -> 1f
        else -> 0f
    }
}

/** The maths of the blur, kept apart from the screen so it can be tested. */
internal object ScrollBlurMath {
    /** Fastest speed counted, in pixels per millisecond. */
    const val MAX_SPEED = 3f
    /** Blur length for one unit of speed, in pixels, and its limit. */
    private const val PIXELS_PER_SPEED = 22f
    private const val MAX_BLUR_PX = 60f

    /**
     * The new smoothed speed after one frame. [delta] is how far the content moved since the previous frame, [dtMs] how
     * long that frame was. While [moving] the speed follows the movement; afterwards it fades out quickly.
     */
    fun nextSpeed(previous: Float, delta: Float, dtMs: Float, moving: Boolean): Float {
        if (dtMs < 1f || dtMs > 100f) return 0f
        return if (moving && abs(delta) > 0.1f) {
            val target = (delta / dtMs).coerceIn(-MAX_SPEED, MAX_SPEED)
            previous * 0.35f + target * 0.65f
        } else {
            val faded = previous * 0.45f
            if (abs(faded) < 0.01f) 0f else faded
        }
    }

    /** Signed length of the blur in pixels (the sign is the direction of travel). */
    fun blurPx(speed: Float, scale: Float): Float = (speed * PIXELS_PER_SPEED * scale).coerceIn(-MAX_BLUR_PX, MAX_BLUR_PX)
}

/** One list's blur: the smoothed scroll speed (read only when drawing) and the loop that follows the scroll position. */
class ScrollBlur internal constructor(internal val horizontal: Boolean) {
    internal var speed by mutableFloatStateOf(0f)
    /** This list's own shader (a RuntimeShader on Android 13+), so lists drawn in the same frame do not share settings. */
    internal var shader: Any? = null
}

@Composable
private fun rememberScrollBlur(
    horizontal: Boolean,
    position: () -> Float,
    moving: () -> Boolean,
): ScrollBlur {
    val blur = remember(horizontal) { ScrollBlur(horizontal) }
    val enabled = ScrollBlurSetting.supported && ScrollBlurSetting.level != ScrollBlurSetting.OFF
    LaunchedEffect(blur, enabled) {
        if (!enabled) { blur.speed = 0f; return@LaunchedEffect }
        // A frame loop runs only while the content moves or the blur is still fading out; at rest nothing is scheduled.
        @OptIn(ExperimentalCoroutinesApi::class)
        snapshotFlow { moving() }.collectLatest { isMoving ->
            var previousPosition = position()
            var previousTime = 0L
            while (isActive) {
                val finished = withFrameNanos { time ->
                    val now = position()
                    if (previousTime != 0L) {
                        val dtMs = (time - previousTime) / 1_000_000f
                        blur.speed = ScrollBlurMath.nextSpeed(blur.speed, now - previousPosition, dtMs, isMoving)
                    }
                    previousPosition = now
                    previousTime = time
                    !isMoving && blur.speed == 0f
                }
                if (finished) break
            }
        }
    }
    return blur
}

@Composable
fun rememberScrollBlur(state: LazyGridState): ScrollBlur = rememberScrollBlur(
    horizontal = false,
    position = { state.firstVisibleItemIndex * 80f + state.firstVisibleItemScrollOffset },
    moving = { state.isScrollInProgress },
)

@Composable
fun rememberScrollBlur(state: LazyListState): ScrollBlur = rememberScrollBlur(
    horizontal = false,
    position = { state.firstVisibleItemIndex * 80f + state.firstVisibleItemScrollOffset },
    moving = { state.isScrollInProgress },
)

@Composable
fun rememberScrollBlur(state: PagerState): ScrollBlur = rememberScrollBlur(
    horizontal = true,
    position = { (state.currentPage + state.currentPageOffsetFraction) * 400f },
    moving = { state.isScrollInProgress },
)

/** Adds the blur to a scrolling list or pager. Does nothing while the option is off or the phone is older than Android 13. */
fun Modifier.scrollBlur(blur: ScrollBlur): Modifier =
    if (!ScrollBlurSetting.supported || ScrollBlurSetting.level == ScrollBlurSetting.OFF) this
    else this.graphicsLayer {
        val speed = blur.speed
        renderEffect = if (abs(speed) > 0.05f) {
            BlurShader.effect(blur, size.width, size.height, ScrollBlurSetting.scaleFor(ScrollBlurSetting.level))
        } else {
            null
        }
    }

@RequiresApi(33)
private object BlurShader {
    private const val SOURCE = """
        uniform shader composable;
        uniform float2 resolution;
        uniform float scrollVelocity;
        uniform float isHorizontal;
        uniform float blurScale;

        half4 main(float2 fragCoord) {
            const int SAMPLES = 10;
            half4 color = half4(0.0);
            float totalWeight = 0.0;
            float blurMagnitude = clamp(scrollVelocity * 22.0 * blurScale, -60.0, 60.0);
            for (int i = 0; i < SAMPLES; i++) {
                float offset = (float(i) / float(SAMPLES - 1) - 0.5) * blurMagnitude;
                float2 sampleCoord;
                if (isHorizontal > 0.5) {
                    sampleCoord = float2(clamp(fragCoord.x + offset, 0.0, resolution.x), fragCoord.y);
                } else {
                    sampleCoord = float2(fragCoord.x, clamp(fragCoord.y + offset, 0.0, resolution.y));
                }
                float weight = 1.0 - abs(offset / (abs(blurMagnitude) + 0.001)) * 0.5;
                color += composable.eval(sampleCoord) * half(weight);
                totalWeight += weight;
            }
            return color / half(totalWeight);
        }
    """

    fun effect(blur: ScrollBlur, width: Float, height: Float, scale: Float): androidx.compose.ui.graphics.RenderEffect {
        val shader = (blur.shader as? RuntimeShader) ?: RuntimeShader(SOURCE).also { blur.shader = it }
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("scrollVelocity", blur.speed)
        shader.setFloatUniform("isHorizontal", if (blur.horizontal) 1f else 0f)
        shader.setFloatUniform("blurScale", scale)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "composable").asComposeRenderEffect()
    }
}
