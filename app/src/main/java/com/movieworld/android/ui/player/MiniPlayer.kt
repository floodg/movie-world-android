package com.movieworld.android.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movieworld.android.CastState
import com.movieworld.android.ui.components.AppIcons
import com.movieworld.android.ui.components.RemoteImage
import com.movieworld.android.ui.theme.Dimens
import com.movieworld.android.ui.theme.PosterShape
import com.movieworld.android.ui.theme.Surface2

/**
 * Persistent "now playing on TV" bar. Visual state is derived from [CastState] only; commands
 * go through the callbacks, which the app wires to the existing [com.movieworld.android.Playback.control].
 */
@Composable
fun MiniPlayer(
    state: CastState,
    poster: String?,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = state.phase()
    val tone = phaseColor(phase)
    Column(
        modifier.fillMaxWidth().background(Surface2)
            .clickable(onClickLabel = "Open TV playback controls", onClick = onExpand)
            .semantics { contentDescription = "Now playing on TV: ${state.title}. ${state.message}" },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Dimens.md, end = Dimens.xs, top = Dimens.sm, bottom = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(32.dp).height(48.dp).clip(PosterShape)) {
                RemoteImage(poster, null, Modifier.fillMaxSize(), placeholderLabel = null, iconSize = 16)
            }
            Column(Modifier.weight(1f).padding(horizontal = Dimens.md)) {
                Text(state.title.ifBlank { "TV playback" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    state.warning ?: state.message, style = MaterialTheme.typography.bodySmall,
                    color = if (state.warning != null) MaterialTheme.colorScheme.primary else tone,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.controllable) {
                IconButton(onClick = onPlayPause, Modifier.size(Dimens.touchTarget)) {
                    Icon(if (state.playing) AppIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.playing) "Pause" else "Resume")
                }
            }
            if (state.active) {
                IconButton(onClick = onStop, Modifier.size(Dimens.touchTarget)) {
                    Icon(AppIcons.Stop, contentDescription = if (state.controllable) "Stop TV" else "Cancel")
                }
            } else {
                IconButton(onClick = onDismiss, Modifier.size(Dimens.touchTarget)) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                }
            }
        }
        ProgressLine(state, phase, Modifier.fillMaxWidth().height(2.dp))
    }
}

@Composable
internal fun ProgressLine(state: CastState, phase: PlaybackPhase, modifier: Modifier) {
    val color = phaseColor(phase)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    when (phase) {
        PlaybackPhase.Playing, PlaybackPhase.Paused -> {
            val played = state.playedFraction()
            if (played != null) LinearProgressIndicator({ played }, modifier, color = color, trackColor = track, drawStopIndicator = {})
            else LinearProgressIndicator(modifier, color = color, trackColor = track)
        }
        PlaybackPhase.Downloading -> {
            val fraction = state.downloadFraction()
            if (fraction != null) LinearProgressIndicator({ fraction }, modifier, color = color, trackColor = track, drawStopIndicator = {})
            else LinearProgressIndicator(modifier, color = color, trackColor = track)
        }
        PlaybackPhase.Preparing -> LinearProgressIndicator(modifier, color = color, trackColor = track)
        else -> Box(modifier.background(color.copy(alpha = 0.6f)))
    }
}

@Composable
internal fun phaseColor(phase: PlaybackPhase): Color = when (phase) {
    PlaybackPhase.Error -> MaterialTheme.colorScheme.error
    PlaybackPhase.Ended -> MaterialTheme.colorScheme.onSurfaceVariant
    PlaybackPhase.Paused -> MaterialTheme.colorScheme.onSurfaceVariant
    PlaybackPhase.Playing -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}
