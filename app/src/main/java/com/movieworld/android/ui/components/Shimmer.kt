package com.movieworld.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import com.movieworld.android.ui.theme.Dimens
import com.movieworld.android.ui.theme.PosterShape
import com.movieworld.android.ui.theme.Surface2
import com.movieworld.android.ui.theme.Surface3

/** Animated sweep used while content loads. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = PosterShape) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-offset",
    )
    val brush = Brush.linearGradient(
        colors = listOf(Surface2, Surface3, Surface2),
        start = Offset(offset * 600f, 0f),
        end = Offset(offset * 600f + 600f, 400f),
    )
    Box(modifier.clip(shape).background(brush))
}

@Composable
fun ShimmerPoster(modifier: Modifier = Modifier) {
    ShimmerBox(modifier.width(Dimens.posterRowWidth).aspectRatio(Dimens.posterAspect))
}
