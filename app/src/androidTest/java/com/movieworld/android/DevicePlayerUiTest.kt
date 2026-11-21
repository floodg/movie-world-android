package com.movieworld.android

import android.net.Uri
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.movieworld.android.ui.player.DevicePlayerPresentation
import com.movieworld.android.ui.player.DevicePlayerWindow
import com.movieworld.android.ui.player.MiniPlayer
import com.movieworld.android.ui.theme.MovieWorldTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevicePlayerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val playing = CastState(active = true, movieId = "test-video", title = "Player UI test",
        message = "Playing on this device", playing = true, controllable = true,
        destination = PlaybackDestination.DEVICE, sessionId = "ui-session")

    @Test fun backRotationThenMiniPlayerStopDoesNotOpenStoppedScreen() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(playing)
        restoration.setContent {
            val presentation = rememberSaveable(saver = DevicePlayerPresentation.Saver) { DevicePlayerPresentation() }
            LaunchedEffect(state.value.sessionId, state.value.active) { presentation.observe(state.value) }
            MovieWorldTheme {
                Column {
                    Text("Catalogue")
                    if (presentation.showMiniPlayer(state.value)) MiniPlayer(state.value, null,
                        onExpand = { presentation.open(state.value) }, onPlayPause = {}, onDismiss = {},
                        onStop = {
                            presentation.stop(state.value)
                            state.value = state.value.copy(active = false, playing = false, message = "Stopped")
                        })
                }
                if (presentation.expanded && state.value.active) DevicePlayerWindow(state.value, null, true,
                    onClose = presentation::close, onToggleFullscreen = {}, onStop = {})
            }
        }
        compose.onNodeWithText("Back").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("device-player-window").assertDoesNotExist()
        compose.onNodeWithContentDescription("Stop this device").performClick()
        compose.onNodeWithTag("device-player-window").assertDoesNotExist()
        compose.onNodeWithText("Stopped").assertDoesNotExist()
        compose.onNodeWithText("Catalogue").assertIsDisplayed()
    }

    @Test fun videoFillsWindowAndChromeHidesWithoutReducingVideoBounds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val video = File.createTempFile("fullscreen-ui-", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("player-test.mp4").use { input -> video.outputStream().use { input.copyTo(it) } }
        lateinit var session: LocalVideoSession
        val selected = mutableStateOf<Player?>(null)
        val visibleBefore = Playback.deviceVisible
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.setContent {
            MovieWorldTheme { DevicePlayerWindow(playing, selected.value, true, {}, {}, {}) }
        }
        compose.activityRule.scenario.onActivity {
            it.startActivity(Intent(it, ComponentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        try {
            compose.runOnIdle {
                Playback.deviceVisible = true
                session = LocalVideoSession(context, Uri.fromFile(video), null, "Fullscreen test", 0) { }
                session.player.repeatMode = Player.REPEAT_MODE_ONE
                selected.value = session.player
            }
            compose.waitUntil(15_000) {
                var ready = false
                instrumentation.runOnMainSync { ready = session.player.isPlaying }
                ready
            }
            val window = compose.onNodeWithTag("device-player-window").getUnclippedBoundsInRoot()
            val surface = compose.onNodeWithTag("device-video-surface").getUnclippedBoundsInRoot()
            assertEquals(window, surface)
            try {
                compose.waitUntil(6000) { compose.onAllNodesWithTag("device-player-chrome").fetchSemanticsNodes().isEmpty() }
            } catch (error: Throwable) {
                File(context.cacheDir, "fullscreen-debug.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
                throw error
            }
            assertEquals(window, compose.onNodeWithTag("device-video-surface").getUnclippedBoundsInRoot())
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val density = context.resources.displayMetrics.density
            assertEquals(screenshot.width.toFloat(), (window.right.value - window.left.value) * density, 1f)
            assertEquals(screenshot.height.toFloat(), (window.bottom.value - window.top.value) * density, 1f)
            File(context.cacheDir, "fullscreen-regression.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            compose.onNodeWithTag("device-video-surface").performTouchInput { click() }
            compose.onNodeWithText("Exit fullscreen").assertIsDisplayed()
            assertEquals(window, compose.onNodeWithTag("device-video-surface").getUnclippedBoundsInRoot())
        } finally {
            compose.runOnIdle { selected.value = null; session.close(); Playback.deviceVisible = visibleBefore }
            video.delete()
        }
    }
}
