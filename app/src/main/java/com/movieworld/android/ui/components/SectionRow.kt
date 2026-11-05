package com.movieworld.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.movieworld.android.Movie
import com.movieworld.android.ui.theme.Dimens

/**
 * Titled horizontal row of posters with snap-friendly scrolling. When [movies] is null the row
 * is still loading and shows shimmer placeholders; an empty list hides the row entirely.
 */
@Composable
fun SectionRow(
    title: String,
    movies: List<Movie>?,
    onOpen: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    progress: (Movie) -> Float? = { null },
    trailing: (@Composable () -> Unit)? = null,
) {
    if (movies != null && movies.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(Dimens.sm))
        val state = rememberLazyListState()
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            if (movies == null) {
                items(6) { ShimmerPoster() }
            } else {
                items(movies, key = { it.id }) { movie ->
                    PosterCard(movie, onClick = { onOpen(movie) }, progress = progress(movie))
                }
            }
        }
    }
}
