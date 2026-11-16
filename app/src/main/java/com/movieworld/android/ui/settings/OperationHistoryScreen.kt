package com.movieworld.android.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.movieworld.android.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@Composable
internal fun OperationHistoryScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var source by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading history…") }
    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                events = withContext(Dispatchers.IO) { OperationLog.rows(context) }
                val remote = withContext(Dispatchers.IO) {
                    OperationLog.sync(context)
                    val address = context.getSharedPreferences("tv", Context.MODE_PRIVATE).getString("pc-server", "").orEmpty()
                    val rows = PcPlayback(TvNetwork(context), address).operations().getJSONArray("events")
                    (0 until rows.length()).map { rows.getJSONObject(it) }
                }
                events = (events + remote).distinctBy { it.getString("event_id") }.sortedByDescending { it.getLong("timestamp") }
                status = "Phone and PC history updated"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { status = "Showing phone history · " + OperationLog.clean(failure.message ?: "PC unavailable") }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val shown = events.filter { row ->
        (source.isBlank() || row.optString("source") == source) &&
        (!errors || row.optString("level") != "info") &&
        (query.isBlank() || listOf("operation", "message", "movie_id").any { row.optString(it).contains(query, ignoreCase = true) })
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Back") }
            TextButton(enabled = !busy, onClick = { refresh() }) { Text("Refresh") }
        }
        Text("Operation history", style = MaterialTheme.typography.headlineMedium)
        Text("Downloads, captions, playback and commands", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("" to "All", "android" to "Phone", "server" to "PC").forEach { (value,label) ->
                FilterChip(selected = source == value, onClick = { source = value }, label = { Text(label) })
            }
            FilterChip(selected = errors, onClick = { errors = !errors }, label = { Text("Issues") })
        }
        OutlinedTextField(query, { query = it.take(120) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search messages or movie ID") })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (shown.isEmpty()) item { Text("No matching operations yet.") }
            items(shown, key = { it.getString("event_id") }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val label = if (row.optString("source") == "server") "PC server" else "Android app"
                        Text("$label · ${row.optString("operation")} · ${row.optString("level")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (row.optString("level") == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(row.optString("message"), style = MaterialTheme.typography.bodyMedium)
                        val movie = row.optString("movie_id").takeIf { it.isNotBlank() }?.let { " · Movie #$it" }.orEmpty()
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(row.getLong("timestamp"))) + movie,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("Latest 300 phone and 200 PC events. PC keeps 5,000 events; phone keeps 2,000. Older PC history is available on the PC’s Operation logs page. Logging begins with this update.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
