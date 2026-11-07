package com.movieworld.android

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "My List" membership backed by Room so removals can sync as tombstones.
 * Imports the legacy SharedPreferences set once.
 */
class Favourites private constructor(context: Context) {
    private val app = context.applicationContext
    private val access = PlaybackDatabase.get(app).access()
    private val preferences = app.getSharedPreferences("movie-world", Context.MODE_PRIVATE)
    private val mutableIds = MutableStateFlow(emptySet<String>())
    val ids: StateFlow<Set<String>> = mutableIds.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            migrateLegacyPrefs()
            refreshSync()
        }
    }

    fun refresh() {
        scope.launch {
            refreshSync()
        }
    }

    private fun refreshSync() {
        mutableIds.value = access.favouriteIds().toSet()
    }

    fun toggle(id: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            val existing = access.findFavourite(id)
            val row = PlaybackDatabase.Favourite().apply {
                movieId = id
                updated = now
                dirty = true
                removed = if (existing == null || existing.removed != null) null else now
            }
            access.saveFavourite(row)
            refreshSync()
            SyncHooks.schedule(400)
        }
    }

    suspend fun upsertFromCloud(movieId: String, updatedMs: Long, removedMs: Long?) = withContext(Dispatchers.IO) {
        val existing = access.findFavourite(movieId)
        if (existing != null && existing.updated > updatedMs) return@withContext
        access.saveFavourite(PlaybackDatabase.Favourite().apply {
            this.movieId = movieId
            updated = updatedMs
            removed = removedMs
            dirty = false
        })
    }

    private fun migrateLegacyPrefs() {
        if (preferences.getBoolean("favourites-migrated-v2", false)) return
        val legacy = preferences.getStringSet("favourites", emptySet()).orEmpty()
        val now = System.currentTimeMillis()
        for (id in legacy) {
            if (access.findFavourite(id) != null) continue
            access.saveFavourite(PlaybackDatabase.Favourite().apply {
                movieId = id
                updated = now
                removed = null
                dirty = true
            })
        }
        preferences.edit()
            .putBoolean("favourites-migrated-v2", true)
            .remove("favourites")
            .apply()
    }

    companion object {
        @Volatile private var instance: Favourites? = null
        fun get(context: Context): Favourites = instance ?: synchronized(this) {
            instance ?: Favourites(context).also { instance = it }
        }
    }
}

