package com.movieworld.android

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Captions are opportunistic; an unavailable caption never delays a user's Play action. */
object HomeSync {
    suspend fun caption(context: Context, id: String) = withContext(Dispatchers.IO) {
        try {
            val details = Catalogue(context).details(id) ?: return@withContext
            val key = subtitleSourceKey(id, details.telegram)
            val cache = subtitleCache(context)
            if (cache.get(key) != null) return@withContext
            val srt = HomeConnection.get(context).client().captions(id) ?: return@withContext
            srt.byteInputStream().use { cache.saveIfAbsent(key, it, "Whisper · PC captions", "en") }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }
    suspend fun captions(context: Context) = withContext(Dispatchers.IO) {
        val home = HomeConnection.get(context)
        if (home.state.value.address == null) return@withContext
        val db = PlaybackDatabase.get(context).access()
        val ids = (listOfNotNull(Playback.state.value.movieId) + db.favouriteIds() +
            db.continuing().map { it.movieId }).distinct().take(100)
        val catalogue = Catalogue(context)
        val cache = subtitleCache(context)
        for (id in ids) {
            try {
                val details = catalogue.details(id) ?: continue
                val key = subtitleSourceKey(id, details.telegram)
                // Existing files, including explicitly disabled selections, belong to the user.
                if (cache.get(key) != null) continue
                val srt = home.client().captions(id) ?: continue
                srt.byteInputStream().use { cache.saveIfAbsent(key, it, "Whisper · PC captions", "en") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withContext }
        }
    }
}
