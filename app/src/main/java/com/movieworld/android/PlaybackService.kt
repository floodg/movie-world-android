package com.movieworld.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class CastState(
    val active: Boolean = false,
    val movieId: String? = null,
    val title: String = "",
    val message: String = "",
    val position: Long = 0,
    val duration: Long = 0,
    val playing: Boolean = false,
    val controllable: Boolean = false,
    val warning: String? = null,
)
object Playback {
    internal val mutableState = MutableStateFlow(CastState())
    val state = mutableState.asStateFlow()
    val revision = MutableStateFlow(0L)

    fun start(context: Context, movie: Movie, telegram: String?, restart: Boolean, localUri: String? = null) {
        if (state.value.active) return
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = "start"
            putExtra("movie", movie.id)
            putExtra("title", movie.title)
            putExtra("telegram", telegram)
            putExtra("restart", restart)
            putExtra("local", localUri)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun control(context: Context, command: String, seconds: Long = 0) {
        if (!state.value.active) return
        context.startService(Intent(context, PlaybackService::class.java).apply {
            action = command
            putExtra("seconds", seconds)
        })
    }
}

class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Mutex()
    private var playback: Job? = null
    private var mediaServer: MediaServer? = null
    private var network: TvNetwork? = null
    private var renderer: Renderer? = null
    private var wake: PowerManager.WakeLock? = null
    private var source: TelegramSource? = null
    private var currentId = ""
    private var restart = false
    private var stopped = false
    private var ownsTransport = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("tv-playback", "TV playback", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "start" -> {
                if (playback?.isActive == true) return START_NOT_STICKY
                currentId = intent.getStringExtra("movie") ?: return START_NOT_STICKY
                restart = intent.getBooleanExtra("restart", false)
                stopped = false
                ownsTransport = false
                Playback.mutableState.value = CastState(true, currentId, intent.getStringExtra("title").orEmpty(), "Preparing TV playback…")
                foreground(true)
                playback = scope.launch {
                    try {
                        withTimeout(6 * 60 * 60 * 1000L) { cast(intent) }
                    } catch (error: TimeoutCancellationException) {
                        update("Playback session timed out. Open Movie World to retry.")
                    } catch (error: CancellationException) {
                        update(if (stopped) "Stopped" else "Playback session ended")
                    } catch (error: Exception) {
                        update(error.message ?: "TV playback failed")
                    } finally {
                        withContext(NonCancellable) {
                            commands.withLock {
                                try { if (ownsTransport) renderer?.let { network?.action(it, "Stop") } } catch (_: Exception) { }
                                mediaServer?.close()
                                mediaServer = null
                                renderer = null
                                network = null
                            }
                            wake?.let { if (it.isHeld) it.release() }
                            Playback.mutableState.value = Playback.state.value.copy(active = false, playing = false, controllable = false)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }
            "stop" -> {
                stopped = true
                playback?.cancel()
            }
            "pause", "resume", "seek" -> scope.launch {
                commands.withLock {
                    val tv = renderer ?: return@withLock
                    val net = network ?: return@withLock
                    try {
                        when (intent.action) {
                            "pause" -> net.action(tv, "Pause")
                            "resume" -> net.action(tv, "Play", mapOf("Speed" to "1"))
                            "seek" -> {
                                val seconds = intent.getLongExtra("seconds", 0).coerceAtLeast(0)
                                require(Playback.state.value.duration == 0L || seconds < Playback.state.value.duration) {
                                    "Choose a position before the end of the movie."
                                }
                                net.seek(tv, seconds)
                            }
                        }
                    } catch (error: Exception) {
                        Playback.mutableState.value = Playback.state.value.copy(warning = error.message ?: "The TV refused this control.")
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun foreground(downloading: Boolean) {
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "tv-playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(Playback.state.value.title)
            .setContentText(Playback.state.value.message)
            .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop).build()
        var type = 0
        if (Build.VERSION.SDK_INT >= 29) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (downloading) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, 42, notification, type)
    }

    private fun update(message: String) {
        Playback.mutableState.value = Playback.state.value.copy(message = message)
    }

    private suspend fun cast(intent: Intent) {
        val description = getSharedPreferences("tv", MODE_PRIVATE).getString("description", null)
            ?: error("Choose a TV in TV settings first.")
        val net = TvNetwork(this)
        network = net
        val tv = net.renderer(description)
        val prefs = getSharedPreferences("tv", MODE_PRIVATE)
        val expected = prefs.getString("renderer-id", null)
        check(expected == null || tv.id == expected) { "The TV at this address has changed. Choose it again in TV settings." }
        renderer = tv
        wake = getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "MovieWorld:TvPlayback").apply { acquire(6 * 60 * 60 * 1000L) }
        val db = PlaybackDatabase.get(this).access()
        val previous = db.find(currentId)
        val saved = if (!restart && previous != null &&
            (previous.duration == 0L || previous.position < previous.duration - 30)) previous.position else 0L
        val local = intent.getStringExtra("local")
        var temporary: File? = null
        try {
            val media: Pair<File, String> = if (local != null) {
                update("Preparing selected video…")
                val uri = android.net.Uri.parse(local)
                require(uri.scheme == "content") { "Choose a video from the Android file picker." }
                val mime = contentResolver.getType(uri) ?: "video/mp4"
                require(mime.startsWith("video/")) { "Choose a video file." }
                temporary = File(cacheDir, "tv-selected.part")
                contentResolver.openInputStream(uri)?.use { input ->
                    temporary!!.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var count = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            check(count <= 8L * 1024 * 1024 * 1024 && cacheDir.usableSpace > 64L * 1024 * 1024) {
                                "Not enough phone storage for this video."
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: error("Cannot open the selected video. Select it again.")
                temporary!! to mime
            } else {
                val telegram = TelegramSource.get(this).also { source = it; it.start() }
                update("Finding movie in Telegram…")
                val movie = telegram.prepare(telegram.resolve(intent.getStringExtra("telegram") ?: error("Telegram link unavailable")))

                update("Downloading movie to phone…")
                foreground(true)
                val file = telegram.download(movie) { bytes, total ->
                    val suffix = if (total > 0) "${bytes * 100 / total}%" else "${bytes / (1024 * 1024)} MB"
                    update("Downloading $suffix")
                    foreground(true)
                }
                file to movie.mime
            }
            currentCoroutineContext().ensureActive()
            check(media.first.length() > 0) { "The video is empty." }
            mediaServer = MediaServer(net.address, media.first, media.second)
            update("Starting ${tv.name}…")
            foreground(false)
            ownsTransport = true
            net.play(tv, mediaServer!!.url, Playback.state.value.title, media.second)
            var confirmed = false
            var failures = 0
            var stopping = 0
            val started = android.os.SystemClock.elapsedRealtime()
            var pendingResume = saved
            var resumeAttempts = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!confirmed && android.os.SystemClock.elapsedRealtime() - started > 30_000)
                    error("The TV did not start playback. It may not support this video format.")
                val position = try {
                    commands.withLock { net.position(tv) }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    if (++failures >= 3) throw IllegalStateException(
                        "Lost contact with the TV. Check Wi-Fi and TV playback. ${error.message}", error)
                    delay(2_000)
                    continue
                }
                failures = 0
                if (position.state == "PLAYING" || position.state == "PAUSED_PLAYBACK") {
                    confirmed = true
                    stopping = 0
                    if (pendingResume > 0) {
                        if (position.seconds >= pendingResume - 5 && resumeAttempts > 0) {
                            pendingResume = 0
                        } else {
                            check(resumeAttempts < 3) {
                                "The TV could not resume this video. Your saved position is kept. Try Start over on TV."
                            }
                            update("Resuming at ${formatTime(pendingResume)}…")
                            try {
                                commands.withLock { net.seek(tv, pendingResume) }
                            } catch (error: Exception) {
                                throw IllegalStateException(
                                    "The TV cannot seek this video. Your saved position is kept. Try Start over on TV.", error)
                            }
                            resumeAttempts++
                            delay(2_000)
                            continue
                        }
                    }
                    val seconds = position.seconds
                    val duration = position.duration.takeIf { it > 0 } ?: Playback.state.value.duration
                    if (seconds >= 0) {
                        val progress = PlaybackDatabase.Progress().apply {
                            movieId = currentId
                            this.position = seconds
                            this.duration = duration
                            updated = maxOf(System.currentTimeMillis(), (db.find(currentId)?.updated ?: 0) + 1)
                            finished = duration > 0 && seconds >= duration - 30
                            dirty = true
                        }
                        db.save(progress)
                        Playback.revision.value += 1
                        SyncHooks.schedule(if (position.state == "PLAYING") 1_500 else 0)
                    }
                    Playback.mutableState.value = Playback.state.value.copy(
                        position = seconds.coerceAtLeast(0), duration = duration,
                        playing = position.state == "PLAYING", controllable = true,
                        message = if (position.state == "PLAYING") "Playing on ${tv.name}" else "Paused on ${tv.name}")
                    foreground(false)
                } else if (confirmed && position.state in listOf("STOPPED", "NO_MEDIA_PRESENT")) {
                    if (++stopping >= 2) { update("Playback ended"); break }
                }
                delay(2_000)
            }
        } finally { temporary?.delete() }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopped = true
        update("Android ended the download session. Open Movie World to retry.")
        playback?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        CloudSync.get(this).reconcileNow()
        playback?.cancel()
        mediaServer?.close()
        wake?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }
}
