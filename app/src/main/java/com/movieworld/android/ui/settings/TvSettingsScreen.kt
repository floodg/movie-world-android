package com.movieworld.android.ui.settings

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movieworld.android.safeSetupError
import com.movieworld.android.PcPlayback
import com.movieworld.android.Playback
import com.movieworld.android.Renderer
import com.movieworld.android.TelegramSource
import com.movieworld.android.TvNetwork
import com.movieworld.android.ui.LocalAppGraph
import com.movieworld.android.ui.components.AppIcons
import com.movieworld.android.ui.components.PrimaryButton
import com.movieworld.android.ui.components.SecondaryButton
import com.movieworld.android.ui.theme.CardShape
import com.movieworld.android.ui.theme.Dimens
import com.movieworld.android.ui.theme.Success
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TvSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cloud = LocalAppGraph.current.cloud
    val account by cloud.account.collectAsStateWithLifecycle()
    val source = remember { TelegramSource.get(context) }
    val telegram by source.state.collectAsStateWithLifecycle()
    val playback by Playback.state.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("tv", Context.MODE_PRIVATE) }
    var address by rememberSaveable { mutableStateOf(prefs.getString("description", "").orEmpty()) }
    var pcAddress by rememberSaveable { mutableStateOf(prefs.getString("pc-server", "").orEmpty()) }
    var pcSaved by remember { mutableStateOf(prefs.getString("pc-server", "").orEmpty()) }
    var apiId by rememberSaveable { mutableStateOf("") }
    // Credentials and sign-in challenges deliberately stay out of saved instance state.
    var apiHash by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf(emptyList<Renderer>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(prefs.getString("name", null)) }
    var savedId by remember { mutableStateOf(prefs.getString("renderer-id", null)) }
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
            catch (failure: Exception) { error = safeSetupError(failure) }
            finally { busy = false }
        }
    }
    fun save(tv: Renderer) {
        prefs.edit {
            putString("description", tv.description).putString("name", tv.name).putString("renderer-id", tv.id)
        }
        address = tv.description
        saved = tv.name
        savedId = tv.id
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, Modifier.size(Dimens.touchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("TV settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) else Spacer(Modifier.height(2.dp))
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.screenPadding, bottom = Dimens.xxl),
            verticalArrangement = Arrangement.spacedBy(Dimens.lg),
        ) {
            item(key = "tv") {
                SettingsCard(
                    title = "Your TV",
                    icon = AppIcons.Tv,
                    connected = saved != null,
                    status = saved?.let { "Selected · $it" } ?: "Not set up",
                ) {
                    Text("Connect this phone and the TV to the same Wi-Fi. The phone hosts the video unless your home PC already has a cached copy.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Dimens.lg))
                    PrimaryButton("Find TVs", icon = Icons.Filled.Search, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        runWork {
                            devices = withContext(Dispatchers.IO) { TvNetwork(context).discover() }
                            if (devices.isEmpty()) error = "No TV found. Turn on the TV or enter its DLNA description URL below."
                        }
                    })
                    if (devices.isNotEmpty()) {
                        Spacer(Modifier.height(Dimens.md))
                        devices.forEach { tv -> DeviceRow(tv, selected = tv.id == savedId, enabled = !busy, onSelect = { save(tv) }) }
                    }
                    Spacer(Modifier.height(Dimens.lg))
                    OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(),
                        label = { Text("TV description URL") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                    Spacer(Modifier.height(Dimens.sm))
                    SecondaryButton("Check and save TV", enabled = !busy && address.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                        runWork {
                            val tv = withContext(Dispatchers.IO) { TvNetwork(context).renderer(address.trim()) }
                            save(tv)
                        }
                    })
                }
            }
            item(key = "subtitles") {
                SubtitleSetup()
            }
            item(key = "pc") {
                SettingsCard(title = "Home PC", icon = AppIcons.Tv,
                    connected = pcSaved.isNotBlank(), status = if (pcSaved.isBlank()) "Optional" else pcSaved) {
                    Text("Before downloading, check your PC for a saved copy. If found, the PC streams to the TV and saves your progress. Otherwise, this phone downloads and streams as usual.")
                    Spacer(Modifier.height(Dimens.md))
                    OutlinedTextField(pcAddress, { pcAddress = it }, Modifier.fillMaxWidth(),
                        label = { Text("PC server address") }, placeholder = { Text("http://192.168.1.10") },
                        singleLine = true)
                    Spacer(Modifier.height(Dimens.sm))
                    SecondaryButton("Check and save PC", enabled = !busy && !playback.active && pcAddress.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(), onClick = {
                            runWork {
                                val value = pcAddress.trim().trimEnd('/')
                                withContext(Dispatchers.IO) { PcPlayback(TvNetwork(context), value).check() }
                                prefs.edit { putString("pc-server", value) }
                                pcAddress = value
                                pcSaved = value
                            }
                        })
                    if (pcSaved.isNotBlank()) TextButton(enabled = !busy && !playback.active, onClick = {
                        prefs.edit { remove("pc-server") }
                        pcSaved = ""
                        pcAddress = ""
                    }) { Text("Use phone only") }
                }
            }
            item(key = "account") {
                SettingsCard(title = "Personal data sync", icon = AppIcons.Bookmark,
                    connected = account.signedIn, status = account.status) {
                    Text("Favourites, resume points and watch history sync through JSON files in your Telegram Saved Messages. Connect Telegram below. Your PC/browser still uses Supabase and does not receive these changes.")
                    Text("Files use ordinary Telegram cloud storage. Credentials, downloads and TV settings are excluded.", style = MaterialTheme.typography.bodySmall)
                    if (account.lastSync > 0) Text("Last sync: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(account.lastSync))}")
                    if (account.pending) Text("Changes pending sync")
                    SecondaryButton("Sync now", enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { cloud.reconcileNow() })
                    Spacer(Modifier.height(Dimens.lg))
                    SecondaryButton("Sign out of Telegram", enabled = !busy && account.signedIn && !playback.active,
                        modifier = Modifier.fillMaxWidth(), onClick = { runWork { source.signOutAccount() } })
                }
            }
            item(key = "telegram") {
                SettingsCard(
                    title = "Telegram",
                    icon = AppIcons.OpenInNew,
                    connected = telegram.step == "ready",
                    status = when (telegram.step) {
                        "ready" -> "Connected"
                        "joining" -> "Joining movie channel…"
                        "channel_pending" -> "Waiting for channel approval"
                        "channel_error" -> "Channel setup incomplete"
                        "setup" -> "Not set up"
                        "initializing" -> "Starting…"
                        "closed" -> "Closed"
                        else -> "Sign-in in progress"
                    },
                ) {
                    Text("Sign in to Telegram and Movie World will automatically join the movie channel using your account. This sign-in is separate from the Telegram app.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Dimens.lg))
                    StepIndicator(telegram.step)
                    Spacer(Modifier.height(Dimens.lg))
                    when (telegram.step) {
                        "setup" -> {
                            Text("Enter the API credentials from my.telegram.org/apps. They are encrypted on this phone.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(Dimens.sm))
                            OutlinedTextField(apiId, { apiId = it }, Modifier.fillMaxWidth(), label = { Text("App api_id") },
                                singleLine = true, shape = MaterialTheme.shapes.medium)
                            Spacer(Modifier.height(Dimens.sm))
                            OutlinedTextField(apiHash, { apiHash = it }, Modifier.fillMaxWidth(), label = { Text("App api_hash") },
                                singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.medium)
                            Spacer(Modifier.height(Dimens.md))
                            PrimaryButton("Set up Telegram", enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                                runWork {
                                    withContext(Dispatchers.IO) { source.configure(apiId.trim(), apiHash.trim()) }
                                    apiHash = ""
                                }
                            })
                        }
                        "phone", "code", "password" -> {
                            val label = when (telegram.step) {
                                "phone" -> "Phone number with country code"
                                "code" -> "Telegram sign-in code"
                                else -> "Two-step verification password"
                            }
                            OutlinedTextField(challenge, { challenge = it }, Modifier.fillMaxWidth(), label = { Text(label) },
                                singleLine = true, shape = MaterialTheme.shapes.medium,
                                visualTransformation = if (telegram.step == "phone") VisualTransformation.None else PasswordVisualTransformation())
                            Spacer(Modifier.height(Dimens.md))
                            PrimaryButton("Continue sign-in", enabled = !busy && challenge.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                                runWork {
                                    withContext(Dispatchers.IO) { source.submit(challenge) }
                                    challenge = ""
                                }
                            })
                        }
                        "ready" -> Text("Telegram is connected and the movie channel is joined. Ready for TV playback.", style = MaterialTheme.typography.bodyMedium)
                        "joining" -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(Dimens.sm))
                            Text("Joining the movie channel…", style = MaterialTheme.typography.bodyMedium)
                        }
                        "channel_error", "channel_pending" -> SecondaryButton(
                            "Retry channel connection", enabled = !busy, modifier = Modifier.fillMaxWidth(),
                            onClick = { runWork { withContext(Dispatchers.IO) { source.joinMovieChannel() } } },
                        )
                        "initializing" -> Text("Starting Telegram…", style = MaterialTheme.typography.bodyMedium)
                        "closed" -> Text("Restart Movie World to reconnect Telegram.", style = MaterialTheme.typography.bodyMedium)
                        else -> Text("Telegram needs an additional sign-in step. Complete any account requirements in Telegram and retry.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    telegram.error?.let {
                        Spacer(Modifier.height(Dimens.sm))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (telegram.step != "setup") {
                        Spacer(Modifier.height(Dimens.sm))
                        TextButton(enabled = !busy && !playback.active && telegram.step != "joining", contentPadding = PaddingValues(0.dp), onClick = {
                            runWork { withContext(Dispatchers.IO) { source.resetConfiguration() } }
                        }) { Text("Change Telegram API credentials") }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) item(key = "notifications") {
                SettingsCard(title = "Notifications", icon = Icons.Filled.Notifications, connected = null, status = null) {
                    Text("A notification shows TV playback progress and offers Stop while the app is in the background.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Dimens.md))
                    SecondaryButton("Allow playback notifications", modifier = Modifier.fillMaxWidth(),
                        onClick = { notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) })
                }
            }
            error?.let { message ->
                item(key = "error") { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    connected: Boolean?,
    status: String?,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(), shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Dimens.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    if (status != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (connected != null) {
                                Box(Modifier.size(8.dp).background(if (connected) Success else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(status, style = MaterialTheme.typography.labelMedium,
                                color = if (connected == true) Success else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.md))
            content()
        }
    }
}

@Composable
private fun DeviceRow(tv: Renderer, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent, MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = Dimens.md, vertical = Dimens.sm)
            .semantics { contentDescription = if (selected) "${tv.name}, selected" else "Use ${tv.name}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Dimens.md))
        Text(tv.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

private val TelegramSteps = listOf("setup" to "Credentials", "phone" to "Phone", "code" to "Code", "password" to "Password", "joining" to "Channel", "ready" to "Ready")

@Composable
private fun StepIndicator(step: String) {
    val displayStep = if (step in listOf("channel_pending", "channel_error")) "joining" else step
    val index = TelegramSteps.indexOfFirst { it.first == displayStep }.let { if (it < 0) 1 else it }
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "Telegram setup step ${index + 1} of ${TelegramSteps.size}" },
        verticalAlignment = Alignment.CenterVertically) {
        TelegramSteps.forEachIndexed { position, (_, label) ->
            val done = position < index
            val current = position == index
            val color = when {
                done -> Success
                current -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(22.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (position < TelegramSteps.lastIndex) {
                Box(Modifier.weight(1f).height(2.dp).padding(horizontal = 4.dp)
                    .background(if (position < index) Success else MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
    }
}
