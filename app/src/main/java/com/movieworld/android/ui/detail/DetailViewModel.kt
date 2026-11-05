package com.movieworld.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movieworld.android.Movie
import com.movieworld.android.MovieDetails
import com.movieworld.android.Playback
import com.movieworld.android.PlaybackDatabase
import com.movieworld.android.ui.AppGraph
import com.movieworld.android.ui.catalogueResult
import com.movieworld.android.ui.resumePosition
import com.movieworld.android.ui.watchedFraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailUiState(
    val movie: Movie? = null,
    val details: MovieDetails? = null,
    val loading: Boolean = true,
    val resume: Long = 0,
    val watched: Float? = null,
    /** Null while loading. */
    val similar: List<Movie>? = null,
)

class DetailViewModel(private val graph: AppGraph, val movieId: String) : ViewModel() {
    private val mutableState = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = mutableState.asStateFlow()
    val favourites: StateFlow<Set<String>> = graph.favourites.ids

    init {
        viewModelScope.launch {
            val movie = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.movie(movieId) } }
            val details = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.details(movieId) } }
            mutableState.update { it.copy(movie = movie, details = details, loading = false) }
            val similar = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.similar(movieId, 18) } }
            mutableState.update { it.copy(similar = similar.orEmpty()) }
        }
        viewModelScope.launch {
            Playback.revision.collectLatest {
                val progress: PlaybackDatabase.Progress? = withContext(Dispatchers.IO) { catalogueResult { graph.progress.find(movieId) } }
                mutableState.update {
                    it.copy(resume = resumePosition(progress), watched = progress?.let(::watchedFraction))
                }
            }
        }
    }

    fun toggleFavourite() = graph.favourites.toggle(movieId)
}
