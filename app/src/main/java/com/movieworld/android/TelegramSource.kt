package com.movieworld.android

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only encrypted secrets are written to preferences; the wrapping key stays in Android Keystore. */
private class TelegramVault(context: Context) {
    private val prefs = context.getSharedPreferences("telegram-vault", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("movie-world-telegram", null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder("movie-world-telegram",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }
    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        check(prefs.edit().putString(name, encrypted).commit()) { "Could not save Telegram settings" }
    }
    fun clearCredentials() {
        check(prefs.edit().remove("api-id").remove("api-hash").commit()) { "Could not clear Telegram settings" }
    }
    fun get(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }
}

data class TelegramState(val step: String = "setup", val error: String? = null)
data class TelegramMedia(val file: TdApi.File, val mime: String)

class TelegramSource private constructor(private val context: Context) {
    private val vault = TelegramVault(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var client: Client? = null
    private val mutableState = MutableStateFlow(TelegramState())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun start() {
        if (client != null) return
        if (vault.get("api-id") == null) return
        System.loadLibrary("tdjni")
        Client.execute(TdApi.SetLogVerbosityLevel(0))
        client = Client.create({ update ->
            if (update is TdApi.UpdateAuthorizationState) {
                val authorization = update.authorizationState
                val step = when (authorization) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> "initializing"
                    is TdApi.AuthorizationStateWaitPhoneNumber -> "phone"
                    is TdApi.AuthorizationStateWaitCode -> "code"
                    is TdApi.AuthorizationStateWaitPassword -> "password"
                    is TdApi.AuthorizationStateReady -> "joining"
                    is TdApi.AuthorizationStateClosed -> "closed"
                    else -> "additional"
                }
                mutableState.value = TelegramState(step)
                if (authorization is TdApi.AuthorizationStateReady) {
                    scope.launch { joinMovieChannel() }
                }
                if (authorization is TdApi.AuthorizationStateWaitTdlibParameters) {
                    scope.launch { authRequest {
                        request(TdApi.SetTdlibParameters().apply {
                            databaseDirectory = File(context.noBackupFilesDir, "telegram").absolutePath
                            filesDirectory = File(context.filesDir, "telegram-media").absolutePath
                            databaseEncryptionKey = databaseKey()
                            useFileDatabase = true
                            useChatInfoDatabase = true
                            useMessageDatabase = false
                            useSecretChats = false
                            apiId = requireNotNull(vault.get("api-id")).toInt()
                            apiHash = requireNotNull(vault.get("api-hash"))
                            systemLanguageCode = "en"
                            deviceModel = Build.MODEL
                            systemVersion = Build.VERSION.RELEASE
                            applicationVersion = "0.2"
                        })
                    } }
                }
            }
        }, { mutableState.value = mutableState.value.copy(error = "Telegram client failed. Restart Movie World.") },
            { mutableState.value = mutableState.value.copy(error = "Telegram client failed. Restart Movie World.") })
    }

    private fun databaseKey(): ByteArray {
        val existing = vault.get("database-key")
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        vault.put("database-key", Base64.encodeToString(key, Base64.NO_WRAP))
        return key
    }

    suspend fun configure(id: String, hash: String) {
        require(id.toIntOrNull()?.let { it > 0 } == true && hash.matches(Regex("[a-fA-F0-9]{32}"))) {
            "Enter the api_id and api_hash from my.telegram.org."
        }
        check(client == null) { "Restart the app before changing Telegram API credentials." }
        vault.put("api-id", id)
        vault.put("api-hash", hash)
        start()
    }

    /** Run after authorization, including restored sessions, before advertising playback readiness. */
    suspend fun joinMovieChannel() {
        check(state.value.step in listOf("joining", "channel_error", "channel_pending", "ready")) {
            "Complete Telegram sign-in first."
        }
        mutableState.value = TelegramState("joining")
        try {
            request(TdApi.JoinChatByInviteLink(MOVIE_CHANNEL_INVITE))
            mutableState.value = TelegramState("ready")
        } catch (error: TimeoutCancellationException) {
            mutableState.value = TelegramState("channel_error", "Joining the movie channel timed out. Check your connection and retry.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            mutableState.value = when {
                "USER_ALREADY_PARTICIPANT" in message -> TelegramState("ready")
                "INVITE_REQUEST_SENT" in message -> TelegramState("channel_pending",
                    "Your request to join the movie channel was sent. Wait for admin approval, then retry.")
                "INVITE_HASH_EXPIRED" in message || "INVITE_HASH_INVALID" in message -> TelegramState("channel_error",
                    "The movie channel invite has expired or is invalid. A new invite is needed in the app.")
                else -> TelegramState("channel_error", "Could not join the movie channel. ${error.message ?: "Check your connection and retry."}")
            }
        }
    }

    suspend fun resetConfiguration() {
        check(!Playback.state.value.active) { "Stop TV playback before changing Telegram settings." }
        if (client != null) {
            request(TdApi.Close())
            withTimeout(15_000) { state.first { it.step == "closed" } }
            client = null
        }
        vault.clearCredentials()
        mutableState.value = TelegramState()
    }

