package com.movieworld.android.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movieworld.android.ui.LocalAppGraph
import com.movieworld.android.ui.components.AppIcons
import com.movieworld.android.ui.components.GenreChip
import com.movieworld.android.ui.components.PosterCard
import com.movieworld.android.ui.components.SecondaryButton
import com.movieworld.android.ui.components.ShimmerBox
import com.movieworld.android.ui.components.StateMessage
import com.movieworld.android.ui.theme.Dimens
import com.movieworld.android.ui.theme.PillShape

@Composable
fun SearchScreen(onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    val graph = LocalAppGraph.current
    val viewModel: SearchViewModel = viewModel { SearchViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        onQuery = viewModel::setQuery,
        onByActor = viewModel::setByActor,
        onGenre = viewModel::setGenre,
        onYears = viewModel::setYears,
        onClearFilters = viewModel::clearFilters,
        onOpen = onOpen,
        contentPadding = contentPadding,
    )
}

@Composable
fun SearchContent(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onByActor: (Boolean) -> Unit,
    onGenre: (String?) -> Unit,
    onYears: (Int?, Int?) -> Unit,
    onClearFilters: () -> Unit,
    onOpen: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Search" },
                singleLine = true,
                shape = PillShape,
                placeholder = { Text(if (state.byActor) "Search by actor" else "Search by title") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.width(Dimens.sm))
            BadgedBox(badge = { if (state.filterCount > 0) Badge { Text(state.filterCount.toString()) } }) {
                IconButton(onClick = { showFilters = true }, Modifier.semantics { contentDescription = "Filters" }) {
                    Icon(AppIcons.Tune, contentDescription = null,
                        tint = if (state.filterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = Dimens.screenPadding).fillMaxWidth()) {
            SegmentedButton(
                selected = !state.byActor, onClick = { onByActor(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = segmentColors(),
            ) { Text("Title") }
            SegmentedButton(
                selected = state.byActor, onClick = { onByActor(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                colors = segmentColors(),
            ) { Text("Actor") }
        }
        Spacer(Modifier.height(Dimens.md))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            item(key = "all") { GenreChip("All genres", state.genre == null, { onGenre(null) }) }
            items(state.genres, key = { it.id }) { genre ->
                GenreChip(genre.name, state.genre == genre.id, { onGenre(if (state.genre == genre.id) null else genre.id) })
            }
        }
        Spacer(Modifier.height(Dimens.md))
        Box(Modifier.fillMaxSize()) {
            val results = state.results
            when {
                state.failed -> StateMessage(Icons.Filled.Warning, "Could not read the offline catalogue", Modifier.align(Alignment.Center),
                    body = "Reinstall the app to restore the bundled catalogue.")
                results != null && results.isEmpty() -> StateMessage(
                    Icons.Filled.Search, "No matching movies", Modifier.align(Alignment.Center),
                    body = "Try another spelling, switch between Title and Actor, or widen the filters.",
                    action = if (state.filterCount > 0 || state.genre != null) ({
                        SecondaryButton("Clear filters", onClick = { onClearFilters(); onGenre(null) })
                    }) else null,
                )
                else -> ResultGrid(results, onOpen, contentPadding)
            }
        }
    }
    if (showFilters) {
        FilterSheet(
            bounds = state.yearBounds,
            from = state.yearFrom,
            to = state.yearTo,
            onApply = { from, to -> onYears(from, to); showFilters = false },
            onClear = { onClearFilters(); showFilters = false },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun segmentColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary,
    activeContentColor = MaterialTheme.colorScheme.onPrimary,
    activeBorderColor = Color.Transparent,
    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveBorderColor = Color.Transparent,
)

@Composable
private fun ResultGrid(results: List<com.movieworld.android.Movie>?, onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.posterGridMinCell),
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Search results" },
        contentPadding = PaddingValues(
            start = Dimens.screenPadding, end = Dimens.screenPadding,
            top = Dimens.xs, bottom = contentPadding.calculateBottomPadding() + Dimens.lg,
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        if (results == null) {
            items(12) { ShimmerBox(Modifier.fillMaxWidth().aspectRatio(Dimens.posterAspect)) }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }, key = "count") {
                Text("${results.size} movies", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            items(results, key = { it.id }) { movie ->
                PosterCard(movie, onClick = { onOpen(movie.id) }, width = null, showRating = true)
            }
        }
    }
}
