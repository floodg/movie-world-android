package com.movieworld.android.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.movieworld.android.Catalogue
import com.movieworld.android.Favourites
import com.movieworld.android.PlaybackDatabase

/** Process-wide dependencies handed to ViewModels through composition. */
class AppGraph(context: Context, val openTelegram: (String) -> Unit) {
    val catalogue = Catalogue(context.applicationContext)
    val favourites = Favourites(context)
    val progress: PlaybackDatabase.Access by lazy { PlaybackDatabase.get(context.applicationContext).access() }
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }

/** Saved position counts as resumable unless it is within 30 s of a known end. */
fun resumePosition(progress: PlaybackDatabase.Progress?): Long =
    if (progress != null && (progress.duration == 0L || progress.position < progress.duration - 30))
        progress.position else 0L

/** Fraction watched for progress bars, or null when the duration is unknown. */
fun watchedFraction(progress: PlaybackDatabase.Progress): Float? =
    if (progress.duration > 0) (progress.position.toFloat() / progress.duration).coerceIn(0f, 1f) else null

/** Label for the primary playback action on the detail screen. */
fun playLabel(resume: Long): String = if (resume >= 15) "Resume · ${com.movieworld.android.formatTime(resume)}" else "Play on TV"

inline fun <T> catalogueResult(block: () -> T): T? = try {
    block()
} catch (_: java.io.IOException) {
    null
} catch (_: android.database.sqlite.SQLiteException) {
    null
}
