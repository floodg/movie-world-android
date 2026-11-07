package com.movieworld.android

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

data class AccountState(
    val configured: Boolean = false,
    val email: String? = null,
    val displayName: String? = null,
    val signedIn: Boolean = false,
    val status: String = "Cloud sync is not configured",
)

/**
 * Pushes dirty Room rows and pulls newer cloud rows. Never blocks TV playback.
 * No-ops when Supabase URL / publishable key are missing from BuildConfig.
 */
class CloudSync private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var debounce: Job? = null
    private val prefs = app.getSharedPreferences("cloud-sync", Context.MODE_PRIVATE)
    private val mutableAccount = MutableStateFlow(AccountState())
    val account: StateFlow<AccountState> = mutableAccount.asStateFlow()

    private val client: SupabaseClient? = run {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
        if (url.isEmpty() || key.isEmpty()) null
        else createSupabaseClient(url, key) {
            install(Auth)
            install(Postgrest)
        }
    }

    init {
        SyncHooks.schedule = { delayMs -> schedule(delayMs) }
        if (client == null) {
            mutableAccount.value = AccountState(configured = false, status = "Add supabase.url and supabase.publishableKey to local.properties")
        } else {
            mutableAccount.value = AccountState(configured = true, status = "Signed out")
            scope.launch {
                client.auth.sessionStatus.collectLatest { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val email = status.session.user?.email
                            val profile = loadProfile()
                            mutableAccount.value = AccountState(
                                configured = true,
                                email = email,
                                displayName = profile?.displayName,
                                signedIn = true,
                                status = "Signed in · syncing…",
                            )
                            reconcile()
                        }
                        else -> mutableAccount.value = AccountState(configured = true, signedIn = false, status = "Signed out")
                    }
                }
            }
        }
    }

    fun schedule(delayMs: Long = 400) {
        if (client == null) return
        debounce?.cancel()
        debounce = scope.launch {
            delay(delayMs)
            reconcile()
        }
    }

    fun reconcileNow() {
        if (client == null) return
        scope.launch { reconcile() }
    }

    suspend fun signUp(email: String, password: String, displayName: String?) {
        val supabase = client ?: error("Cloud sync is not configured")
        supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            if (!displayName.isNullOrBlank()) {
                data = buildJsonObject { put("display_name", displayName.trim()) }
            }
        }
        if (!displayName.isNullOrBlank() && supabase.auth.currentSessionOrNull() != null) {
            saveDisplayName(displayName.trim())
        }
    }

    suspend fun signIn(email: String, password: String) {
        val supabase = client ?: error("Cloud sync is not configured")
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        client?.auth?.signOut()
    }

    suspend fun saveDisplayName(displayName: String?) {
        val supabase = client ?: return
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        val name = displayName?.trim()?.ifEmpty { null }
        supabase.from("profiles").update(
            ProfileUpdate(displayName = name, updatedAt = Instant.now().toString())
        ) {
            filter { eq("id", userId) }
        }
        mutableAccount.value = mutableAccount.value.copy(displayName = name, status = "Display name saved")
    }

    private suspend fun reconcile() = mutex.withLock {
        val supabase = client ?: return
        val user = supabase.auth.currentUserOrNull() ?: return
        val access = PlaybackDatabase.get(app).access()
        try {
            pushDirty(access, user.id)
            pullRemote(access, user.id)
            Favourites.get(app).refresh()
            Playback.revision.value += 1
            val dirtyLeft = access.dirtyFavourites().isNotEmpty() || access.dirtyProgress().isNotEmpty()
            mutableAccount.value = mutableAccount.value.copy(
                signedIn = true,
                email = user.email,
                displayName = loadProfile()?.displayName ?: mutableAccount.value.displayName,
                status = if (dirtyLeft) "Signed in · offline changes pending" else "Synced",
            )
            prefs.edit().putLong("last-pull", System.currentTimeMillis()).apply()
        } catch (error: Exception) {
            Log.w(TAG, "Cloud sync failed", error)
            mutableAccount.value = mutableAccount.value.copy(
                status = error.message?.take(120) ?: "Sync failed · local copy kept"
            )
        }
    }

    private suspend fun pushDirty(access: PlaybackDatabase.Access, userId: String) {
        val supabase = client ?: return
        for (row in access.dirtyFavourites()) {
            supabase.from("favourites").upsert(
                FavouriteRow(
                    userId = userId,
                    movieId = row.movieId.toLong(),
                    removedAt = row.removed?.let { Instant.ofEpochMilli(it).toString() },
                    updatedAt = Instant.ofEpochMilli(row.updated).toString(),
                )
            )
            access.clearFavouriteDirty(row.movieId, row.updated)
        }
        for (row in access.dirtyProgress()) {
            supabase.from("watch_progress").upsert(
                WatchRow(
                    userId = userId,
                    movieId = row.movieId.toLong(),
                    positionSeconds = row.position.toInt().coerceAtLeast(0),
                    durationSeconds = row.duration.takeIf { it > 0 }?.toInt(),
                    finished = row.finished,
                    updatedAt = Instant.ofEpochMilli(row.updated).toString(),
                )
            )
            access.clearProgressDirty(row.movieId, row.updated)
        }
    }

    private suspend fun pullRemote(access: PlaybackDatabase.Access, userId: String) {
        val supabase = client ?: return
        val favourites = supabase.from("favourites").select {
            filter { eq("user_id", userId) }
        }.decodeList<FavouriteRow>()
        for (row in favourites) {
            Favourites.get(app).upsertFromCloud(
                movieId = row.movieId.toString(),
                updatedMs = parseInstantMs(row.updatedAt),
                removedMs = row.removedAt?.let(::parseInstantMs),
            )
        }
        val progress = supabase.from("watch_progress").select {
            filter { eq("user_id", userId) }
        }.decodeList<WatchRow>()
        for (row in progress) {
            val updatedMs = parseInstantMs(row.updatedAt)
            val existing = access.find(row.movieId.toString())
            if (existing != null && existing.updated > updatedMs) continue
            access.save(PlaybackDatabase.Progress().apply {
                movieId = row.movieId.toString()
                position = row.positionSeconds.toLong()
                duration = row.durationSeconds?.toLong() ?: 0L
                updated = updatedMs
                finished = row.finished
                dirty = false
            })
        }
    }

    private suspend fun loadProfile(): ProfileRow? {
        val supabase = client ?: return null
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return supabase.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeSingleOrNull<ProfileRow>()
    }

    private fun parseInstantMs(value: String): Long = try {
        Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }

    @Serializable
    private data class ProfileRow(
        val id: String,
        @SerialName("display_name") val displayName: String? = null,
    )

    @Serializable
    private data class ProfileUpdate(
        @SerialName("display_name") val displayName: String?,
        @SerialName("updated_at") val updatedAt: String,
    )

    @Serializable
    private data class FavouriteRow(
        @SerialName("user_id") val userId: String,
        @SerialName("movie_id") val movieId: Long,
        @SerialName("removed_at") val removedAt: String? = null,
        @SerialName("updated_at") val updatedAt: String,
    )

    @Serializable
    private data class WatchRow(
        @SerialName("user_id") val userId: String,
        @SerialName("movie_id") val movieId: Long,
        @SerialName("position_seconds") val positionSeconds: Int,
        @SerialName("duration_seconds") val durationSeconds: Int? = null,
        val finished: Boolean = false,
        @SerialName("updated_at") val updatedAt: String,
    )

    companion object {
        private const val TAG = "CloudSync"
        @Volatile private var instance: CloudSync? = null
        fun get(context: Context): CloudSync = instance ?: synchronized(this) {
            instance ?: CloudSync(context).also { instance = it }
        }
    }
}
