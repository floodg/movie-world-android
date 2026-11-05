package com.movieworld.android.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movieworld.android.Movie
import com.movieworld.android.ui.LocalAppGraph
import com.movieworld.android.ui.components.AppIcons
import com.movieworld.android.ui.components.bottomClearance
import com.movieworld.android.ui.components.PosterCard
import com.movieworld.android.ui.components.ShimmerBox
import com.movieworld.android.ui.components.StateMessage
import com.movieworld.android.ui.theme.Dimens

@Composable
fun MyListScreen(onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    val graph = LocalAppGraph.current
    val viewModel: MyListViewModel = viewModel { MyListViewModel(graph) }
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    MyListContent(movies, onOpen, contentPadding)
}

@Composable
fun MyListContent(movies: List<Movie>?, onOpen: (String) -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "My List", style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.md).semantics { heading() },
        )
        if (movies != null && movies.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                StateMessage(
                    AppIcons.BookmarkBorder, "Nothing saved yet", Modifier.align(Alignment.Center),
                    body = "Tap My List on any movie to keep it here for later.",
                )
            }
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimens.posterGridMinCell),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.screenPadding, end = Dimens.screenPadding,
                top = Dimens.xs, bottom = contentPadding.bottomClearance() + Dimens.lg,
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            if (movies == null) {
                items(9) { ShimmerBox(Modifier.fillMaxWidth().aspectRatio(Dimens.posterAspect)) }
            } else {
                items(movies, key = { it.id }) { movie ->
                    PosterCard(movie, onClick = { onOpen(movie.id) }, width = null, showRating = true)
                }
            }
        }
    }
}
