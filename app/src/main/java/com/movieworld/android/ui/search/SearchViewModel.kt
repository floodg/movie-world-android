package com.movieworld.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movieworld.android.Genre
import com.movieworld.android.Movie
import com.movieworld.android.ui.AppGraph
import com.movieworld.android.ui.catalogueResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val query: String = "",
    val byActor: Boolean = false,
    val genre: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val genres: List<Genre> = emptyList(),
    val yearBounds: IntRange = 1900..2030,
    /** Null while loading. */
    val results: List<Movie>? = null,
    val failed: Boolean = false,
) {
    val filterCount: Int get() = listOfNotNull(yearFrom, yearTo).size.coerceAtMost(1)
}

class SearchViewModel(private val graph: AppGraph) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private var search: Job? = null

    init {
        viewModelScope.launch {
            val genres = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.genres() } }.orEmpty()
            val bounds = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.yearRange() } }
            mutableState.update { it.copy(genres = genres, yearBounds = bounds ?: it.yearBounds) }
        }
        runSearch(debounce = false)
    }

    fun setQuery(value: String) = change(debounce = value.isNotBlank()) { it.copy(query = value) }
    fun setByActor(value: Boolean) = change { it.copy(byActor = value) }
    fun setGenre(value: String?) = change { it.copy(genre = value) }
    fun setYears(from: Int?, to: Int?) = change { it.copy(yearFrom = from, yearTo = to) }
    fun clearFilters() = change { it.copy(yearFrom = null, yearTo = null) }

    private fun change(debounce: Boolean = false, transform: (SearchUiState) -> SearchUiState) {
        mutableState.update(transform)
        runSearch(debounce)
    }

    /** Same semantics as the original screen: 200 ms debounce only while typing a term. */
    private fun runSearch(debounce: Boolean) {
        search?.cancel()
        search = viewModelScope.launch {
            mutableState.update { it.copy(results = null) }
            if (debounce) delay(200)
            val snapshot = mutableState.value
            val result = withContext(Dispatchers.IO) {
                catalogueResult {
                    graph.catalogue.movies(snapshot.query, snapshot.byActor, snapshot.genre, snapshot.yearFrom, snapshot.yearTo)
                }
            }
            mutableState.update { it.copy(results = result.orEmpty(), failed = result == null) }
        }
    }
}
