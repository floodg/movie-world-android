package com.movieworld.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movieworld.android.Featured
import com.movieworld.android.Genre
import com.movieworld.android.Movie
import com.movieworld.android.Playback
import com.movieworld.android.PlaybackDatabase
import com.movieworld.android.ui.AppGraph
import com.movieworld.android.ui.catalogueResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Continuing(val movie: Movie, val progress: PlaybackDatabase.Progress)
data class GenreRow(val genre: Genre, val movies: List<Movie>?)

/** Lists are null while loading; empty lists hide their row. */
data class HomeUiState(
    val featured: Featured? = null,
    val featuredLoaded: Boolean = false,
    val featuredResume: Long = 0,
    val continuing: List<Continuing>? = null,
    val recentlyWatched: List<Movie>? = null,
    val myList: List<Movie>? = null,
    val topRated: List<Movie>? = null,
    val genreRows: List<GenreRow> = emptyList(),
    val failed: Boolean = false,
)

class HomeViewModel(private val graph: AppGraph) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()
    val favourites: StateFlow<Set<String>> = graph.favourites.ids

    init {
        viewModelScope.launch {
            val featured = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.featured() } }
            mutableState.update { it.copy(featured = featured, featuredLoaded = true) }
        }
        viewModelScope.launch {
            Playback.revision.collectLatest { refreshProgress() }
        }
        viewModelScope.launch {
            graph.favourites.ids.collectLatest { ids ->
                val list = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.byIds(ids) } }
                mutableState.update { it.copy(myList = list.orEmpty()) }
            }
        }
        viewModelScope.launch {
            val top = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.topRated(ROW_SIZE) } }
            mutableState.update { it.copy(topRated = top.orEmpty(), failed = top == null) }
            val genres = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.popularGenres(GENRE_ROWS) } }.orEmpty()
            mutableState.update { it.copy(genreRows = genres.map { GenreRow(it, null) }) }
            for (genre in genres) {
                val movies = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.byGenre(genre.id, ROW_SIZE) } }.orEmpty()
                mutableState.update { home ->
                    home.copy(genreRows = home.genreRows.map { if (it.genre.id == genre.id) it.copy(movies = movies) else it })
                }
            }
        }
    }

    private suspend fun refreshProgress() {
        val continuing = withContext(Dispatchers.IO) {
            catalogueResult {
                graph.progress.continuing().mapNotNull { progress ->
                    graph.catalogue.movie(progress.movieId)?.let { Continuing(it, progress) }
                }
            }
        }
        val recent = withContext(Dispatchers.IO) {
            catalogueResult {
                graph.progress.recentlyWatched(24).mapNotNull { progress ->
                    graph.catalogue.movie(progress.movieId)
                }
            }
        }
        mutableState.update { home ->
            val featuredId = home.featured?.movie?.id
            val resume = continuing?.firstOrNull { it.movie.id == featuredId }?.progress?.position ?: 0L
            home.copy(
                continuing = continuing.orEmpty(),
                recentlyWatched = recent.orEmpty(),
                featuredResume = resume,
            )
        }
    }

    fun toggleFavourite(id: String) = graph.favourites.toggle(id)

    companion object {
        const val ROW_SIZE = 24
        const val GENRE_ROWS = 6
    }
}
