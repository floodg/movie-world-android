package com.movieworld.android

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalVideoDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun localVideoSupportsResumePauseSeekCaptionsAndCompletion() {
        val context = instrumentation.targetContext
        val video = File.createTempFile("player-test-", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("player-test.mp4").use { input -> video.outputStream().use { input.copyTo(it) } }
        val srt = File.createTempFile("player-test-", ".srt", context.cacheDir).apply {
            writeText("1\n00:00:00,000 --> 00:00:11,000\nTest captions\n")
        }
        val selected = mutableStateOf<Player?>(null)
        val flushes = AtomicInteger()
        lateinit var session: LocalVideoSession
        compose.setContent {
            AndroidView(factory = { PlayerView(it) }, update = { it.player = selected.value }, modifier = Modifier.fillMaxSize())
        }
        val wasVisible = Playback.deviceVisible
        try {
            compose.runOnIdle {
                Playback.deviceVisible = true
                session = LocalVideoSession(context, Uri.fromFile(video), srt, "Player test", 3) { flushes.incrementAndGet() }
                selected.value = session.player
            }
            compose.waitUntil(15_000) {
                var ready = false
                instrumentation.runOnMainSync { ready = session.player.isPlaying && session.player.currentPosition >= 3000 }
                ready
            }
            compose.runOnIdle {
                session.player.pause()
                val state = session.snapshot(CastState(destination = PlaybackDestination.DEVICE))
                assertTrue(state.controllable)
                assertFalse(state.playing)
                assertTrue(state.duration in 11..13)
                assertTrue(state.position >= 3)
                session.player.seekTo(6000)
            }
            compose.waitUntil(5000) {
                var captions = false
                instrumentation.runOnMainSync { captions = session.player.currentCues.cues.any { it.text.toString().contains("Test captions") } }
                captions
            }
            compose.runOnIdle {
                assertTrue(flushes.get() > 0)
                assertTrue(session.player.currentPosition >= 5900)
                session.player.seekTo(11_000)
                session.player.play()
            }
            compose.waitUntil(6000) { session.ended.isCompleted }
            compose.runOnIdle { assertEquals("Playback ended", session.snapshot(CastState()).message) }
        } finally {
            compose.runOnIdle { selected.value = null; session.close(); Playback.deviceVisible = wasVisible }
            video.delete(); srt.delete()
        }
    }

    @Test fun finishingDownloadWhileHiddenDoesNotAutoplay() {
        val context = instrumentation.targetContext
        val video = File.createTempFile("hidden-player-", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("player-test.mp4").use { input -> video.outputStream().use { input.copyTo(it) } }
        val wasVisible = Playback.deviceVisible
        lateinit var session: LocalVideoSession
        try {
            compose.runOnUiThread {
                Playback.deviceVisible = false
                session = LocalVideoSession(context, Uri.fromFile(video), null, "Hidden test", 0) { }
                assertFalse(session.player.playWhenReady)
            }
        } finally {
            compose.runOnUiThread { session.close(); Playback.deviceVisible = wasVisible }
            video.delete()
        }
    }
}
