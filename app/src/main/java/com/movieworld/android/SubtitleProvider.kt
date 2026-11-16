package com.movieworld.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.*

internal fun subtitleCache(context: Context) = SubtitleCache(File(context.cacheDir, "movie-subtitles"))

/** Provider credentials stay encrypted with an Android Keystore key; passwords are never persisted. */
internal class SubtitleSettings(context: Context) {
    private val prefs = context.getSharedPreferences("subtitle-provider", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey("movie-world-subtitles", null) as? SecretKey ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("movie-world-subtitles", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        check(prefs.edit().putString(name, encoded).commit()) { "Could not save subtitle settings." }
    }
    private fun get(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
    fun provider() = prefs.getString("provider", "opensubtitles").orEmpty()
    fun selectProvider(value: String) { require(value in setOf("subdl", "opensubtitles")); check(prefs.edit().putString("provider", value).commit()) }
    fun configured() = if (provider() == "subdl") prefs.contains("subdl-key") else prefs.contains("api-key")
    fun subdlKey() = get("subdl-key") ?: error("Set up SubDL in TV settings first.")
    fun apiKey() = if (provider() == "subdl") subdlKey() else get("api-key") ?: error("Set up OpenSubtitles in TV settings first.")
    fun token() = get("token")
    fun language() = prefs.getString("language", "en").orEmpty()
    fun baseUrl(): String = prefs.getString("base", "https://api.opensubtitles.com").orEmpty()
    fun save(apiKey: String, language: String) {
        require(Regex("[A-Za-z0-9_-]{8,200}").matches(apiKey)) { "Enter your subtitle provider API key." }
        require(Regex("[a-z]{2,3}(-[a-z]{2})?").matches(language)) { "Use a language code such as en, es or fr." }
        if (provider() == "subdl") {
            put("subdl-key", apiKey)
            check(prefs.edit().putString("language", language).commit())
            return
        }
        val changedKey = get("api-key") != apiKey
        put("api-key", apiKey)
        val editor = prefs.edit().putString("language", language)
        if (changedKey) editor.remove("token").remove("base")
        check(editor.commit())
    }
    fun signedIn(token: String, base: String) {
        val address = URL(if (base.startsWith("https://")) base else "https://$base")
        require(address.protocol == "https" && address.host in setOf("api.opensubtitles.com", "vip-api.opensubtitles.com") &&
            address.userInfo == null && (address.port == -1 || address.port == 443)) { "Unexpected OpenSubtitles API address." }
        put("token", token)
        check(prefs.edit().putString("base", "https://${address.host}").commit())
    }
    fun clear() { check(prefs.edit().clear().commit()) }
}

internal class SubtitleProvider(private val context: Context) {
    private val settings = SubtitleSettings(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val agent = "MovieWorld v${BuildConfig.VERSION_NAME}"
    private fun bytes(connection: HttpURLConnection, limit: Int): ByteArray {
        return connection.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (out.size() <= limit) {
                val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - out.size()))
                if (count < 0) break
                out.write(buffer, 0, count)
            }
            require(out.size() <= limit) { "Subtitle server response is too large." }
            out.toByteArray()
        }
    }
    private fun checkStatus(code: Int) {
        when (code) {
            401, 403 -> error("OpenSubtitles access denied. Check the API key or sign in in TV settings.")
            406, 429 -> error("OpenSubtitles download limit reached. Try later or check your account quota.")
        }
        check(code in 200..299) { "OpenSubtitles request failed (HTTP $code). Try again later." }
    }
    private fun api(method: String, path: String, body: String?): String {
        val connection = URL(settings.baseUrl() + "/api/v1" + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 20000
            connection.requestMethod = method
            connection.setRequestProperty("User-Agent", agent)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Api-Key", settings.apiKey())
            settings.token()?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            checkStatus(connection.responseCode)
            return String(bytes(connection, 2 * 1024 * 1024), Charsets.UTF_8)
        } finally { connection.disconnect() }
    }
    private fun download(address: String): ByteArray {
        var url = URL(address)
        repeat(5) {
            require(url.protocol == "https" && (url.host == "opensubtitles.com" || url.host.endsWith(".opensubtitles.com") || url.host == "dl.subdl.com") &&
                url.userInfo == null && (url.port == -1 || url.port == 443)) { "OpenSubtitles returned an unsupported download address." }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000; connection.readTimeout = 20000
                // Credentials are scoped to their provider, including on every redirect hop.
                if (url.host == "dl.subdl.com") connection.setRequestProperty("X-API-Key", settings.subdlKey())
                connection.setRequestProperty("User-Agent", agent)
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Missing subtitle redirect."))
                } else {
                    checkStatus(status)
                    return bytes(connection, 2 * 1024 * 1024)
                }
            } finally { connection.disconnect() }
        }
        error("Too many subtitle download redirects.")
    }
    private fun subdl(): SubDlClient = SubDlClient({ query ->
        val encoded = java.net.URLEncoder.encode(settings.subdlKey(), "UTF-8")
        val connection = URL("https://api.subdl.com/api/v1/subtitles?api_key=$encoded&$query").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 20000
            val code = connection.responseCode
            check(code in 200..299) { "SubDL request failed (HTTP $code). Check your key or quota." }
            String(bytes(connection, 2 * 1024 * 1024), Charsets.UTF_8)
        } finally { connection.disconnect() }
    }, ::download)
    fun test() {
        if (settings.provider() == "subdl") subdl().search(SubtitleIdentity("", null, "tt0133093", null), settings.language())
        else api("GET", "/infos/languages", null)
    }
    fun signIn(username: String, password: String) {
        val body = buildJsonObject { put("username", username); put("password", password) }.toString()
        val response = json.parseToJsonElement(api("POST", "/login", body)).jsonObject
        val token = response["token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: error("OpenSubtitles sign-in failed.")
        settings.signedIn(token, response["base_url"]?.jsonPrimitive?.contentOrNull ?: "api.opensubtitles.com")
    }
    private fun <T> logged(action: String, block: () -> T): T {
        OperationLog.record(context, "subtitles", "${settings.provider()} · $action started")
        try {
            return block().also { OperationLog.record(context, "subtitles", "${settings.provider()} · $action completed") }
        } catch (failure: Exception) {
            OperationLog.record(context, "subtitles", "${settings.provider()} · $action failed: ${failure.message.orEmpty()}", "error")
            throw failure
        }
    }
    fun search(movie: SubtitleIdentity) = logged("Search for ${movie.title}") { if (settings.provider() == "subdl") subdl().search(movie, settings.language()) else OpenSubtitlesClient(::api, ::download).search(movie, settings.language()) }
    fun download(match: SubtitleMatch) = logged("Download subtitle") { if (match.provider == "subdl") subdl().download(match) else OpenSubtitlesClient(::api, ::download).download(match) }
}
