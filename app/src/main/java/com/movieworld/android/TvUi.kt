package com.movieworld.android

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
fun TvSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember { TelegramSource.get(context) }
    val telegram by source.state.collectAsState()
    val playback by Playback.state.collectAsState()
    val prefs = remember { context.getSharedPreferences("tv", Context.MODE_PRIVATE) }
    var address by rememberSaveable { mutableStateOf(prefs.getString("description", "").orEmpty()) }
    var apiId by rememberSaveable { mutableStateOf("") }
    // Credentials and sign-in challenges deliberately stay out of saved instance state.
    var apiHash by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf(emptyList<Renderer>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(prefs.getString("name", null)) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        try { withContext(Dispatchers.IO) { source.start() } }
        catch (failure: Exception) { error = failure.message ?: "Telegram could not start" }
    }
    fun runWork(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try { block() }
            catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { error = failure.message ?: "Setup failed" }
            finally { busy = false }
        }
    }
    fun save(tv: Renderer) {
        prefs.edit().putString("description", tv.description).putString("name", tv.name)
            .putString("renderer-id", tv.id).apply()
        address = tv.description
        saved = tv.name
    }
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item { Text("TV settings", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Connect this phone and the TV to the same Wi-Fi. The phone hosts the video while you watch.") }
        saved?.let { item { Text("Selected TV · $it") } }
        item {
            Button(enabled = !busy, onClick = { runWork {
                devices = withContext(Dispatchers.IO) { TvNetwork(context).discover() }
                if (devices.isEmpty()) error = "No TV found. Turn on the TV or enter its DLNA description URL below."
            } }) { Text("Find TVs") }
        }
        items(devices, key = { it.id }) { tv ->
            OutlinedButton(onClick = { save(tv) }, enabled = !busy) { Text("Use ${tv.name}") }
        }
        item { OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(),
            label = { Text("TV description URL") }, singleLine = true) }
        item {
            OutlinedButton(enabled = !busy && address.isNotBlank(), onClick = { runWork {
                val tv = withContext(Dispatchers.IO) { TvNetwork(context).renderer(address.trim()) }
                save(tv)
            } }) { Text("Check and save TV") }
        }
        item { HorizontalDivider() }
        item { Text("Telegram for TV playback", style = MaterialTheme.typography.titleLarge) }
        item { Text("Use an account that has joined the movie channel. This sign-in is separate from the Telegram app.") }
        when (telegram.step) {
            "setup" -> {
                item { Text("Enter the API credentials from my.telegram.org/apps. They are encrypted on this phone.") }
                item { OutlinedTextField(apiId, { apiId = it }, Modifier.fillMaxWidth(), label = { Text("App api_id") }, singleLine = true) }
                item { OutlinedTextField(apiHash, { apiHash = it }, Modifier.fillMaxWidth(), label = { Text("App api_hash") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation()) }
                item { Button(enabled = !busy, onClick = { runWork {
                    withContext(Dispatchers.IO) { source.configure(apiId.trim(), apiHash.trim()) }
                    apiHash = ""
                } }) { Text("Set up Telegram") } }
            }
            "phone", "code", "password" -> {
                val label = when (telegram.step) {
                    "phone" -> "Phone number with country code"
                    "code" -> "Telegram sign-in code"
                    else -> "Two-step verification password"
                }
                item { OutlinedTextField(challenge, { challenge = it }, Modifier.fillMaxWidth(), label = { Text(label) },
                    singleLine = true, visualTransformation = if (telegram.step == "phone") VisualTransformation.None else PasswordVisualTransformation()) }
                item { Button(enabled = !busy && challenge.isNotBlank(), onClick = { runWork {
                    withContext(Dispatchers.IO) { source.submit(challenge) }
                    challenge = ""
                } }) { Text("Continue sign-in") } }
            }
            "ready" -> item { Text("Telegram connected") }
            "initializing" -> item { Text("Starting Telegram…") }
            "closed" -> item { Text("Restart Movie World to reconnect Telegram.") }
            else -> item { Text("Telegram needs an additional sign-in step. Complete any account requirements in Telegram and retry.") }
        }
        telegram.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (telegram.step != "setup") item {
            TextButton(enabled = !busy && !playback.active, onClick = { runWork {
                withContext(Dispatchers.IO) { source.resetConfiguration() }
            } }) { Text("Change Telegram API credentials") }
        }
        if (Build.VERSION.SDK_INT >= 33) item {
            OutlinedButton(onClick = { notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Allow playback notifications")
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}

@Composable
fun PlaybackControls() {
    val context = LocalContext.current
    val state by Playback.state.collectAsState()
    if (state.message.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(state.message, style = MaterialTheme.typography.bodyMedium)
        state.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.controllable) {
            Text("${formatTime(state.position)} / ${formatTime(state.duration)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    Playback.control(context, if (state.playing) "pause" else "resume")
                }) { Text(if (state.playing) "Pause" else "Resume") }
                OutlinedButton(onClick = { Playback.control(context, "seek", (state.position - 30).coerceAtLeast(0)) }) { Text("−30s") }
            }
        }
        if (state.active) OutlinedButton(onClick = { Playback.control(context, "stop") }) {
            Text(if (state.controllable) "Stop TV" else "Cancel")
        }
    }
}

@Composable
fun CastActions(movie: Movie, telegram: String?, resume: Long) {
    val context = LocalContext.current
    val state by Playback.state.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var chooseFile by remember { mutableStateOf(false) }
    var restartChoice by remember { mutableStateOf(false) }
    fun play(restart: Boolean, local: String? = null) {
        try {
            Playback.start(context, movie, telegram, restart, local)
            error = null
        } catch (failure: Exception) { error = failure.message ?: "Cannot start TV playback" }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { }
            play(restartChoice, uri.toString())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !state.active, onClick = { play(false) }) {
            Text(if (resume >= 15) "Resume on TV · ${formatTime(resume)}" else "Play on TV")
        }
        if (resume >= 15) OutlinedButton(enabled = !state.active, onClick = { play(true) }) { Text("Start over on TV") }
        OutlinedButton(enabled = !state.active, onClick = { chooseFile = true }) { Text("Play downloaded video on TV") }
        if (chooseFile) {
            Text("Choose the video for this movie. You can use a file downloaded by Telegram.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { restartChoice = false; picker.launch(arrayOf("video/*")); chooseFile = false }) {
                    Text(if (resume >= 15) "Choose and resume" else "Choose video")
                }
                if (resume >= 15) TextButton(onClick = { restartChoice = true; picker.launch(arrayOf("video/*")); chooseFile = false }) {
                    Text("Choose and start over")
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.movieId == movie.id) PlaybackControls()
    }
}
