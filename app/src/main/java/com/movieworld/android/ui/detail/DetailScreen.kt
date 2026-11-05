package com.movieworld.android.ui.detail

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movieworld.android.CastState
import com.movieworld.android.Playback
import com.movieworld.android.ui.LocalAppGraph
import com.movieworld.android.ui.components.AppIcons
import com.movieworld.android.ui.components.bottomClearance
import com.movieworld.android.ui.components.PrimaryButton
import com.movieworld.android.ui.components.RatingBadge
import com.movieworld.android.ui.components.RemoteImage
import com.movieworld.android.ui.components.SecondaryButton
import com.movieworld.android.ui.components.SectionRow
import com.movieworld.android.ui.components.ShimmerBox
import com.movieworld.android.ui.components.StateMessage
import com.movieworld.android.ui.components.isLandscape
import com.movieworld.android.ui.components.windowSize
import com.movieworld.android.ui.playLabel
import com.movieworld.android.ui.theme.Dimens
import com.movieworld.android.ui.theme.Night
import com.movieworld.android.ui.theme.ScrimStrong
import com.movieworld.android.ui.theme.ScrimTransparent

@Composable
fun DetailScreen(movieId: String, onBack: () -> Unit, onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    val graph = LocalAppGraph.current
    val viewModel: DetailViewModel = viewModel(key = "detail-$movieId") { DetailViewModel(graph, movieId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val cast by Playback.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var pickerRestart by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val movie = state.movie
        if (uri != null && movie != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { }
            try {
                Playback.start(context, movie, state.details?.telegram, pickerRestart, uri.toString())
                error = null
            } catch (failure: Exception) { error = failure.message ?: "Cannot start TV playback" }
        }
    }
    DetailContent(
        state = state,
        inList = movieId in favourites,
        cast = cast,
        error = error,
        onBack = onBack,
        onOpen = onOpen,
        onToggleFavourite = viewModel::toggleFavourite,
        onPlay = { restart ->
            val movie = state.movie ?: return@DetailContent
            try {
                Playback.start(context, movie, state.details?.telegram, restart)
                error = null
            } catch (failure: Exception) { error = failure.message ?: "Cannot start TV playback" }
        },
        onPlayLocal = { restart -> pickerRestart = restart; picker.launch(arrayOf("video/*")) },
        onOpenTelegram = graph.openTelegram,
        contentPadding = contentPadding,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailContent(
    state: DetailUiState,
    inList: Boolean,
    cast: CastState,
    error: String?,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onToggleFavourite: () -> Unit,
    onPlay: (restart: Boolean) -> Unit,
    onPlayLocal: (restart: Boolean) -> Unit,
    onOpenTelegram: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val movie = state.movie
    val details = state.details
    val window = windowSize()
    val backdropHeight = if (window.isLandscape) window.height * 0.7f else window.width * 0.62f + 60.dp
    var localChoice by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.bottomClearance() + Dimens.xl),
        ) {
            item(key = "backdrop") {
                Box(Modifier.fillMaxWidth().height(backdropHeight)) {
                    when {
                        movie != null -> RemoteImage(details?.backdrop ?: movie.poster, null, Modifier.fillMaxSize(), iconSize = 56)
                        state.loading -> ShimmerBox(Modifier.fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ScrimStrong, ScrimTransparent), endY = 360f)))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to ScrimTransparent, 1f to Night)))
                }
            }
            if (movie == null) {
                item(key = "missing") {
                    if (!state.loading) StateMessage(Icons.Filled.Warning, "Movie details unavailable",
                        body = "This title is not in the offline catalogue.")
                }
                return@LazyColumn
            }
            item(key = "title") {
                Column(Modifier.padding(horizontal = Dimens.screenPadding)) {
                    Text(movie.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(Dimens.sm))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        movie.year?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        movie.rating?.let { RatingBadge(it, onDark = false) }
                        if (details != null && details.genres.isNotEmpty()) {
                            Text(details.genres.joinToString(" · "), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item(key = "actions") {
                val resume = state.resume
                val canStart = !cast.active
                val ownSession = cast.active && cast.movieId == movie.id
                Column(Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.lg)) {
                    if (resume >= 15 && state.watched != null) {
                        LinearProgressIndicator(
                            progress = { state.watched }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(Dimens.md))
                    }
                    PrimaryButton(
                        text = if (ownSession) cast.message else playLabel(resume),
                        onClick = { onPlay(false) }, icon = Icons.Filled.PlayArrow,
                        enabled = canStart, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Dimens.md))
                    val link = details?.telegram?.takeIf(::isTelegramLink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        if (resume >= 15) SecondaryButton("Start over", onClick = { onPlay(true) }, icon = AppIcons.Replay, enabled = canStart)
                        SecondaryButton(
                            if (inList) "In My List" else "My List", onClick = onToggleFavourite,
                            icon = if (inList) Icons.Filled.Check else Icons.Filled.Add,
                        )
                        if (link != null) SecondaryButton("Telegram", onClick = { onOpenTelegram(link) }, icon = AppIcons.OpenInNew)
                        SecondaryButton(
                            "Downloaded video", icon = AppIcons.VideoFile, enabled = canStart,
                            onClick = { if (resume >= 15) localChoice = true else onPlayLocal(false) },
                        )
                    }
                    if (details != null && link == null) {
                        Spacer(Modifier.height(Dimens.sm))
                        Text("Telegram link unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    error?.let {
                        Spacer(Modifier.height(Dimens.sm))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            details?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                item(key = "overview") { Overview(overview) }
            }
            if (details != null && details.cast.isNotEmpty()) {
                item(key = "cast") {
                    Column(Modifier.padding(top = Dimens.xl)) {
                        Text("Cast", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = Dimens.screenPadding).semantics { heading() })
                        Spacer(Modifier.height(Dimens.sm))
                        LazyRow(contentPadding = PaddingValues(horizontal = Dimens.screenPadding), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            items(details.cast) { name ->
                                Text(
                                    name, style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                        .padding(horizontal = Dimens.md, vertical = Dimens.sm),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "similar") {
                Spacer(Modifier.height(Dimens.xl))
                SectionRow("More like this", state.similar, onOpen = { onOpen(it.id) })
            }
        }
        IconButton(
            onClick = onBack,
            Modifier.statusBarsPadding().padding(Dimens.sm).size(Dimens.touchTarget)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
    }
    if (localChoice) {
        AlertDialog(
            onDismissRequest = { localChoice = false },
            title = { Text("Play downloaded video") },
            text = { Text("Choose the video file for this movie, for example one downloaded by Telegram. Resume from ${com.movieworld.android.formatTime(state.resume)} or start over?") },
            confirmButton = { TextButton(onClick = { localChoice = false; onPlayLocal(false) }) { Text("Choose and resume") } },
            dismissButton = { TextButton(onClick = { localChoice = false; onPlayLocal(true) }) { Text("Choose and start over") } },
        )
    }
}

@Composable
private fun Overview(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = Dimens.screenPadding).fillMaxWidth().animateContentSize()
            .clickable { expanded = !expanded },
    ) {
        Text(
            text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Dimens.xs))
        Text(if (expanded) "Less" else "More", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

internal fun isTelegramLink(link: String): Boolean {
    val uri = link.toUri()
    return uri.scheme == "https" && uri.host == "t.me" && uri.pathSegments.isNotEmpty()
}
