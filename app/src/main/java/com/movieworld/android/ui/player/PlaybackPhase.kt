package com.movieworld.android.ui.player

import com.movieworld.android.CastState

/** Visual state of the TV session, derived purely from [CastState]. */
enum class PlaybackPhase { Idle, Downloading, Preparing, Playing, Paused, Ended, Error }

fun CastState.phase(): PlaybackPhase = when {
    !active && message.isBlank() -> PlaybackPhase.Idle
    !active -> if (message in ENDED_MESSAGES) PlaybackPhase.Ended else PlaybackPhase.Error
    controllable && playing -> PlaybackPhase.Playing
    controllable -> PlaybackPhase.Paused
    message.startsWith("Downloading") -> PlaybackPhase.Downloading
    else -> PlaybackPhase.Preparing
}

/** Download percentage parsed from the service message, or null when unknown. */
fun CastState.downloadFraction(): Float? {
    val percent = Regex("Downloading (\\d{1,3})%").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    return (percent / 100f).coerceIn(0f, 1f)
}

/** Fraction of the movie played, or null when the duration is unknown. */
fun CastState.playedFraction(): Float? =
    if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null

private val ENDED_MESSAGES = setOf("Stopped", "Playback ended", "Playback session ended")
