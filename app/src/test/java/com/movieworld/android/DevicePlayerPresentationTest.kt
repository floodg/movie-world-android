package com.movieworld.android

import androidx.compose.runtime.saveable.SaverScope
import com.movieworld.android.ui.player.DevicePlayerPresentation
import org.junit.Assert.*
import org.junit.Test

class DevicePlayerPresentationTest {
    private val playing = CastState(active = true, movieId = "1", playing = true, controllable = true,
        destination = PlaybackDestination.DEVICE, sessionId = "first-session")

    @Test fun backThenStopDoesNotReopenThePlayerDuringServiceShutdown() {
        val presentation = DevicePlayerPresentation()
        presentation.observe(playing)
        assertTrue(presentation.expanded)
        presentation.close()
        presentation.stop(playing)
        presentation.observe(playing.copy(message = "Paused on this device", playing = false))
        assertFalse(presentation.expanded)
        assertFalse(presentation.showMiniPlayer(playing))
        presentation.observe(playing.copy(active = false, message = "Stopped"))
        presentation.open(playing.copy(active = false, message = "Stopped"))
        assertFalse(presentation.expanded)
    }

    @Test fun rotationAfterBackPreservesDismissalUntilExplicitlyOpened() {
        val original = DevicePlayerPresentation()
        original.observe(playing)
        original.close()
        val saver = DevicePlayerPresentation.Saver
        val scope = SaverScope { true }
        val saved = with(saver) { scope.save(original) }!!
        val restored = saver.restore(saved)!!
        restored.observe(playing)
        assertFalse(restored.expanded)
        restored.open(playing)
        assertTrue(restored.expanded)
    }

    @Test fun replayOfSameMovieIsANewSessionAndAutomaticallyOpens() {
        val presentation = DevicePlayerPresentation()
        presentation.observe(playing)
        presentation.stop(playing)
        val next = playing.copy(sessionId = "next-session")
        presentation.observe(next.copy(destination = PlaybackDestination.AUTO))
        assertFalse(presentation.expanded)
        presentation.observe(next)
        assertTrue(presentation.expanded)
        assertTrue(presentation.showMiniPlayer(next))
    }

    @Test fun stoppingFromAnotherControlClosesThePlayer() {
        val presentation = DevicePlayerPresentation()
        presentation.observe(playing)
        presentation.observe(playing.copy(active = false, playing = false, message = "Stopped"))
        assertFalse(presentation.expanded)
    }
}
