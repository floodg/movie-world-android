package com.movieworld.android.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movieworld.android.Movie
import com.movieworld.android.ui.AppGraph
import com.movieworld.android.ui.catalogueResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyListViewModel(private val graph: AppGraph) : ViewModel() {
    /** Null while loading. */
    private val mutableMovies = MutableStateFlow<List<Movie>?>(null)
    val movies: StateFlow<List<Movie>?> = mutableMovies.asStateFlow()

    init {
        viewModelScope.launch {
            graph.favourites.ids.collectLatest { ids ->
                mutableMovies.value = withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.byIds(ids) } }.orEmpty()
            }
        }
    }
}
