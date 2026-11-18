package com.killersparrow1.clashtrackers.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.killersparrow1.clashtrackers.core.HideOnScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Sizes of the floating tab bar. */
internal object FloatingBar {
    val Height = 64.dp
    val BottomMargin = 12.dp
    val SideMargin = 16.dp
    val MaxWidth = 420.dp

    /** Bottom room a page keeps free: the bar, its margin and a little air (the system navigation bar is added by the Scaffold). */
    val ContentInset = Height + BottomMargin + 12.dp
}

internal class TabBarItem(@StringRes val label: Int, @DrawableRes val icon: Int)

/**
 * When the floating bar hides. It listens to the movement of the page lists through nested scrolling and keeps only a few
 * numbers (see [HideOnScroll]); nothing is measured while scrolling. The page distance from the top is worked out from how
 * far each page has been scrolled so far, kept per page.
 */
@Stable
internal class FloatingBarState(
    private val pager: PagerState,
    hideAfterPx: Float,
    showAfterPx: Float,
    nearTopPx: Float,
) {
    private val tracker = HideOnScroll(hideAfterPx, showAfterPx, nearTopPx)
    private val fromTop = HashMap<Int, Float>()

    var visible by mutableStateOf(true)
        private set

    /** Counts scroll events; used to notice that scrolling has stopped. */
    var activity by mutableIntStateOf(0)
        private set

    fun show() {
        tracker.show()
        visible = true
    }

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = -consumed.y // positive: the list moved toward its end ("down")
            if (dy == 0f) return Offset.Zero
            val page = pager.currentPage
            val distance = ((fromTop[page] ?: 0f) + dy).coerceAtLeast(0f)
            fromTop[page] = distance
            // Never hide while the tabs themselves are moving (a swipe or a tap on a tab).
            if (pager.isScrollInProgress) {
                show()
                return Offset.Zero
            }
            visible = tracker.onScroll(dy, distance)
            activity++
            return Offset.Zero
        }
    }
}

@Composable
internal fun rememberFloatingBarState(pager: PagerState): FloatingBarState {
    val density = LocalDensity.current
    val state = remember(pager, density) {
        with(density) { FloatingBarState(pager, 12.dp.toPx(), 6.dp.toPx(), 24.dp.toPx()) }
    }
    // A tab change, by swipe or by tap, shows the bar at once.
    LaunchedEffect(state, pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { moving -> if (moving) state.show() }
    }
    // Scrolling stopped for a moment: show it again.
    LaunchedEffect(state) {
        snapshotFlow { state.activity }.collectLatest {
            if (!state.visible) {
                delay(700)
                state.show()
            }
        }
    }
    return state
}

/**
 * The tab bar as a floating pill: inset from the screen edges, fully rounded, solid colour with a hairline border and a
 * soft static shadow (no blur or transparency). It hides by sliding down and comes back the same way; the highlight
 * slides between tabs on a spring. Only translation is animated. With [reduced] the bar stays put and the highlight moves
 * without a spring.
 */
@Composable
internal fun FloatingTabBar(
    items: List<TabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    barState: FloatingBarState,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    // How far to slide to be fully out of sight: bar, margin, system bar and the shadow.
    val travel = with(density) { (FloatingBar.Height + FloatingBar.BottomMargin + 16.dp).toPx() } + navBottom
    val hide = remember { Animatable(0f) }
    LaunchedEffect(barState, reduced) {
        snapshotFlow { barState.visible }.collectLatest { visible ->
            val target = if (visible || reduced) 0f else 1f
            if (reduced) hide.snapTo(target) else hide.animateTo(target, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
        }
    }

    Surface(
        modifier = modifier
            .graphicsLayer { translationY = hide.value * travel }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = FloatingBar.SideMargin, end = FloatingBar.SideMargin, bottom = FloatingBar.BottomMargin)
            .widthIn(max = FloatingBar.MaxWidth)
            .fillMaxWidth()
            .height(FloatingBar.Height),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
            val itemWidth = maxWidth / items.size
            val itemWidthPx = with(density) { itemWidth.toPx() }
            val slide = remember { Animatable(itemWidthPx * selectedIndex) }
            LaunchedEffect(selectedIndex, itemWidthPx, reduced) {
                val target = itemWidthPx * selectedIndex
                if (slide.value == target) return@LaunchedEffect
                if (reduced) slide.animateTo(target, tween(120))
                else slide.animateTo(target, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
            }
            Box(
                Modifier
                    .width(itemWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = slide.value }
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(percent = 50)),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                items.forEachIndexed { i, item ->
                    val selected = i == selectedIndex
                    val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(percent = 50))
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(i) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(painterResource(item.icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Text(
                            stringResource(item.label),
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
