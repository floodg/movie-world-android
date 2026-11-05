package com.movieworld.android

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** "My List" membership, kept in SharedPreferences under the key the app has always used. */
class Favourites(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("movie-world", Context.MODE_PRIVATE)
    private val mutableIds = MutableStateFlow(preferences.getStringSet("favourites", emptySet()).orEmpty().toSet())
    val ids: StateFlow<Set<String>> = mutableIds.asStateFlow()

    fun toggle(id: String) {
        mutableIds.update { if (id in it) it - id else it + id }
        preferences.edit { putStringSet("favourites", mutableIds.value) }
    }
}
