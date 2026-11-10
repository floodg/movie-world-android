package com.movieworld.android.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.movieworld.android.SubtitleProvider
import com.movieworld.android.SubtitleSettings
import kotlinx.coroutines.*

@Composable
internal fun SubtitleSetup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SubtitleSettings(context) }
    var configured by remember { mutableStateOf(settings.configured()) }
    var apiKey by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf(settings.language()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Subtitle downloads", style = MaterialTheme.typography.titleMedium)
        Text("Create an OpenSubtitles.com account, then create an API key in API Consumers. Download limits follow your account. Search sends the movie identifier (or title/year) and language to OpenSubtitles.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.opensubtitles.com/en/consumers")))
        }) { Text("Open OpenSubtitles setup") }
        if (configured) Text("API key saved on this phone", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(), label = { Text(if (configured) "Replacement API key (optional)" else "API key") })
        OutlinedTextField(language, { language = it.lowercase().trim() }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Subtitle language (en, es, fr…)") })
        Text("Optional account sign-in for your account's download allowance. Your password is not saved.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("OpenSubtitles username") })
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(), label = { Text("OpenSubtitles password") })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(enabled = !busy && (configured || apiKey.isNotBlank()), onClick = {
            busy = true; failed = false; status = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        require(username.isBlank() == password.isBlank()) { "Enter both username and password, or leave both blank." }
                        settings.save(apiKey.trim().ifBlank { settings.apiKey() }, language)
                        val provider = SubtitleProvider(context)
                        if (username.isNotBlank()) provider.signIn(username.trim(), password)
                        provider.test()
                    }
                    apiKey = ""; password = ""; configured = true
                    status = "Connected. Open a movie and tap Find subtitles."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    failed = true; configured = settings.configured()
                    status = failure.message ?: "OpenSubtitles connection failed."
                } finally { busy = false; password = "" }
            }
        }) { Text("Save and test") }
        if (configured) TextButton(enabled = !busy, onClick = {
            settings.clear(); configured = false; status = "OpenSubtitles disconnected. Cached subtitles are kept."
        }) { Text("Disconnect OpenSubtitles") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
