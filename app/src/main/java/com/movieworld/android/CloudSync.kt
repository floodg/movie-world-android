package com.movieworld.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.util.UUID

data class AccountState(
    val configured: Boolean = true, val signedIn: Boolean = false,
    val status: String = "Connect Telegram in settings", val lastSync: Long = 0,
    val pending: Boolean = false,
)

/** Room is authoritative offline. Telegram is an eventually consistent snapshot transport. */
class CloudSync private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val prefs = app.getSharedPreferences("telegram-sync", Context.MODE_PRIVATE)
    private val device = prefs.getString("device", null) ?: UUID.randomUUID().toString().also {
        check(prefs.edit().putString("device", it).commit())
    }
    private val source = TelegramSource.get(app)
    private val store: PersonalDataStore = TelegramPersonalStore(app, source)
    private val db = PlaybackDatabase.get(app)
    private val access = db.access()
    private val repository = PersonalDataRepository(db, device)
    private val mutableAccount = MutableStateFlow(AccountState())
    val account = mutableAccount.asStateFlow()
    private var scheduled: Job? = null
    private var lastUpload = 0L
    private var retryAt = 0L

    init {
        SyncHooks.schedule = { schedule(it) }
        source.start()
        scope.launch {
            source.state.collect { state ->
                if (state.step in listOf("joining", "ready", "channel_error", "channel_pending")) reconcileNow()
                else mutableAccount.value = mutableAccount.value.copy(signedIn = false, status = "Connect Telegram in settings")
            }
        }
        val network = app.getSystemService(ConnectivityManager::class.java)
        network.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { reconcileNow() }
        })
        scope.launch {
            while (isActive) { delay(60_000); reconcileNow() }
        }
    }

    fun schedule(delayMs: Long = 400) {
        mutableAccount.value = mutableAccount.value.copy(pending = true)
        if (scheduled?.isActive == true) {
            if (delayMs != 0L) return // Continuous playback updates must not starve the upload.
            scheduled?.cancel()
        }
        scheduled = scope.launch {
            val throttle = if (Playback.state.value.playing) (lastUpload + 60_000 - System.currentTimeMillis()).coerceAtLeast(0) else 0
            delay(if (delayMs == 0L) 0 else maxOf(delayMs, throttle))
            scheduled = null
            reconcile()
        }
    }
    fun reconcileNow() { scope.launch { reconcile() } }

    private suspend fun sync(forceUpload: Boolean = false): Long {
        val owner = store.accountId()
        check(access.syncValue("owner") == owner.toString() || !Playback.state.value.active) {
            "Stop playback before switching sync accounts"
        }
        val changedAccount = access.syncValue("owner") != owner.toString()
        repository.bind(owner)
        if (changedAccount) { Favourites.get(app).refresh(); Playback.revision.value += 1 }
        mutableAccount.value = mutableAccount.value.copy(signedIn = true,
            lastSync = access.syncValue("last-$owner")?.toLongOrNull() ?: 0)
        val remote = store.pull()
        check(store.accountId() == owner) { "Telegram account changed during sync" }
        repository.merge(remote, owner)
        Favourites.get(app).refresh(); Playback.revision.value += 1
        val upload = repository.snapshot(owner)
        val previous = access.syncValue("published-$owner")
        val encoded = SnapshotCodec.json.encodeToString(upload)
        // Republish when the remote attachment was removed, even if local data did not change.
        if (previous != encoded || remote.none { it == upload }) {
            if (!forceUpload && Playback.state.value.playing && System.currentTimeMillis() < lastUpload + 60_000) {
                mutableAccount.value = mutableAccount.value.copy(status = "Playback changes pending sync", pending = true)
                return owner
            }
            store.publish(upload)
            lastUpload = System.currentTimeMillis()
            repository.state("published-$owner", encoded)
        }
        upload.favourites.forEach { access.clearFavouriteDirty(it.movieId, it.updated) }
        upload.progress.forEach { access.clearProgressDirty(it.movieId, it.updated) }
        Favourites.get(app).refresh(); Playback.revision.value += 1
        val now = System.currentTimeMillis()
        repository.state("last-$owner", now.toString())
        mutableAccount.value = AccountState(signedIn = true, status = "Synced to Saved Messages", lastSync = now,
            pending = access.dirtyFavourites().isNotEmpty() || access.dirtyProgress().isNotEmpty())
        try { store.cleanup(upload) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            mutableAccount.value = mutableAccount.value.copy(status = "Synced to Saved Messages · backup cleanup pending")
        }
        return owner
    }
    private suspend fun reconcile() = mutex.withLock {
        if (System.currentTimeMillis() < retryAt) return@withLock
        try { sync(); retryAt = 0 }
        catch (error: TimeoutCancellationException) {
            mutableAccount.value = mutableAccount.value.copy(status = "Sync timed out; local data kept", pending = true)
        }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (error is TelegramRequestException && error.code == 429) {
                retryAt = System.currentTimeMillis() + (error.retryAfterSeconds ?: 60).coerceAtLeast(1) * 1000
            }
            mutableAccount.value = mutableAccount.value.copy(status = safeSetupError(error), pending = true)
        }
    }
    companion object {
        @Volatile private var instance: CloudSync? = null
        fun get(context: Context): CloudSync = instance ?: synchronized(this) {
            instance ?: CloudSync(context).also { instance = it }
        }
    }
}
