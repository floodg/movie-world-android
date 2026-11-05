package com.movieworld.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movieworld.android.Movie
import com.movieworld.android.ui.theme.Dimens
import com.movieworld.android.ui.theme.PosterShape

/**
 * 2:3 poster with press feedback, optional rating badge and a thin resume progress line.
 * [width] null fills the available width (grid cells); otherwise fixed for rows.
 */
@Composable
fun PosterCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = Dimens.posterRowWidth,
    progress: Float? = null,
    showRating: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = 600f), label = "poster-press")
    val description = buildString {
        append(movie.title)
        movie.year?.let { append(", $it") }
        progress?.let { append(", ${(it * 100).toInt()} percent watched") }
    }
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .aspectRatio(Dimens.posterAspect)
            .scale(scale)
            .clip(PosterShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        RemoteImage(movie.poster, contentDescription = null, Modifier.fillMaxSize(), placeholderLabel = movie.title)
        if (showRating && movie.rating != null) {
            RatingBadge(movie.rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        if (progress != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.5f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxSize().background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}
