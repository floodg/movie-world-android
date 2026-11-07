package com.movieworld.android.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movieworld.android.CastState
import com.movieworld.android.Featured
import com.movieworld.android.Playback
import com.movieworld.android.ui.LocalAppGraph
import com.movieworld.android.ui.LocalMessages
import com.movieworld.android.ui.components.AppIcons
import com.movieworld.android.ui.components.bottomClearance
import com.movieworld.android.ui.components.PrimaryButton
import com.movieworld.android.ui.components.RemoteImage
import com.movieworld.android.ui.components.SecondaryButton
import com.movieworld.android.ui.components.SectionRow
import com.movieworld.android.ui.components.ShimmerBox
import com.movieworld.android.ui.components.StateMessage
import com.movieworld.android.ui.components.isLandscape
import com.movieworld.android.ui.components.windowSize
import com.movieworld.android.ui.playLabel
import com.movieworld.android.ui.theme.Dimens
import com.movieworld.android.ui.theme.Night
import com.movieworld.android.ui.theme.ScrimStrong
import com.movieworld.android.ui.theme.ScrimTransparent
import com.movieworld.android.ui.watchedFraction

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onSettings: () -> Unit, contentPadding: PaddingValues) {
    val graph = LocalAppGraph.current
    val viewModel: HomeViewModel = viewModel { HomeViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val cast by Playback.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val messages = LocalMessages.current
    HomeContent(
        state = state,
        favourites = favourites,
        cast = cast,
        onOpen = onOpen,
        onToggleFavourite = viewModel::toggleFavourite,
        onPlayFeatured = { featured ->
            try {
                Playback.start(context, featured.movie, featured.telegram, restart = false)
            } catch (failure: Exception) {
                messages(failure.message ?: "Cannot start TV playback")
            }
        },
        onSettings = onSettings,
        contentPadding = contentPadding,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    favourites: Set<String>,
    cast: CastState,
    onOpen: (String) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onPlayFeatured: (Featured) -> Unit,
    onSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.bottomClearance() + Dimens.xl),
            verticalArrangement = Arrangement.spacedBy(Dimens.xl),
        ) {
            item(key = "hero") {
                Hero(
                    featured = state.featured,
                    loaded = state.featuredLoaded,
                    resume = state.featuredResume,
                    inList = state.featured?.movie?.id in favourites,
                    castActive = cast.active,
                    onOpen = onOpen,
                    onPlay = onPlayFeatured,
                    onToggleList = onToggleFavourite,
                )
            }
            if (state.failed) {
                item(key = "failed") {
                    StateMessage(Icons.Filled.Warning, "Could not read the offline catalogue",
                        body = "Reinstall the app to restore the bundled catalogue.")
                }
            }
            // Rows that are still loading show shimmer; rows known to be empty are left out entirely
            // so they do not leave gaps in the column.
            val continuing = state.continuing
            if (continuing == null || continuing.isNotEmpty()) item(key = "continue") {
                SectionRow(
                    title = "Continue Watching",
                    movies = continuing?.map { it.movie },
                    onOpen = { onOpen(it.id) },
                    progress = { movie -> continuing?.firstOrNull { it.movie.id == movie.id }?.progress?.let(::watchedFraction) },
                )
            }
            if (state.recentlyWatched?.isEmpty() != true) item(key = "recent") {
                SectionRow("Recently Watched", state.recentlyWatched, onOpen = { onOpen(it.id) })
            }
            if (state.myList?.isEmpty() != true) item(key = "my-list") { SectionRow("My List", state.myList, onOpen = { onOpen(it.id) }) }
            if (state.topRated?.isEmpty() != true) item(key = "top-rated") { SectionRow("Top Rated", state.topRated, onOpen = { onOpen(it.id) }) }
            state.genreRows.filter { it.movies?.isEmpty() != true }.forEach { row ->
                item(key = "genre-${row.genre.id}") { SectionRow(row.genre.name, row.movies, onOpen = { onOpen(it.id) }) }
            }
        }
        HomeTopBar(solid = scrolled, castActive = cast.active, onSettings = onSettings)
    }
}

@Composable
private fun HomeTopBar(solid: Boolean, castActive: Boolean, onSettings: () -> Unit) {
    val background by animateColorAsState(if (solid) Night.copy(alpha = 0.94f) else Color.Transparent, label = "top-bar")
    Row(
        Modifier.fillMaxWidth().background(background).statusBarsPadding()
            .padding(start = Dimens.screenPadding, end = Dimens.xs, top = Dimens.xs, bottom = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark(Modifier.weight(1f))
        IconButton(onClick = onSettings, Modifier.semantics {
            contentDescription = if (castActive) "TV settings, playing on TV" else "TV settings"
        }) {
            Icon(
                if (castActive) AppIcons.CastConnected else AppIcons.Cast, contentDescription = null,
                tint = if (castActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("MOVIE", style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 3.sp), color = MaterialTheme.colorScheme.onSurface)
        Text("WORLD", style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 3.sp), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Hero(
    featured: Featured?,
    loaded: Boolean,
    resume: Long,
    inList: Boolean,
    castActive: Boolean,
    onOpen: (String) -> Unit,
    onPlay: (Featured) -> Unit,
    onToggleList: (String) -> Unit,
) {
    val window = windowSize()
    val landscape = window.isLandscape
    val height = if (landscape) window.height * 0.82f else minOf(window.height * 0.62f, window.width * 1.3f)
    Box(Modifier.fillMaxWidth().height(height)) {
        when {
            featured != null -> RemoteImage(
                featured.backdrop ?: featured.movie.poster, contentDescription = null,
                Modifier.fillMaxSize(), placeholderLabel = null, iconSize = 56,
            )
            !loaded -> ShimmerBox(Modifier.fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
            else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ScrimStrong, ScrimTransparent), endY = 400f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to ScrimTransparent, 0.78f to ScrimStrong, 1f to Night)))
        if (featured != null) {
            val movie = featured.movie
            Column(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth(if (landscape) 0.6f else 1f)
                    .padding(horizontal = Dimens.screenPadding),
            ) {
                Text(
                    movie.title, style = MaterialTheme.typography.displayMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Dimens.sm))
                val meta = (listOfNotNull(movie.year, movie.rating?.let { "IMDb $it" }) + featured.genres).joinToString("  ·  ")
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Dimens.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    PrimaryButton(
                        text = playLabel(resume), onClick = { onPlay(featured) }, icon = Icons.Filled.PlayArrow,
                        enabled = !castActive, modifier = Modifier.weight(1.4f),
                    )
                    SecondaryButton(
                        text = "My List", onClick = { onToggleList(movie.id) },
                        icon = if (inList) Icons.Filled.Check else Icons.Filled.Add, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Dimens.sm))
                SecondaryButtonText(text = "Details", onClick = { onOpen(movie.id) })
            }
        }
    }
}

@Composable
private fun SecondaryButtonText(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
