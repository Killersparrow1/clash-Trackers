package com.killersparrow1.clashtrackers.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** True when the phone's "remove animations" setting is on (animator duration scale 0); follows changes while the app runs. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    fun read(): Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    var reduced by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        runCatching {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduced
}

/** Room the floating tab bar takes at the bottom of a page; 0 where there is no floating bar (wide screens). */
internal val LocalBarInset = compositionLocalOf { 0.dp }

/** Padding for a page's scrolling list: 16 dp around, plus room at the bottom so the floating bar never hides content. */
@Composable
internal fun listPadding(): PaddingValues = PaddingValues(16.dp, 16.dp, 16.dp, 16.dp + LocalBarInset.current)

/**
 * A subtle scale while a page is moving: 0.96 when a page is a whole page away, 1.0 when it is in place, so the page
 * coming in grows into place as the one going out settles back. Transform only; read in the draw phase.
 */
internal fun Modifier.pageScale(pager: PagerState, page: Int, enabled: Boolean): Modifier =
    if (!enabled) this else graphicsLayer {
        val distance = abs((page - pager.currentPage) - pager.currentPageOffsetFraction).coerceIn(0f, 1f)
        val scale = 0.96f + 0.04f * (1f - distance)
        scaleX = scale
        scaleY = scale
    }

/**
 * Shows [content] for [index] and, when [index] changes, slides the new one in from the side the tabs moved towards while
 * the old one slides out the other way, both fading, in one short spring. Only translation and opacity change: the
 * layout is not touched while it runs. With [reduced] the content switches at once.
 */
@Composable
internal fun SlideSwitcher(
    index: Int,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    distance: Dp = 40.dp,
    content: @Composable (Int) -> Unit,
) {
    var shown by remember { mutableIntStateOf(index) }
    var leaving by remember { mutableStateOf<Int?>(null) }
    var direction by remember { mutableIntStateOf(1) }
    val progress = remember { Animatable(1f) }
    val distancePx = with(LocalDensity.current) { distance.toPx() }

    LaunchedEffect(index, reduced) {
        if (index == shown) return@LaunchedEffect
        if (reduced) {
            leaving = null
            shown = index
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        direction = if (index > shown) 1 else -1
        leaving = shown
        shown = index
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
        leaving = null
    }

    Box(modifier) {
        Box(
            Modifier.graphicsLayer {
                translationX = direction * (1f - progress.value) * distancePx
                alpha = progress.value
            },
        ) { content(shown) }
        leaving?.let { old ->
            // Does not take part in the size: the new content decides it, the old one just slides off.
            Box(
                Modifier.matchParentSize().wrapContentSize(Alignment.CenterStart, unbounded = true).graphicsLayer {
                    translationX = -direction * progress.value * distancePx
                    alpha = 1f - progress.value
                },
            ) { content(old) }
        }
    }
}
