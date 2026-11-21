package com.movieworld.android.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.movieworld.android.PcPlayback
import com.movieworld.android.TvNetwork
import kotlinx.coroutines.*

@Composable
internal fun WhisperSetup(address: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember(address) { mutableStateOf(false) }
    var loaded by remember(address) { mutableStateOf(false) }
    var busy by remember(address) { mutableStateOf(false) }
    var error by remember(address) { mutableStateOf<String?>(null) }
    fun load(value: Boolean? = null) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { com.movieworld.android.HomeConnection.get(context).client().whisperSettings(value) }
                enabled = result.getBoolean("auto_whisper"); loaded = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "PC unavailable. Connect to home Wi-Fi to change this setting." }
            finally { busy = false }
        }
    }
    LaunchedEffect(address) { if (address.isNotBlank()) load() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PC captions", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Automatic Whisper captions", modifier = Modifier.weight(1f))
            Switch(checked = enabled, enabled = loaded && !busy && address.isNotBlank(), onCheckedChange = { load(it) })
        }
        Text("Off by default. Favourites still download on the PC. Turn this on to generate English captions after downloading. This setting applies to the PC for all devices; existing captions are kept.", style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (address.isBlank()) Text("Save your home PC address below to manage this setting.", style = MaterialTheme.typography.bodySmall)
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = { load() }) { Text("Retry PC connection") }
        }
    }
}
