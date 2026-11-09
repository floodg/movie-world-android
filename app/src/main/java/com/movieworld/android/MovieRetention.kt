package com.movieworld.android

internal object MovieRetention {
    const val KEEP_MS = 7L * 24 * 60 * 60 * 1000
    fun expired(lastPlayed: Long, now: Long, active: Boolean): Boolean =
        !active && now >= lastPlayed && now - lastPlayed >= KEEP_MS
}
