package com.movieworld.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.movieworld.android.ui.components.RemoteImage
import com.movieworld.android.ui.theme.MovieWorldTheme
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catalogue = Catalogue(applicationContext)
        val preferences = getSharedPreferences("movie-world", MODE_PRIVATE)
        setContent {
            MovieWorldTheme {
                Surface(Modifier.fillMaxSize()) {
                    var favourites by remember {
                        mutableStateOf(preferences.getStringSet("favourites", emptySet()).orEmpty().toSet())
                    }
                    MovieWorld(catalogue, favourites, { id ->
                        favourites = if (id in favourites) favourites - id else favourites + id
                        preferences.edit().putStringSet("favourites", favourites).apply()
                    }, ::openTelegram)
                }
            }
        }
    }

    private fun openTelegram(link: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(Intent(intent).setPackage("org.telegram.messenger"))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Install Telegram or a web browser to open this post.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun MovieWorld(
    catalogue: Catalogue,
    favourites: Set<String>,
    toggleFavourite: (String) -> Unit,
    openTelegram: (String) -> Unit,
) {
    val context = LocalContext.current
    var settings by rememberSaveable { mutableStateOf(false) }
    val revision by Playback.revision.collectAsState()
    var continuing by remember { mutableStateOf(emptyList<Pair<Movie, PlaybackDatabase.Progress>>()) }
    var picked by remember { mutableStateOf<Movie?>(null) }
    LaunchedEffect(revision) {
        continuing = withContext(Dispatchers.IO) {
            PlaybackDatabase.get(context).access().continuing().take(20).mapNotNull { progress ->
                catalogue.movie(progress.movieId)?.let { it to progress }
            }
        }
    }
    BackHandler(settings) { settings = false }
    if (settings) {
        TvSettings { settings = false }
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var byActor by rememberSaveable { mutableStateOf(false) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyFavourites by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var genres by remember { mutableStateOf(emptyList<Genre>()) }
    var movies by remember { mutableStateOf(emptyList<Movie>()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(catalogue) {
        genres = withContext(Dispatchers.IO) { catalogueResult { catalogue.genres() }.orEmpty() }
    }
    LaunchedEffect(query, byActor, genre, from, to) {
        loading = true
        if (query.isNotBlank()) delay(200)
        val result = withContext(Dispatchers.IO) {
            catalogueResult { catalogue.movies(query, byActor, genre, from.toIntOrNull(), to.toIntOrNull()) }
        }
        failed = result == null
        movies = result.orEmpty()
        loading = false
    }

    val selected = picked ?: movies.firstOrNull { it.id == selectedId }
    BackHandler(selected != null) { selectedId = null; picked = null }
    if (selected != null) {
        DetailScreen(selected, catalogue, selected.id in favourites, { selectedId = null; picked = null },
            { toggleFavourite(selected.id) }, openTelegram)
        return
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
        Text("Movie World", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = { settings = true }) { Text("TV settings") }
        PlaybackControls()
        if (continuing.isNotEmpty()) {
            Text("Continue Watching", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(continuing, key = { it.first.id }) { (movie, progress) ->
                    OutlinedButton(onClick = { picked = movie }) {
                        Text("${movie.title}\n${formatTime(progress.position)}")
                    }
                }
            }
        }
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (byActor) "Search by actor" else "Search by title") }
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(!byActor, { byActor = false }, { Text("Title") }) }
            item { FilterChip(byActor, { byActor = true }, { Text("Actor") }) }
            item { FilterChip(onlyFavourites, { onlyFavourites = !onlyFavourites }, { Text("Favourites") }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(from, { from = yearInput(it, from) }, Modifier.weight(1f),
                label = { Text("From year") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(to, { to = yearInput(it, to) }, Modifier.weight(1f),
                label = { Text("To year") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(genre == null, { genre = null }, { Text("All genres") }) }
            items(genres, key = { it.id }) { entry ->
                FilterChip(genre == entry.id, { genre = entry.id }, { Text(entry.name) })
            }
        }
        val shown = if (onlyFavourites) movies.filter { it.id in favourites } else movies
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            failed -> Text("Could not read the offline catalogue.")
            shown.isEmpty() -> Text("No matching movies.")
            else -> {
                Text("${shown.size} movies", style = MaterialTheme.typography.labelLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { movie ->
                        Card(Modifier.fillMaxWidth().clickable { selectedId = movie.id }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RemoteImage(movie.poster, null, Modifier.width(60.dp).height(88.dp))
                                Column(Modifier.padding(12.dp)) {
                                    Text(movie.title, style = MaterialTheme.typography.titleMedium)
                                    Text(movie.subtitle(), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    movie: Movie,
    catalogue: Catalogue,
    favourite: Boolean,
    back: () -> Unit,
    toggleFavourite: () -> Unit,
    openTelegram: (String) -> Unit,
) {
    val context = LocalContext.current
    val revision by Playback.revision.collectAsState()
    var resume by remember(movie.id) { mutableStateOf(0L) }
    LaunchedEffect(movie.id, revision) {
        resume = withContext(Dispatchers.IO) {
            val progress = PlaybackDatabase.get(context).access().find(movie.id)
            if (progress != null && (progress.duration == 0L || progress.position < progress.duration - 30))
                progress.position else 0L
        }
    }
    var details by remember(movie.id) { mutableStateOf<MovieDetails?>(null) }
    var loading by remember(movie.id) { mutableStateOf(true) }
    LaunchedEffect(movie.id) {
        details = withContext(Dispatchers.IO) { catalogueResult { catalogue.details(movie.id) } }
        loading = false
    }
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TextButton(onClick = back) { Text("Back to catalogue") } }
        item { RemoteImage(details?.backdrop ?: movie.poster, null, Modifier.fillMaxWidth().height(220.dp)) }
        item { Text(movie.title, style = MaterialTheme.typography.headlineMedium) }
        item { Text(movie.subtitle()) }
        item { OutlinedButton(onClick = toggleFavourite) { Text(if (favourite) "Remove favourite" else "Add favourite") } }
        val detail = details
        if (detail != null) {
            val link = detail.telegram?.takeIf(::isTelegramLink)
            item {
                if (link != null) Button(onClick = { openTelegram(link) }) { Text("Open in Telegram") }
                else Text("Telegram link unavailable")
            }
            item { CastActions(movie, detail.telegram, resume) }
            if (detail.genres.isNotEmpty()) item { Text(detail.genres.joinToString(" · ")) }
            detail.overview?.takeIf { it.isNotBlank() }?.let { overview -> item { Text(overview) } }
            if (detail.cast.isNotEmpty()) {
                item { Text("Cast", style = MaterialTheme.typography.titleMedium) }
                item { Text(detail.cast.joinToString(", ")) }
            }
        } else if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else item { Text("Movie details unavailable.") }
    }
}

private fun yearInput(input: String, previous: String): String =
    if (input.length <= 4 && input.all { it in '0'..'9' }) input else previous

private fun Movie.subtitle() = listOfNotNull(year, rating?.let { "IMDb $it" }).joinToString(" · ")

private fun isTelegramLink(link: String): Boolean {
    val uri = Uri.parse(link)
    return uri.scheme == "https" && uri.host == "t.me" && uri.pathSegments.isNotEmpty()
}

private inline fun <T> catalogueResult(block: () -> T): T? = try {
    block()
} catch (_: IOException) {
    null
} catch (_: android.database.sqlite.SQLiteException) {
    null
}
