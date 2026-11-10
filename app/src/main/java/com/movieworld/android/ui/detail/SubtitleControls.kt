package com.movieworld.android.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.movieworld.android.*
import com.movieworld.android.ui.settings.SubtitleSetup
import kotlinx.coroutines.*

@Composable
internal fun MovieSubtitleControls(movie: Movie, telegram: String?, enabled: Boolean, onBusy: (Boolean) -> Unit) {
    val source = subtitleSourceKey(movie.id, telegram)
    key(source) { SubtitleControlsForSource(movie, source, enabled, onBusy) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleControlsForSource(movie: Movie, source: String, enabled: Boolean, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { subtitleCache(context) }
    var selected by remember { mutableStateOf<CachedSubtitle?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SubtitleMatch>?>(null) }
    var setup by remember { mutableStateOf(false) }
    LaunchedEffect(source) { selected = withContext(Dispatchers.IO) { cache.get(source) } }
    LaunchedEffect(busy) { onBusy(busy) }
    DisposableEffect(Unit) { onDispose { onBusy(false) } }
    fun work(block: suspend () -> Unit) {
        busy = true; error = null
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Subtitles could not be loaded. Try again." }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work {
            selected = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { cache.save(source, it, "Imported SRT", "und") }
                    ?: error("Cannot read the selected subtitles.")
            }
        }
    }
    Column {
        Text("Subtitles", style = MaterialTheme.typography.titleSmall)
        val current = selected
        Text(when {
            current == null -> "Find and download subtitles once. They will be remembered for this movie."
            !current.enabled -> "Off · ${current.label} is cached for this movie"
            else -> "${current.language} · ${current.label} · ready for future plays"
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = enabled && !busy, onClick = {
                if (!SubtitleSettings(context).configured()) setup = true
                else work {
                    val found = withContext(Dispatchers.IO) {
                        val identity = Catalogue(context).subtitleIdentity(movie.id)
                            ?: SubtitleIdentity(movie.title, movie.year, null, null)
                        SubtitleProvider(context).search(identity)
                    }
                    results = found
                }
            }) { Text(if (current == null) "Find subtitles" else "Find another version") }
            TextButton(enabled = enabled && !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import SRT") }
            if (current != null) TextButton(enabled = enabled && !busy, onClick = {
                work { selected = withContext(Dispatchers.IO) { cache.configure(source, enabled = !current.enabled) } }
            }) { Text(if (current.enabled) "Turn off" else "Use cached subtitles") }
        }
        if (current?.enabled == true) {
            Text("Timing: ${current.delayMs / 1000.0}s · positive displays later", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = enabled && !busy && current.delayMs > -30000, onClick = {
                    work { selected = withContext(Dispatchers.IO) { cache.configure(source, delayMs = current.delayMs - 500) } }
                }) { Text("0.5s earlier") }
                TextButton(enabled = enabled && !busy && current.delayMs < 30000, onClick = {
                    work { selected = withContext(Dispatchers.IO) { cache.configure(source, delayMs = current.delayMs + 500) } }
                }) { Text("0.5s later") }
            }
            Text("The phone will stream the movie with these subtitles. Stop playback to change them.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (!enabled) Text("Subtitle changes are available before playback.", style = MaterialTheme.typography.bodySmall)
    }
    if (setup) AlertDialog(onDismissRequest = { setup = false }, title = { Text("Set up subtitle downloads") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { SubtitleSetup() } },
        confirmButton = { TextButton(onClick = { setup = false }) { Text("Done") } })
    results?.let { matches ->
        AlertDialog(onDismissRequest = { if (!busy) results = null }, title = { Text("Subtitles for ${movie.title}") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text("Choose the release closest to your movie file. Timing can differ between releases.")
                    if (matches.isEmpty()) Text("No matching subtitles found. Try another language in TV settings, or import an SRT.")
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    matches.forEach { match ->
                        TextButton(enabled = enabled && !busy, onClick = {
                            work {
                                selected = withContext(Dispatchers.IO) {
                                    val bytes = SubtitleProvider(context).download(match)
                                    bytes.inputStream().use { cache.save(source, it, match.label, match.language) }
                                }
                                results = null
                            }
                        }) { Text("${match.language} · ${match.label}") }
                    }
                }
            }, confirmButton = { TextButton(enabled = !busy, onClick = { results = null }) { Text("Close") } })
    }
}
