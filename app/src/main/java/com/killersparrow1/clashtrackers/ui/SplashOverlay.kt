package com.killersparrow1.clashtrackers.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.killersparrow1.clashtrackers.R
import kotlinx.coroutines.delay

/**
 * The start-up animation: a transparent animated WebP (res/raw/splash_anim.webp, ~3 s) played once over
 * the theme's own background, so it follows light, dark and Material You. Tap to skip. Phones below
 * Android 9 cannot play animated WebP, so they skip it.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    // Safety net: never keep the app hidden if the animation fails to report its end.
    LaunchedEffect(Unit) {
        delay(4500)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onFinished() },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    runCatching { playOnce(this, onFinished) }.onFailure { onFinished() }
                }
            },
        )
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun playOnce(view: ImageView, onFinished: () -> Unit) {
    val drawable: Drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(view.resources, R.raw.splash_anim))
    view.setImageDrawable(drawable)
    if (drawable is AnimatedImageDrawable) {
        drawable.repeatCount = 0 // play once
        drawable.registerAnimationCallback(object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(d: Drawable?) = onFinished()
        })
        drawable.start()
    } else {
        onFinished()
    }
}
