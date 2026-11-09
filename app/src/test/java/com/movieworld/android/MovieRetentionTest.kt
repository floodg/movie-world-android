package com.movieworld.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieRetentionTest {
    @Test fun expiresAtSevenDays() {
        val played = 1_000L
        assertFalse(MovieRetention.expired(played, played + MovieRetention.KEEP_MS - 1, false))
        assertTrue(MovieRetention.expired(played, played + MovieRetention.KEEP_MS, false))
    }
    @Test fun protectsActivePlayback() {
        assertFalse(MovieRetention.expired(0, MovieRetention.KEEP_MS * 2, true))
    }
    @Test fun replayRestartsRetention() {
        val now = MovieRetention.KEEP_MS * 3
        assertTrue(MovieRetention.expired(0, now, false))
        assertFalse(MovieRetention.expired(now, now, false))
    }
    @Test fun clockMovingBackDoesNotExpireMovies() {
        assertFalse(MovieRetention.expired(10_000, 1_000, false))
    }
}
