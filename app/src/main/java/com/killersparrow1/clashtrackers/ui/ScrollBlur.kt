package com.killersparrow1.clashtrackers.ui

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Optional motion blur while a list scrolls or the pages are swiped (Settings > Personalization). Off by default. It softens the list in the direction it moves,
 * more the faster it moves, and fades out as soon as it stops. Needs Android 13 (a small shader); on older phones the
 * option is hidden and nothing is drawn.
 */
object ScrollBlurSetting {
    const val OFF = 0
    const val LOW = 1
    const val HIGH = 2

    private const val PREFS = "scroll_blur"
    private const val KEY = "level"

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

    /** Blur radius in pixels for a scroll step of [stepPx] pixels (one frame). */
    internal fun radiusFor(stepPx: Float, level: Int): Float = when (level) {
        LOW -> (stepPx * 0.08f).coerceAtMost(8f)
        HIGH -> (stepPx * 0.14f).coerceAtMost(16f)
        else -> 0f
    }
}

/** One list's blur state: the current radius (read only when drawing) and the scroll listener that drives it. */
class ScrollBlur internal constructor(scope: CoroutineScope, internal val horizontal: Boolean = false) {
    internal val radius = Animatable(0f)
    /** This list's own shader (a RuntimeShader on Android 13+), so lists drawn in the same frame do not share settings. */
    internal var shader: Any? = null
    private var job: Job? = null

    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // A list listens to up and down movement only, the page swipe to sideways movement only, so a list inside the
            // pager is not blurred twice.
            val step = if (horizontal) abs(available.x) else abs(available.y)
            if (step == 0f) return Offset.Zero
            val target = ScrollBlurSetting.radiusFor(step, ScrollBlurSetting.level)
            job?.cancel()
            job = scope.launch {
                // Follow the speed, then relax to zero when no new scroll step arrives.
                radius.animateTo(target, tween(60))
                delay(40)
                radius.animateTo(0f, tween(220))
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberScrollBlur(horizontal: Boolean = false): ScrollBlur {
    val scope = rememberCoroutineScope()
    return remember { ScrollBlur(scope, horizontal) }
}

/** Adds the blur to a scrolling list. Does nothing while the option is off or the phone is older than Android 13. */
fun Modifier.scrollBlur(blur: ScrollBlur): Modifier =
    if (!ScrollBlurSetting.supported || ScrollBlurSetting.level == ScrollBlurSetting.OFF) this
    else this.nestedScroll(blur.connection).graphicsLayer {
        val r = blur.radius.value
        renderEffect = if (r > 0.5f) BlurShader.effect(blur, r) else null
    }

@RequiresApi(33)
private object BlurShader {
    private const val SOURCE = """
        uniform shader content;
        uniform float radius;
        uniform float2 dir;
        half4 main(float2 coord) {
            half4 sum = half4(0.0);
            float total = 0.0;
            for (int i = -4; i <= 4; i++) {
                float w = 1.0 - abs(float(i)) / 5.0;
                sum += content.eval(coord + dir * (float(i) * radius / 4.0)) * half(w);
                total += w;
            }
            return sum / half(total);
        }
    """

    fun effect(blur: ScrollBlur, radius: Float): androidx.compose.ui.graphics.RenderEffect {
        val shader = (blur.shader as? RuntimeShader) ?: RuntimeShader(SOURCE).also { blur.shader = it }
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("dir", if (blur.horizontal) 1f else 0f, if (blur.horizontal) 0f else 1f)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
