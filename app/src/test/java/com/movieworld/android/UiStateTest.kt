package com.movieworld.android

import com.movieworld.android.ui.playLabel
import com.movieworld.android.ui.player.PlaybackPhase
import com.movieworld.android.ui.player.downloadFraction
import com.movieworld.android.ui.player.phase
import com.movieworld.android.ui.player.playedFraction
import com.movieworld.android.ui.resumePosition
import com.movieworld.android.ui.watchedFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiStateTest {
    @Test fun playbackPhasesFollowServiceState() {
        assertEquals(PlaybackPhase.Idle, CastState().phase())
        assertEquals(PlaybackPhase.Preparing, CastState(active = true, message = "Preparing TV playback…").phase())
        assertEquals(PlaybackPhase.Downloading, CastState(active = true, message = "Downloading 42%").phase())
        assertEquals(PlaybackPhase.Playing, CastState(active = true, controllable = true, playing = true, message = "Playing on TV").phase())
        assertEquals(PlaybackPhase.Paused, CastState(active = true, controllable = true, playing = false, message = "Paused on TV").phase())
        assertEquals(PlaybackPhase.Ended, CastState(active = false, message = "Stopped").phase())
        assertEquals(PlaybackPhase.Ended, CastState(active = false, message = "Playback ended").phase())
        assertEquals(PlaybackPhase.Error, CastState(active = false, message = "The TV did not start playback.").phase())
    }

    @Test fun progressFractionsAreBoundedAndOptional() {
        assertEquals(0.42f, CastState(message = "Downloading 42%").downloadFraction())
        assertNull(CastState(message = "Downloading 120 MB").downloadFraction())
        assertEquals(0.5f, CastState(position = 50, duration = 100).playedFraction())
        assertNull(CastState(position = 50, duration = 0).playedFraction())
        assertEquals(1f, CastState(position = 500, duration = 100).playedFraction())
    }

    @Test fun resumeLabelAndPositionMatchOriginalRules() {
        assertEquals("Play on TV", playLabel(0))
        assertEquals("Play on TV", playLabel(14))
        assertEquals("Resume · 00:00:15", playLabel(15))
        assertEquals("Resume · 01:02:03", playLabel(3723))
        assertEquals(0L, resumePosition(null))
        val progress = PlaybackDatabase.Progress().apply { movieId = "m"; position = 300; duration = 7200 }
        assertEquals(300L, resumePosition(progress))
        assertEquals(300f / 7200f, watchedFraction(progress))
        progress.position = 7190
        assertEquals(0L, resumePosition(progress))
        progress.duration = 0
        assertEquals(7190L, resumePosition(progress))
        assertNull(watchedFraction(progress))
    }
}
