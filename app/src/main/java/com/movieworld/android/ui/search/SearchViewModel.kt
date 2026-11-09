package com.movieworld.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movieworld.android.Actor
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
    /** Null while actor suggestions are loading. Counts cover the visible catalogue. */
    val actors: List<Actor>? = emptyList(),
    val selectedActor: Actor? = null,
    val actorSuggestionsVisible: Boolean = false,
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

class SearchViewModel(private val graph: AppGraph, initialActor: String? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private var search: Job? = null

    init {
        viewModelScope.launch {
            val genres = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.genres() } }.orEmpty()
            val bounds = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.yearRange() } }
            mutableState.update { it.copy(genres = genres, yearBounds = bounds ?: it.yearBounds) }
        }
        if (initialActor == null) {
            runSearch(debounce = false)
        } else {
            mutableState.update { it.copy(query = initialActor, byActor = true) }
            search = viewModelScope.launch {
                val actor = withContext(Dispatchers.IO) {
                    catalogueResult { graph.catalogue.actors(initialActor).firstOrNull { it.name == initialActor } }
                }
                if (actor != null) selectActor(actor) else setQuery(initialActor)
            }
        }
    }

    fun setQuery(value: String) = change(debounce = value.isNotBlank()) {
        it.copy(query = value.take(80), selectedActor = null, actors = emptyList(), actorSuggestionsVisible = it.byActor)
    }
    fun setByActor(value: Boolean) = change {
        it.copy(byActor = value, selectedActor = null, actors = emptyList(), actorSuggestionsVisible = value)
    }
    fun selectActor(actor: Actor) = change {
        it.copy(query = actor.name, selectedActor = actor, actors = emptyList(), actorSuggestionsVisible = false)
    }
    fun dismissActors() { mutableState.update { it.copy(actorSuggestionsVisible = false) } }
    fun showActors() { mutableState.update { it.copy(actorSuggestionsVisible = it.byActor && it.selectedActor == null) } }
    fun submit() {
        val current = mutableState.value
        if (current.byActor && current.selectedActor == null) {
            val choices = current.actors.orEmpty()
            val exact = choices.firstOrNull { it.name.equals(current.query.trim(), ignoreCase = true) }
            val actor = exact ?: choices.singleOrNull()
            if (actor != null) { selectActor(actor); return }
            showActors()
        }
        runSearch(debounce = false)
    }
    fun setGenre(value: String?) = change { it.copy(genre = value) }
    fun setYears(from: Int?, to: Int?) = change { it.copy(yearFrom = from, yearTo = to) }
    fun clearFilters() = change { it.copy(yearFrom = null, yearTo = null) }

    private fun change(debounce: Boolean = false, transform: (SearchUiState) -> SearchUiState) {
        mutableState.update(transform)
        runSearch(debounce)
    }

    /** Same semantics as the original screen: 250 ms debounce only while typing a term. */
    private fun runSearch(debounce: Boolean) {
        search?.cancel()
        search = viewModelScope.launch {
            val findingActor = mutableState.value.byActor && mutableState.value.selectedActor == null
            mutableState.update { it.copy(results = if (findingActor) emptyList() else null, actors = if (findingActor) null else it.actors, failed = false) }
            if (debounce) delay(250)
            val snapshot = mutableState.value
            if (findingActor) {
                if (snapshot.query.trim().length < 2) {
                    mutableState.update { it.copy(actors = emptyList(), results = emptyList()) }
                    return@launch
                }
                val actors = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.actors(snapshot.query) } }
                mutableState.update { it.copy(actors = actors.orEmpty(), results = emptyList(), failed = actors == null) }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                catalogueResult {
                    graph.catalogue.movies(snapshot.query, snapshot.byActor, snapshot.genre, snapshot.yearFrom, snapshot.yearTo, actorId = snapshot.selectedActor?.id)
                }
            }
            mutableState.update { it.copy(results = result.orEmpty(), failed = result == null) }
        }
    }
}