    suspend fun submit(value: String) = authRequest {
        when (state.value.step) {
            "phone" -> request(TdApi.SetAuthenticationPhoneNumber(value, null))
            "code" -> request(TdApi.CheckAuthenticationCode(value))
            "password" -> request(TdApi.CheckAuthenticationPassword(value))
            else -> error("Wait for the next Telegram sign-in step.")
        }
    }

    private suspend fun authRequest(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(error = error.message ?: "Telegram sign-in failed")
        }
    }

    suspend fun <T : TdApi.Object> request(function: TdApi.Function<T>): T = withTimeout(60_000) {
        var waiting = 0
        while (client == null && waiting++ < 20) delay(25)
        suspendCancellableCoroutine { continuation ->
            val active = client
            if (active == null) {
                continuation.resumeWithException(IllegalStateException("Set up Telegram first"))
            } else active.send(function) { result ->
                if (continuation.isActive) {
                    if (result is TdApi.Error) continuation.resumeWithException(
                        IllegalStateException("Telegram ${result.code}: ${result.message}"))
                    else {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(result as T)
                    }
                }
            }
        }
    }

    suspend fun resolve(url: String): TelegramMedia {
        check(client != null) { "Sign into Telegram in TV settings first." }
        val authorization = withTimeoutOrNull(20_000) {
            state.first { it.step !in listOf("setup", "initializing", "joining") }
        }
        check(authorization?.step == "ready") {
            authorization?.error ?: "Complete Telegram setup in TV settings first."
        }
        val uri = android.net.Uri.parse(url)
        require(uri.scheme == "https" && uri.host == "t.me" && uri.pathSegments.size >= 2) {
            "This title does not have a movie post link."
        }
        // Loading dialogs gives TDLib the private channel's access information.
        try { request(TdApi.LoadChats(TdApi.ChatListMain(), 200)) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* A fully loaded list returns 404. Link resolution remains authoritative. */ }
        val message = request(TdApi.GetMessageLinkInfo(url)).message
            ?: error("This Telegram post is unavailable. Check channel membership in Telegram.")
        // History can return only the cached poster even with onlyLocal=false.
        // Resolve adjacent Telegram post links directly; the resolver still checks
        // chat and album identity before accepting any video.
        var postId = uri.lastPathSegment?.toLongOrNull()
            ?: error("This title does not have a valid Telegram post link.")
        val postPrefix = url.substringBefore('?').substringBeforeLast('/')
        return resolveAlbumVideo(message) {
            postId += 1
            request(TdApi.GetMessageLinkInfo("$postPrefix/$postId")).message
        } ?: error("No video was found in this Telegram album. Open it in Telegram to check the post.")
    }

    suspend fun prepare(media: TelegramMedia): TelegramMedia {
        val size = maxOf(media.file.size, media.file.expectedSize)
        val quota = 8L * 1024 * 1024 * 1024
        require(size <= quota) { "This movie exceeds the 8 GB phone download limit." }
        if (media.file.local.isDownloadingCompleted && File(media.file.local.path).isFile) return media
        val total = File(context.filesDir, "telegram-media").walkTopDown()
            .filter { it.isFile }.sumOf { it.length() }
        val spaceNeeded = (size + 256L * 1024 * 1024 - context.filesDir.usableSpace).coerceAtLeast(0)
        if (total + size > quota || spaceNeeded > 0) {
            request(TdApi.OptimizeStorage(
                minOf(quota - size, (total - spaceNeeded).coerceAtLeast(0)),
                Int.MAX_VALUE, Int.MAX_VALUE, 0,
                arrayOf(TdApi.FileTypeVideo(), TdApi.FileTypeDocument()),
                longArrayOf(), longArrayOf(), false, 0
            ))
        }
        return media.copy(file = request(TdApi.GetFile(media.file.id)))
    }
    suspend fun download(media: TelegramMedia, progress: (Long, Long) -> Unit): File {
        val size = maxOf(media.file.size, media.file.expectedSize)
        require(size <= 8L * 1024 * 1024 * 1024) { "This movie exceeds the 8 GB phone download limit." }
        val required = (size - media.file.local.downloadedSize).coerceAtLeast(0) + 256L * 1024 * 1024
        require(context.filesDir.usableSpace >= required) { "Free more phone storage before downloading this movie." }
        var complete = false
        try {
            var file = request(TdApi.DownloadFile(media.file.id, 16, 0, 0, false))
            while (!file.local.isDownloadingCompleted) {
                currentCoroutineContext().ensureActive()
                progress(file.local.downloadedSize, maxOf(file.size, file.expectedSize))
                check(file.local.isDownloadingActive) { "Telegram download stopped. Tap Play on TV to retry." }
                delay(1_000)
                file = request(TdApi.GetFile(file.id))
            }
            complete = true
            return File(file.local.path).also { check(it.isFile) { "Downloaded video is unavailable." } }
        } finally {
            if (!complete) client?.send(TdApi.CancelDownloadFile(media.file.id, false)) { }
        }
    }

    companion object {
        private const val MOVIE_CHANNEL_INVITE = "https://t.me/+aPF0d29KPUdlNWQ0"
        @Volatile private var instance: TelegramSource? = null
        fun get(context: Context): TelegramSource = instance ?: synchronized(this) {
            instance ?: TelegramSource(context.applicationContext).also { instance = it }
        }
    }
}
