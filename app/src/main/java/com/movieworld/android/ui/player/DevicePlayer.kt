package com.movieworld.android.ui.player

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.IBinder
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.movieworld.android.CastState
import com.movieworld.android.Playback
import com.movieworld.android.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** The playback service owns the player; opening/rotating this window never restarts a video. */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun DevicePlayer(state: CastState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context.activity()
    var binder by remember { mutableStateOf<PlaybackService.LocalBinder?>(null) }
    val emptyPlayer = remember { MutableStateFlow<Player?>(null) }
    val player by (binder?.player ?: emptyPlayer).collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var landscape by rememberSaveable { mutableStateOf(false) }
    val originalOrientation = rememberSaveable { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    val close = { Playback.control(context, "pause"); onDismiss() }
    LaunchedEffect(player) {
        if (player != null && Playback.deviceVisible && Playback.pendingDevicePlay.compareAndSet(true, false)) player?.play()
    }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { binder = service as PlaybackService.LocalBinder }
            override fun onServiceDisconnected(name: ComponentName) { binder = null }
        }
        val bound = context.bindService(Intent(context, PlaybackService::class.java), connection, 0)
        onDispose { if (bound) context.unbindService(connection) }
    }
    DisposableEffect(lifecycle, activity) {
        Playback.deviceVisible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) Playback.deviceVisible = true
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) {
                Playback.deviceVisible = false
                Playback.pendingDevicePlay.set(false)
                Playback.control(context, "pause")
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) {
                Playback.deviceVisible = false
                Playback.pendingDevicePlay.set(false)
                Playback.control(context, "pause")
            }
        }
    }
    DisposableEffect(activity) {
        onDispose { if (activity?.isChangingConfigurations != true) activity?.requestedOrientation = originalOrientation }
    }
    DevicePlayerWindow(state, player, landscape, onClose = close, onToggleFullscreen = {
        landscape = !landscape
        activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else originalOrientation
    }, onStop = { Playback.control(context, "stop"); onDismiss() })
}

/** Video fills the window; all chrome overlays it and follows the native controls' visibility. */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun DevicePlayerWindow(
    state: CastState,
    player: Player?,
    fullscreen: Boolean,
    onClose: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onStop: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view, state.playing) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= 28) window?.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            if (state.playing) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        BackHandler(onBack = onClose)
        var controlsVisible by remember { mutableStateOf(true) }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("device-player-window")) {
            if (player != null) {
                val attachedPlayer = player
                AndroidView(factory = { PlayerView(it).apply {
                    controllerShowTimeoutMs = 3000
                    setControllerAnimationEnabled(false)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == View.VISIBLE
                    })
                    setShowSubtitleButton(true)
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    this.player = attachedPlayer
                    showController()
                } }, update = {
                    if (it.player !== attachedPlayer) {
                        it.player = attachedPlayer
                        it.showController()
                    }
                }, onRelease = {
                    it.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
                    it.player = null
                }, modifier = Modifier.fillMaxSize().testTag("device-video-surface"))
            } else {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text(state.message, color = if (state.active) Color.White else MaterialTheme.colorScheme.error)
                    if (state.active) {
                        Spacer(Modifier.height(16.dp))
                        val fraction = state.downloadFraction()
                        if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick = onStop) { Text("Cancel") }
                    }
                }
            }
            if (controlsVisible || player == null) {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(bottom = 12.dp).testTag("device-player-chrome")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onClose) { Text("Back") }
                        TextButton(onClick = onToggleFullscreen) { Text(if (fullscreen) "Exit fullscreen" else "Fullscreen") }
                        TextButton(onClick = onStop) { Text(if (state.active) "Stop" else "Close") }
                    }
                    Text(state.title, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
