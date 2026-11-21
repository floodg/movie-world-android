package com.movieworld.android

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/** All methods and callbacks run on the main thread; the service persists snapshots on IO. */
internal class LocalVideoSession(
    context: Context,
    uri: Uri,
    subtitle: File?,
    title: String,
    startSeconds: Long,
    private val onFlush: () -> Unit,
) {
    val ended = CompletableDeferred<Unit>()
    val player: ExoPlayer = ExoPlayer.Builder(context).setSeekBackIncrementMs(30_000)
        .setSeekForwardIncrementMs(30_000).build()
    private val mediaSession = MediaSession.Builder(context, player).build()
    private var failure: String? = null
    private var ready = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.playWhenReady && !Playback.deviceVisible) player.pause()
            if (player.playbackState == Player.STATE_READY) ready = true
            if (!player.isPlaying || events.contains(Player.EVENT_POSITION_DISCONTINUITY)) onFlush()
            if (player.playbackState == Player.STATE_ENDED) ended.complete(Unit)
        }
        override fun onPlayerError(error: PlaybackException) {
            failure = "This device could not play the video (${error.errorCodeName}). Try another file or open it in Telegram."
            ended.complete(Unit)
        }
    }

    init {
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(listener)
        val media = MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        if (subtitle != null) media.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitle))
            .setMimeType(MimeTypes.APPLICATION_SUBRIP).setLabel("Cached subtitles")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
        player.setMediaItem(media.build(), startSeconds * 1000)
        player.prepare()
        player.playWhenReady = Playback.deviceVisible
    }

    fun snapshot(previous: CastState): CastState {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.div(1000) ?: 0
        return previous.copy(
            position = if (ready) player.currentPosition.coerceAtLeast(0) / 1000 else previous.position,
            duration = duration.takeIf { it > 0 } ?: previous.duration,
            playing = player.isPlaying, controllable = ready && failure == null,
            message = failure ?: when {
                player.playbackState == Player.STATE_ENDED -> "Playback ended"
                player.playbackState == Player.STATE_BUFFERING || !ready -> "Preparing video…"
                player.isPlaying -> "Playing on this device"
                else -> "Paused on this device"
            },
        )
    }

    fun close() {
        player.removeListener(listener)
        mediaSession.release()
        player.release()
    }
}
