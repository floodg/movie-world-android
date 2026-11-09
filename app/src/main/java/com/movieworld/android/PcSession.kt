package com.movieworld.android

/** One PC-owned session shared by every connected remote. */
internal data class PcSession(
    val id: String, val movieId: String, val title: String,
    val rendererId: String, val rendererName: String,
    val state: String, val position: Long?, val duration: Long?,
    val detail: String = "", val error: String? = null,
) {
    val active get() = state !in setOf("stopped", "ended", "cancelled", "failed")
    fun canJoin(selectedRenderer: String) = active && selectedRenderer.isNotBlank() &&
        rendererId == selectedRenderer && !rendererId.startsWith("tizen:")

    fun castState(previous: CastState = CastState()): CastState {
        val sameMovie = previous.movieId == movieId
        return CastState(
            active = active, movieId = movieId, title = title,
            position = position ?: if (sameMovie) previous.position else 0,
            duration = duration?.takeIf { it > 0 } ?: if (sameMovie) previous.duration else 0,
            playing = state == "playing", controllable = active && state in setOf("playing", "paused"),
            message = error ?: when (state) {
                "playing" -> "Playing from PC on $rendererName"
                "paused" -> "Paused on $rendererName · PC stream"
                "stopped", "ended", "cancelled" -> "Playback ended"
                "failed" -> detail.ifBlank { "PC playback failed" }
                else -> "PC: ${detail.ifBlank { "Preparing movie…" }}"
            },
            warning = previous.warning.takeIf { sameMovie },
        )
    }
}
