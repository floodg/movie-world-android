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
    val destination: PlaybackDestination = PlaybackDestination.TV,
)
object Playback {
    internal val mutableState = MutableStateFlow(CastState())
    val state = mutableState.asStateFlow()
    val revision = MutableStateFlow(0L)
    @Volatile internal var deviceVisible = false
    internal val pendingDevicePlay = java.util.concurrent.atomic.AtomicBoolean(false)

    fun start(context: Context, movie: Movie, telegram: String?, restart: Boolean, localUri: String? = null, subtitleUri: String? = null, subtitleDelayMs: Int = 0, fromPc: Boolean = false, destination: PlaybackDestination = PlaybackDestination.AUTO) {
        if (state.value.active) return
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = "start"
            putExtra("movie", movie.id)
            putExtra("title", movie.title)
            putExtra("telegram", telegram)
            putExtra("restart", restart)
            putExtra("local", localUri)
            putExtra("subtitle", subtitleUri)
            putExtra("subtitle-delay", subtitleDelayMs)
            putExtra("from-pc", fromPc)
            putExtra("destination", destination.name)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    /** Called while the app is visible; never starts, replaces or downloads a movie. */
    suspend fun observePc(context: Context) {
        if (state.value.active) return
        val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
        val selected = prefs.getString("renderer-id", "").orEmpty()
        if (HomeConnection.get(context).state.value.address == null || selected.isBlank()) return
        val session = try {
            withContext(Dispatchers.IO) { HomeConnection.get(context).client().activeSession() }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { return }
        if (session == null || !session.canJoin(selected) || state.value.active) return
        androidx.core.content.ContextCompat.startForegroundService(context,
            Intent(context, PlaybackService::class.java).apply {
                action = "attach-pc"
                putExtra("job", session.id)
                putExtra("movie", session.movieId)
                putExtra("title", session.title)
            })
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
    private var pcPlayback: PcPlayback? = null
    private var wake: PowerManager.WakeLock? = null
    private var source: TelegramSource? = null
    private var currentId = ""
    private var restart = false
    private var stopped = false
    private var ownsTransport = false
    private var resumeRecovery = ResumeRecovery()
    private var localVideo: LocalVideoSession? = null
    private val mutablePlayer = MutableStateFlow<androidx.media3.common.Player?>(null)
    inner class LocalBinder : android.os.Binder() {
        val player get() = mutablePlayer.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("tv-playback", "Movie playback", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "start", "attach-pc" -> {
                if (playback?.isActive == true) return START_NOT_STICKY
                currentId = intent.getStringExtra("movie") ?: return START_NOT_STICKY
                OperationLog.record(this, "playback", "Playback requested", movieId = currentId)
                restart = intent.getBooleanExtra("restart", false)
                stopped = false
                ownsTransport = false
                resumeRecovery = ResumeRecovery()
                pcPlayback = null
                source = null
                val requested = intent.getStringExtra("destination")?.let { PlaybackDestination.valueOf(it) } ?: PlaybackDestination.TV
                val initialDestination = if (intent.action == "attach-pc" || intent.getBooleanExtra("from-pc", false)) PlaybackDestination.TV else requested
                Playback.pendingDevicePlay.set(initialDestination != PlaybackDestination.TV)
                Playback.mutableState.value = CastState(true, currentId, intent.getStringExtra("title").orEmpty(), "Preparing playback…", destination = initialDestination)
                foreground(intent.action != "attach-pc")
                playback = scope.launch {
                    try {
                        withTimeout(6 * 60 * 60 * 1000L) {
                            if (intent.action == "attach-pc") attachPc(intent)
                            else if (intent.getBooleanExtra("from-pc", false)) castFromPc(intent)
                            else {
                                val home = HomeConnection.get(this@PlaybackService)
                                val target = if (requested == PlaybackDestination.AUTO) {
                                    home.refresh(force = true)
                                    home.state.value.destination
                                } else requested
                                Playback.mutableState.value = Playback.state.value.copy(destination = target)
                                if (target == PlaybackDestination.DEVICE) playOnDevice(intent) else cast(intent)
                            }
                        }
                    } catch (error: TimeoutCancellationException) {
                        update("Playback session timed out. Open Movie World to retry.")
                    } catch (error: CancellationException) {
                        update(if (stopped) "Stopped" else "Playback session ended")
                    } catch (error: Exception) {
                        OperationLog.record(this@PlaybackService, "playback", error.message ?: "Playback failed", "error", currentId)
                        update(error.message ?: "Playback failed")
                    } finally {
                        withContext(NonCancellable) {
                            commands.withLock {
                                try { if (ownsTransport) renderer?.let { network?.action(it, "Stop") } } catch (_: Exception) { }
                                if (stopped) {
                                    try { pcPlayback?.control("stop") } catch (_: Exception) { }
                                }
                                pcPlayback = null
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
                    if (!Playback.state.value.active || !Playback.state.value.controllable) return@withLock
                    if (localVideo != null) {
                        withContext(Dispatchers.Main.immediate) {
                            localVideo?.player?.let { player ->
                                when (intent.action) {
                                    "pause" -> player.pause()
                                    "resume" -> player.play()
                                    "seek" -> player.seekTo(intent.getLongExtra("seconds", 0).coerceAtLeast(0) * 1000)
                                }
                            }
                        }
                        saveLocalProgress(flush = true)
                        foreground(false)
                        return@withLock
                    }
                    pcPlayback?.let { pc ->
                        try {
                            pc.control(intent.action!!, intent.getLongExtra("seconds", 0).coerceAtLeast(0))
                            Playback.mutableState.value = pc.session(pc.status()).castState(Playback.state.value).copy(warning = null)
                        }
                        catch (error: Exception) {
                            Playback.mutableState.value = Playback.state.value.copy(warning = error.message ?: "PC control failed.")
                        }
                        foreground(false)
                        return@withLock
                    }
                    val tv = renderer ?: return@withLock
                    val net = network ?: return@withLock
                    try {
                        when (intent.action) {
                            "pause" -> {
                                net.action(tv, "Pause")
                                resumeRecovery.pause(android.os.SystemClock.elapsedRealtime())
                            }
                            "resume" -> {
                                resumeRecovery.resume(android.os.SystemClock.elapsedRealtime(), Playback.state.value.position)
                                net.action(tv, "Play", mapOf("Speed" to "1"))
                            }
                            "seek" -> {
                                val seconds = intent.getLongExtra("seconds", 0).coerceAtLeast(0)
                                require(Playback.state.value.duration == 0L || seconds < Playback.state.value.duration) {
                                    "Choose a position before the end of the movie."
                                }
                                net.seek(tv, seconds)
                            }
                        }
                        OperationLog.record(this@PlaybackService, "playback", "TV control ${intent.action} accepted at ${formatTime(Playback.state.value.position)}", movieId = currentId)
                        if (intent.action == "pause" || intent.action == "resume") {
                            val playing = intent.action == "resume"
                            Playback.mutableState.value = Playback.state.value.copy(
                                playing = playing, warning = null,
                                message = if (playing) "Playing on ${tv.name}" else "Paused on ${tv.name}")
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        OperationLog.record(this@PlaybackService, "playback", "TV control ${intent.action} failed: ${error.message}", "error", currentId)
                        Playback.mutableState.value = Playback.state.value.copy(warning = error.message ?: "The TV refused this control.")
                    }
                    foreground(false)
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
        val state = Playback.state.value
        val detail = buildString {
            append(state.message)
            if (state.controllable) {
                append(" · ").append(formatTime(state.position))
                if (state.duration > 0) append(" / ").append(formatTime(state.duration))
            }
            state.warning?.let { append("\n").append(it) }
        }
        val builder = NotificationCompat.Builder(this, "tv-playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(state.title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true)
        if (state.controllable && (state.destination != PlaybackDestination.DEVICE || state.playing)) {
            val action = if (state.playing) "pause" else "resume"
            val control = PendingIntent.getService(this, if (state.playing) 2 else 3,
                Intent(this, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(
                if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.playing) "Pause" else "Resume", control)
        }
        val notification = builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop).build()
        var type = 0
        if (Build.VERSION.SDK_INT >= 29) {
            type = if (downloading) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else if (state.destination == PlaybackDestination.DEVICE) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        ServiceCompat.startForeground(this, 42, notification, type)
    }

    private fun update(message: String) {
        if (message != Playback.state.value.message) OperationLog.record(this, "playback", message, movieId = currentId)
        Playback.mutableState.value = Playback.state.value.copy(message = message)
    }

    private val localProgressLock = Mutex()
    private suspend fun saveLocalProgress(flush: Boolean = false) = localProgressLock.withLock {
        val state = withContext(Dispatchers.Main.immediate) {
            localVideo?.snapshot(Playback.state.value)
        } ?: return@withLock
        Playback.mutableState.value = state
        if (!state.controllable) return@withLock
        val db = PlaybackDatabase.get(this).access()
        db.save(PlaybackDatabase.Progress().apply {
            movieId = currentId
            position = state.position
            duration = state.duration
            updated = maxOf(System.currentTimeMillis(), (db.find(currentId)?.updated ?: 0) + 1)
            finished = state.message == "Playback ended" || (duration > 0 && position >= duration - 30)
            dirty = true
        })
        Playback.revision.value += 1
        SyncHooks.schedule(if (flush || !state.playing) 0 else 1500)
    }

    private suspend fun playOnDevice(intent: Intent) {
        val db = PlaybackDatabase.get(this).access()
        val previous = db.find(currentId)
        val saved = if (!restart && previous != null && !previous.finished &&
            (previous.duration == 0L || previous.position < previous.duration - 30)) previous.position else 0
        Playback.mutableState.value = Playback.state.value.copy(position = saved)
        var captions: File? = null
        var played = false
        try {
            val local = intent.getStringExtra("local")
            val uri = if (local != null) {
                android.net.Uri.parse(local).also {
                    require(it.scheme == "content") { "Choose a video from the Android file picker." }
                    require(contentResolver.getType(it)?.startsWith("video/") != false) { "Choose a video file." }
                }
            } else {
                val tg = TelegramSource.get(this).also { source = it; it.start() }
                update("Finding movie in Telegram…")
                val resolved = tg.resolve(intent.getStringExtra("telegram") ?: error("Telegram link unavailable"),
                    intent.getStringExtra("title"))
                tg.protectMovie(resolved)
                val media = tg.prepare(resolved)
                val file = if (media.file.local.isDownloadingCompleted && File(media.file.local.path).isFile) {
                    File(media.file.local.path)
                } else {
                    update("Downloading movie to this device…")
                    foreground(true)
                    tg.download(media) { bytes, total ->
                        val suffix = if (total > 0) "${bytes * 100 / total}%" else "${bytes / (1024 * 1024)} MB"
                        update("Downloading $suffix")
                        foreground(true)
                    }
                }
                check(file.length() > 0) { "The video is empty." }
                android.net.Uri.fromFile(file)
            }
            val srt = pcSubtitles(intent)
            if (srt != null) {
                captions = File.createTempFile("device-subtitles-", ".srt", cacheDir).apply { writeText(srt, Charsets.UTF_8) }
            }
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.Main.immediate) {
                localVideo = LocalVideoSession(this@PlaybackService, uri, captions,
                    Playback.state.value.title, saved) {
                    scope.launch { saveLocalProgress(flush = true) }
                }
                mutablePlayer.value = localVideo!!.player
            }
            foreground(false)
            while (true) {
                currentCoroutineContext().ensureActive()
                saveLocalProgress()
                if (Playback.state.value.controllable) {
                    played = true
                    source?.recordMoviePlayback()
                }
                foreground(false)
                if (localVideo!!.ended.isCompleted) break
                delay(1000)
            }
        } finally {
            withContext(NonCancellable) {
                saveLocalProgress(flush = true)
                withContext(Dispatchers.Main.immediate) {
                    mutablePlayer.value = null
                    localVideo?.close()
                    localVideo = null
                }
                source?.releaseMovie(played)
            }
            captions?.delete()
        }
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
        val subtitleUri = intent.getStringExtra("subtitle")
        val subtitleKey = subtitleSourceKey(currentId, intent.getStringExtra("telegram"))
        val cachedSubtitles = subtitleCache(this)
        val hasCachedSubtitle = cachedSubtitles.get(subtitleKey)?.enabled == true
        if (local == null) {
            if (HomeConnection.get(this).state.value.address != null) {
                update("Checking PC for a saved movie...")
                val pc = try {
                    HomeConnection.get(this).client().takeIf { it.cached(currentId, tv.id) }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
                currentCoroutineContext().ensureActive()
                if (pc != null) {
                    val job = pc.start(currentId, tv.id, restart, previous, subtitleSrt = pcSubtitles(intent))
                    if (job != null) {
                        // Use the PC source even when subtitles have been selected on the phone.
                        pcPlayback = pc
                        monitorPc(pc, job)
                        return
                    }
                }
            }
        }
        var subtitleFile: File? = null
        var temporary: File? = null
        var played = false
        try {
            if (subtitleUri != null) {
                val uri = android.net.Uri.parse(subtitleUri)
                require(uri.scheme == "content") { "Choose subtitles from the Android file picker." }
                val captions = contentResolver.openInputStream(uri)?.use {
                    SrtSubtitles.prepare(it, intent.getIntExtra("subtitle-delay", 0))
                } ?: error("Cannot open the selected subtitles. Choose the SRT again.")
                subtitleFile = File.createTempFile("tv-subtitles-", ".srt", cacheDir)
                subtitleFile.writeText(captions, Charsets.UTF_8)
            }
            if (subtitleUri == null && hasCachedSubtitle) {
                subtitleFile = File.createTempFile("tv-subtitles-", ".srt", cacheDir)
                check(cachedSubtitles.prepareForPlayback(subtitleKey, subtitleFile)) {
                    "Cached subtitles were cleared. Find or import them again before playing."
                }
            }
            val media: Pair<File, String> = if (local != null) {
                update("Preparing selected video...")
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
                update("Finding movie in Telegram...")
                val resolved = telegram.resolve(intent.getStringExtra("telegram") ?: error("Telegram link unavailable"), intent.getStringExtra("title"))
                telegram.protectMovie(resolved)
                val movie = telegram.prepare(resolved)

                update("Downloading movie to phone...")
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
            mediaServer = MediaServer(net.address, media.first, media.second, subtitleFile) { detail ->
                OperationLog.record(this@PlaybackService, "media", detail, movieId = currentId)
            }
            update("Starting ${tv.name}...")
            foreground(false)
            ownsTransport = true
            net.play(tv, mediaServer!!.url, Playback.state.value.title, media.second, mediaServer!!.subtitleUrl)
            var confirmed = false
            var failures = 0
            var stopping = 0
            val started = android.os.SystemClock.elapsedRealtime()
            var pendingResume = saved
            var resumeAttempts = 0
            var lastTransport = ""
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!confirmed && android.os.SystemClock.elapsedRealtime() - started > 30_000)
                    error("The TV did not start playback. It may not support this video format.")
                val position = try {
                    commands.withLock {
                        check(!resumeRecovery.expired(android.os.SystemClock.elapsedRealtime())) {
                            "The TV could not resume playback. Your saved position is kept."
                        }
                        net.position(tv).also { sample ->
                            check(!resumeRecovery.observe(sample.state,
                                android.os.SystemClock.elapsedRealtime(), seeking = pendingResume > 0)) {
                                "The TV could not resume playback. Your saved position is kept."
                            }
                        }
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: IllegalStateException) {
                    if (resumeRecovery.expired(android.os.SystemClock.elapsedRealtime())) throw error
                    if (++failures >= 3) throw IllegalStateException(
                        "Lost contact with the TV. Check Wi-Fi and TV playback. ${error.message}", error)
                    delay(2_000)
                    continue
                }
                catch (error: Exception) {
                    if (++failures >= 3) throw IllegalStateException(
                        "Lost contact with the TV. Check Wi-Fi and TV playback. ${error.message}", error)
                    delay(2_000)
                    continue
                }
                failures = 0
                if (position.state != lastTransport) {
                    OperationLog.record(this@PlaybackService, "playback",
                        "TV transport $lastTransport -> ${position.state} at ${formatTime(position.seconds)} / ${formatTime(position.duration)}",
                        movieId = currentId)
                    lastTransport = position.state
                }
                if (position.state == "PLAYING" || position.state == "PAUSED_PLAYBACK") {
                    source?.recordMoviePlayback()
                    played = true
                    confirmed = true
                    stopping = 0
                    if (pendingResume > 0) {
                        if (position.seconds >= pendingResume - 5 && resumeAttempts > 0) {
                            pendingResume = 0
                        } else {
                            check(resumeAttempts < 3) {
                                "The TV could not resume this video. Your saved position is kept. Try Start over on TV."
                            }
                            update("Resuming at ${formatTime(pendingResume)}...")
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
                    val decision = commands.withLock {
                        resumeRecovery.stopped(android.os.SystemClock.elapsedRealtime()).also { decision ->
                            if (decision == ResumeRecovery.Decision.RELOAD) {
                                update("Restoring paused playback...")
                                net.play(tv, mediaServer!!.url, Playback.state.value.title, media.second, mediaServer!!.subtitleUrl)
                                pendingResume = resumeRecovery.position
                                resumeAttempts = 0
                            }
                        }
                    }
                    if (decision != ResumeRecovery.Decision.STOP) {
                        stopping = 0
                    } else if (++stopping >= 2) {
                        val state = Playback.state.value
                        val finished = state.duration > 0 && state.position >= state.duration - 30
                        update(if (finished) "Playback ended" else "Stopped on the TV. Your saved position is kept.")
                        break
                    }
                }
                delay(2_000)
            }
        } finally {
            subtitleFile?.delete()
            temporary?.delete()
            withContext(NonCancellable) { source?.releaseMovie(played) }
        }
    }

    /** Send only the small SRT; video and subtitle serving remain on the PC. */
    private fun pcSubtitles(intent: Intent): String? {
        val imported = intent.getStringExtra("subtitle")
        val srt = if (imported != null) {
            val uri = android.net.Uri.parse(imported)
            require(uri.scheme == "content") { "Choose subtitles from the Android file picker." }
            contentResolver.openInputStream(uri)?.use {
                SrtSubtitles.prepare(it, intent.getIntExtra("subtitle-delay", 0))
            } ?: error("Cannot open the selected subtitles. Choose the SRT again.")
        } else {
            val temporary = File.createTempFile("pc-subtitles-", ".srt", cacheDir)
            try {
                val key = subtitleSourceKey(currentId, intent.getStringExtra("telegram"))
                if (subtitleCache(this).prepareForPlayback(key, temporary)) temporary.readText(Charsets.UTF_8) else null
            } finally { temporary.delete() }
        }
        if (srt != null && Playback.state.value.destination == PlaybackDestination.TV) update("Sending subtitles to PC...")
        return srt
    }

    private suspend fun castFromPc(intent: Intent) {
        val prefs = getSharedPreferences("tv", MODE_PRIVATE)
        val selected = prefs.getString("renderer-id", "").orEmpty()
        check(selected.isNotBlank()) { "Choose a TV in TV settings first." }
        HomeConnection.get(this).refresh()
        val pc = HomeConnection.get(this).client()
        update("Checking PC and TV...")
        pc.prepare(currentId, selected)
        wake = getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "MovieWorld:PcUpload").apply { acquire(6 * 60 * 60 * 1000L) }
        var uploaded = false
        try {
            if (!pc.hasMovie(currentId)) {
                update("Checking phone for a downloaded video...")
                val coroutine = currentCoroutineContext()
                val progress: (Long, Long) -> Unit = { sent, total ->
                    coroutine.ensureActive()
                    val percent = (sent * 100 / total).toInt()
                    if (Playback.state.value.message != "Uploading to PC... $percent%") {
                        update("Uploading to PC... $percent%")
                        foreground(true)
                    }
                }
                val local = getSharedPreferences("movie-local-videos", MODE_PRIVATE).getString(currentId, null)
                val uri = local?.let { android.net.Uri.parse(it) }?.takeIf { candidate ->
                    candidate.scheme == "content" && try {
                        contentResolver.openAssetFileDescriptor(candidate, "r")?.use { true } == true
                    } catch (_: java.io.FileNotFoundException) { false }
                    catch (_: SecurityException) { false }
                }
                if (local != null && uri == null) {
                    getSharedPreferences("movie-local-videos", MODE_PRIVATE).edit().remove(currentId).apply()
                }
                // Only inspect completed downloads; never download a phone copy for this action.
                var phoneFile: File? = null
                var mime = "video/mp4"
                val telegram = intent.getStringExtra("telegram")
                if (uri == null && telegram != null) {
                    val tg = TelegramSource.get(this)
                    try {
                        tg.start()
                        val media = tg.resolve(telegram, intent.getStringExtra("title"))
                        tg.protectMovie(media)
                        source = tg
                        val file = File(media.file.local.path)
                        if (media.file.local.isDownloadingCompleted && file.isFile) {
                            phoneFile = file
                            mime = media.mime
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { /* The PC can download without phone Telegram access. */ }
                }
                try {
                    if (uri != null) {
                        val descriptor = contentResolver.openAssetFileDescriptor(uri, "r")
                            ?: error("The selected phone video is unavailable. Select it again under Downloaded video.")
                        descriptor.use {
                            var name = "movie.mp4"
                            var size = it.length
                            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME,
                                android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    name = cursor.getString(0) ?: name
                                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                                }
                            }
                            it.createInputStream().use { input -> pc.upload(currentId, name, size, input, progress) }
                        }
                        uploaded = true
                    } else if (phoneFile != null) {
                        val ext = when (mime) {
                            "video/x-matroska" -> "mkv"
                            "video/webm" -> "webm"
                            "video/x-msvideo" -> "avi"
                            else -> phoneFile.extension.takeIf { it.lowercase() in setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts") } ?: "mp4"
                        }
                        phoneFile.inputStream().use { pc.upload(currentId, "movie.$ext", phoneFile.length(), it, progress) }
                        uploaded = true
                    }
                } catch (error: PcPlaybackError) {
                    if (error.code != "already_cached") throw error
                }
            }
            currentCoroutineContext().ensureActive()
            update("Starting playback from PC...")
            val job = pc.start(currentId, selected, restart, PlaybackDatabase.get(this).access().find(currentId), cachedOnly = false, subtitleSrt = pcSubtitles(intent))
                ?: error("The PC could not start this movie. Retry Play from PC.")
            pcPlayback = pc
            monitorPc(pc, job)
        } finally {
            withContext(NonCancellable) { source?.releaseMovie(uploaded) }
        }
    }

    private suspend fun attachPc(intent: Intent) {
        val prefs = getSharedPreferences("tv", MODE_PRIVATE)
        val pc = HomeConnection.get(this).client()
        val job = pc.attach(intent.getStringExtra("job").orEmpty(),
            prefs.getString("renderer-id", "").orEmpty())
        if (job == null) { update("Playback ended"); return }
        pcPlayback = pc
        monitorPc(pc, job)
    }

    private suspend fun monitorPc(pc: PcPlayback, initial: org.json.JSONObject) {
        foreground(false)
        var job = initial
        var failures = 0
        val db = PlaybackDatabase.get(this).access()
        while (true) {
            currentCoroutineContext().ensureActive()
            val session = pc.session(job)
            val shared = session.castState(Playback.state.value).copy(warning = null)
            Playback.mutableState.value = shared
            val seconds = session.position
            // Keep final progress too, including completion reported by another remote.
            if (seconds != null && seconds >= 0 && (shared.controllable || !session.active)) {
                db.save(PlaybackDatabase.Progress().apply {
                    movieId = currentId
                    position = seconds
                    duration = shared.duration
                    updated = maxOf(System.currentTimeMillis(), (db.find(currentId)?.updated ?: 0) + 1)
                    finished = session.state == "ended" || (duration > 0 && seconds >= duration - 30)
                    dirty = true
                })
                Playback.revision.value += 1
                SyncHooks.schedule(if (shared.playing) 1500 else 0)
            }
            if (!session.active) return
            foreground(false)
            // A failed read never replaces the last known position or writes stale progress.
            do {
                delay(2000)
                try {
                    job = commands.withLock { pc.status() }
                    failures = 0
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    Playback.mutableState.value = Playback.state.value.copy(controllable = false,
                        warning = "Reconnecting to PC...")
                    foreground(false)
                    if (++failures >= 3) error("Lost contact with the PC. It may still be playing on the TV.")
                }
            } while (failures > 0)
        }
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
