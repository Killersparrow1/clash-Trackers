@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.killersparrow1.clashtrackers.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().wiggleOnTap(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            content()
        }
    }
}

/** A card that is closed by default: the whole header row toggles it, "+" when closed and "−" when open. */
@Composable
internal fun CollapsibleCard(
    title: String,
    stateKey: String,
    modifier: Modifier = Modifier,
    /** True for a card that has a page of its own: no "+" / "−", always open. */
    alwaysOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var openState by rememberSaveable(stateKey) { mutableStateOf(false) }
    val open = alwaysOpen || openState
    Card(
        modifier = modifier.fillMaxWidth().wiggleOnTap(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (alwaysOpen) Modifier else Modifier.clickable { openState = !openState }),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                if (!alwaysOpen) Text(if (open) "−" else "+", style = MaterialTheme.typography.headlineMedium)
            }
            AnimatedVisibility(visible = open) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

/** A titled block inside a card, so several related blocks can share one card. */
@Composable
internal fun SubSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** A smooth sideways slide with a fade: the new page comes in from the side you swiped towards. */
internal fun slideTransition(forward: Boolean): androidx.compose.animation.ContentTransform {
    val spring = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
    )
    val sign = if (forward) 1 else -1
    return (androidx.compose.animation.slideInHorizontally(spring) { sign * it / 3 } +
        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 40))) togetherWith
        (androidx.compose.animation.slideOutHorizontally(spring) { -sign * it / 3 } +
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)))
}

/**
 * The "glass spring": on a plain tap the card swells a little, then springs past its size and settles
 * (damping 0.41, stiffness 386), with a crisp tick. It only reacts to a tap on the card itself: a scroll, or a switch,
 * button or checkbox inside the card, leaves the card still. Follows the system "touch feedback" setting.
 */
@Composable
internal fun Modifier.wiggleOnTap(): Modifier {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                // Null when the touch was a scroll or a switch, button or checkbox inside the card took it: then the card stays still.
                if (waitForUpOrCancellation() != null) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    scope.launch {
                        scale.snapTo(1.03f)
                        scale.animateTo(1f, spring(dampingRatio = 0.41f, stiffness = 386f))
                    }
                }
            }
        }
}

/** Drives [scale] with the glass spring from touches on this element; it never consumes the touch, so taps still work. */
@Composable
internal fun Modifier.glassTouch(scale: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, swell: Float, tick: Boolean): Modifier {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    return this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            scope.launch { scale.animateTo(swell, spring(dampingRatio = 0.8f, stiffness = 900f)) }
            val up = waitForUpOrCancellation()
            if (up != null && tick) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.41f, stiffness = 386f)) }
        }
    }
}

/** The light rounded tile behind every game picture (buildings, troops, heroes, spells), so they look the same everywhere. */
@Composable
internal fun Modifier.iconTile(size: androidx.compose.ui.unit.Dp): Modifier =
    this.size(size)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.27f))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .padding(size * 0.07f)
