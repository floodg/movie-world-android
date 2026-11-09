package com.movieworld.android

import org.junit.Assert.*
import org.junit.Test

class PcSessionTest {
    private fun session(state: String = "playing", position: Long? = 400, duration: Long? = 1000,
        renderer: String = "uuid:home-tv") = PcSession("job-1", "1504", "Movie", renderer, "Home TV",
            state, position, duration)

    @Test fun joinsExistingSessionOnlyOnSelectedTv() {
        assertTrue(session().canJoin("uuid:home-tv"))
        assertFalse(session().canJoin("uuid:other-tv"))
        assertFalse(session().canJoin(""))
        assertFalse(session("ended").canJoin("uuid:home-tv"))
        assertFalse(session(renderer = "tizen:192.168.0.5").canJoin("tizen:192.168.0.5"))
    }

    @Test fun existingPcStreamAppearsAsActivePlaybackWithProgress() {
        val state = session().castState()
        assertTrue(state.active)
        assertTrue(state.playing)
        assertTrue(state.controllable)
        assertEquals("1504", state.movieId)
        assertEquals("Movie", state.title)
        assertEquals(400, state.position)
        assertEquals(1000, state.duration)
        assertTrue(state.message.contains("Home TV"))
    }

    @Test fun changesFromOtherRemoteUpdateSamePlayer() {
        val playing = session().castState()
        val paused = session("paused", 450).castState(playing)
        assertFalse(paused.playing)
        assertTrue(paused.active && paused.controllable)
        assertEquals(450, paused.position)
        val sought = session("playing", 700).castState(paused)
        assertTrue(sought.playing)
        assertEquals(700, sought.position)
        val stopped = session("stopped", 705).castState(sought)
        assertFalse(stopped.active || stopped.controllable || stopped.playing)
        assertEquals(705, stopped.position)
    }

    @Test fun temporarilyMissingTimesKeepCurrentSessionProgress() {
        val previous = session().castState()
        val pending = session("starting", null, null).castState(previous)
        assertEquals(400, pending.position)
        assertEquals(1000, pending.duration)
        assertFalse(pending.controllable)
        val otherMovie = session(position = null, duration = null).copy(movieId = "2000").castState(previous)
        assertEquals(0, otherMovie.position)
        assertEquals(0, otherMovie.duration)
    }

    @Test fun completionKeepsFinalPositionAndClosesControls() {
        val ended = session("ended", 1000).castState()
        assertEquals(1000, ended.position)
        assertEquals("Playback ended", ended.message)
        assertFalse(ended.active || ended.controllable)
    }
}
