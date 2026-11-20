package com.killersparrow1.clashtrackers.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.killersparrow1.clashtrackers.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val HOLD_MS = 3_000L

/**
 * Hold the app title for 3 seconds: the phone gives one buzz and a sound plays, with the title shaking until it ends on the media
 * volume. Holding again while it plays stops it. Everything is local and skipped quietly if the sound can't play.
 */
@Composable
fun Modifier.titleEasterEgg(): Modifier {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val wobble = remember { Animatable(0f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var shakeJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose { runCatching { player?.release() }; player = null }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
    }

    // Every title_easter_egg*.ogg in res/raw is a candidate; one is picked at random, never the same one twice in a row.
    val sounds = remember {
        listOf("title_easter_egg", "title_easter_egg1", "title_easter_egg2", "title_easter_egg3")
            .map { context.resources.getIdentifier(it, "raw", context.packageName) }.filter { it != 0 }
    }
    var lastSound by remember { mutableStateOf(-1) }

    fun play() {
        stop()
        val pool = sounds.filter { it != lastSound }.ifEmpty { sounds }
        val pick = pool.randomOrNull() ?: R.raw.title_easter_egg
        lastSound = pick
        player = runCatching {
            MediaPlayer.create(context, pick)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setVolume(0.8f, 0.8f)
                setOnCompletionListener { mp -> runCatching { mp.release() }; if (player === mp) player = null }
                start()
            }
        }.getOrNull()
    }

    return this
        .rotate(wobble.value)
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val released = withTimeoutOrNull(HOLD_MS) { waitForUpOrCancellation() }
                if (released == null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (player?.isPlaying == true) stop() else play()
                    shakeJob?.cancel()
                    shakeJob = scope.launch {
                        // Shake for as long as the sound plays (at least one round if it couldn't start).
                        do {
                            wobble.animateTo(-4f, tween(70))
                            wobble.animateTo(4f, tween(70))
                        } while (player?.isPlaying == true)
                        wobble.animateTo(0f, tween(70))
                    }
                    // Wait for the finger to lift so one long press triggers once.
                    waitForUpOrCancellation()
                }
            }
        }
}
