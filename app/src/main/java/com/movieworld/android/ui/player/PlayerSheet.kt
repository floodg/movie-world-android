package com.movieworld.android.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movieworld.android.CastState
import com.movieworld.android.formatTime
import com.movieworld.android.ui.components.AppIcons
import com.movieworld.android.ui.components.RemoteImage
import com.movieworld.android.ui.components.SecondaryButton
import com.movieworld.android.ui.theme.Dimens
import com.movieworld.android.ui.theme.PosterShape
import com.movieworld.android.ui.theme.SheetShape

/** Expanded TV playback controls. Commands map 1:1 onto the existing service actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    state: CastState,
    poster: String?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val phase = state.phase()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.xl).navigationBarsPadding().padding(bottom = Dimens.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(72.dp).height(108.dp).clip(PosterShape)) {
                    RemoteImage(poster, null, Modifier.fillMaxSize(), placeholderLabel = null)
                }
                Spacer(Modifier.width(Dimens.lg))
                Column(Modifier.weight(1f)) {
                    Text("NOW ON TV", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(Dimens.xs))
                    Text(state.title.ifBlank { "TV playback" }, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(Dimens.xs))
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = phaseColor(phase))
                    state.warning?.let {
                        Spacer(Modifier.height(Dimens.xs))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(Dimens.xl))
            if (state.controllable) {
                SeekBar(state, onSeek)
                Spacer(Modifier.height(Dimens.lg))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.xl, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSeek((state.position - 30).coerceAtLeast(0)) }, Modifier.size(56.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(AppIcons.Replay, contentDescription = "Back 30 seconds", Modifier.size(28.dp))
                            Text("30", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    FilledIconButton(
                        onClick = onPlayPause, Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) {
                        Icon(if (state.playing) AppIcons.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.playing) "Pause" else "Resume", Modifier.size(36.dp))
                    }
                    IconButton(onClick = onStop, Modifier.size(56.dp)) {
                        Icon(AppIcons.Stop, contentDescription = "Stop TV", Modifier.size(28.dp))
                    }
                }
            } else {
                ProgressLine(state, phase, Modifier.fillMaxWidth().height(4.dp).clip(androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.height(Dimens.lg))
                if (state.active) {
                    SecondaryButton("Cancel", onClick = onStop, icon = AppIcons.Stop, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        if (phase == PlaybackPhase.Error) "Open the movie and try again, or check TV settings." else "This TV session has finished.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekBar(state: CastState, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.duration.coerceAtLeast(1L).toFloat()
    val shown = dragging ?: state.position.toFloat()
    Column {
        Slider(
            value = shown.coerceIn(0f, duration),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { onSeek(it.toLong()) }; dragging = null },
            valueRange = 0f..duration,
            enabled = state.duration > 0,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Seek position" },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(shown.toLong()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(if (state.duration > 0) formatTime(state.duration) else "--:--:--", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
