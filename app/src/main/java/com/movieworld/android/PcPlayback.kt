package com.movieworld.android

import java.net.HttpURLConnection
import java.util.UUID
import org.json.JSONObject

/** Wi-Fi-only connection to the PC-owned playback session. */
class PcPlayback(private val net: TvNetwork, address: String) {
    private val base = net.validate(address.trim().trimEnd('/')).let {
        require(it.path.isNullOrEmpty() || it.path == "/") { "Enter the PC server address without a path." }
        require(it.query == null) { "Enter the PC server address without a query." }
        it.toString().trimEnd('/')
    }
    var jobId: String? = null
        private set
    private var resumePoint: JSONObject? = null

    private fun request(path: String, body: JSONObject? = null, timeout: Int = 3000): JSONObject {
        val connection = net.network.openConnection(net.validate(base + path)) as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 1024 * 1024) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: byteArrayOf()
            check(bytes.size <= 1024 * 1024) { "PC response was too large." }
            val result = JSONObject(bytes.toString(Charsets.UTF_8))
            if (code !in 200..299) throw PcPlaybackError(result.optString("code"),
                result.optString("error", "PC returned HTTP $code"))
            return result
        } finally { connection.disconnect() }
    }

    fun check() {
        check(request("/api/tv/session").optBoolean("enabled")) { "Enable TV playback on the PC server first." }
    }

    fun hasMovie(movieId: String): Boolean = request("/api/playback/cache/$movieId").optBoolean("cached")

    fun prepare(movieId: String, rendererId: String) {
        check()
        check(hasRenderer(rendererId)) { "The PC cannot find the selected TV. Turn on the TV and retry." }
        resumePoint = request("/api/resume/$movieId").optJSONObject("resume")
    }

    private fun hasRenderer(rendererId: String): Boolean {
        fun find(refresh: Boolean): Boolean {
            val rows = request("/api/tvs" + if (refresh) "?refresh=1" else "", timeout = 12000)
                .getJSONArray("renderers")
            return (0 until rows.length()).any { rows.getJSONObject(it).optString("id") == rendererId }
        }
        return find(false) || find(true)
    }

    fun upload(movieId: String, name: String, size: Long, input: java.io.InputStream,
        progress: (Long, Long) -> Unit) {
        require(size > 0) { "Cannot determine the phone video's size. Select the downloaded video again." }
        val connection = net.network.openConnection(net.validate("$base/api/movies/$movieId/upload")) as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 6000
            connection.readTimeout = 180000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("X-File-Name", java.net.URLEncoder.encode(name, "UTF-8"))
            connection.setFixedLengthStreamingMode(size)
            connection.outputStream.use { output ->
                val buffer = ByteArray(256 * 1024)
                var sent = 0L
                while (sent < size) {
                    progress(sent, size)
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), size - sent).toInt())
                    check(count > 0) { "Phone video ended before the upload finished." }
                    output.write(buffer, 0, count)
                    sent += count
                }
                progress(sent, size)
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val result = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 1024 * 1024) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                check(output.size() <= 1024 * 1024) { "PC response was too large." }
                JSONObject(output.toString("UTF-8"))
            }
            if (code !in 200..299) throw PcPlaybackError(result?.optString("code").orEmpty(),
                result?.optString("error") ?: "PC upload failed (HTTP $code).")
            check(result?.optBoolean("uploaded") == true) { "The PC did not confirm the upload. Retry Play from PC." }
        } finally { connection.disconnect() }
    }

    /** Read-only preflight failures are safe to fall back from. */
    fun cached(movieId: String, rendererId: String): Boolean {
        if (!request("/api/playback/cache/$movieId").optBoolean("cached")) return false
        fun hasTv(refresh: Boolean): Boolean {
            val rows = request("/api/tvs" + if (refresh) "?refresh=1" else "", timeout = 12000)
                .getJSONArray("renderers")
            return (0 until rows.length()).any { rows.getJSONObject(it).optString("id") == rendererId }
        }
        val found = hasTv(false) || hasTv(true)
        if (found) resumePoint = request("/api/resume/$movieId").optJSONObject("resume")
        return found
    }

    fun start(movieId: String, rendererId: String, restart: Boolean, previous: PlaybackDatabase.Progress?, cachedOnly: Boolean = true, subtitleSrt: String? = null): JSONObject? {
        val point = resumePoint
        val start = pcResumePosition(restart, previous?.position ?: 0, previous?.duration ?: 0,
            previous?.updated ?: 0, previous?.finished == true, point?.optLong("position") ?: 0,
            point?.optLong("duration") ?: 0, ((point?.optDouble("updated_at") ?: 0.0) * 1000).toLong())
        val body = JSONObject().put("movie_id", movieId.toLong()).put("renderer_id", rendererId)
            .put("cached_only", cachedOnly).put("start_at", start).put("idempotency_key", UUID.randomUUID().toString())
        if (subtitleSrt != null) {
            check(request("/api/tv/session").optBoolean("subtitles")) {
                "Update the PC server to use subtitles downloaded on the phone."
            }
            body.put("subtitle_srt", subtitleSrt)
        }
        // Once this POST is sent, failure must not start a second phone-hosted session.
        val job = try { request("/api/playback", body, 12000).getJSONObject("job") }
        catch (error: PcPlaybackError) {
            if (error.code == "cache_miss") return null
            throw error
        } catch (error: java.io.IOException) {
            throw IllegalStateException("Could not confirm PC playback. Check the TV before retrying.", error)
        }
        jobId = job.getString("id")
        return job
    }

    internal fun activeSession(): PcSession? = request("/api/playback/active")
        .optJSONObject("job")?.let { session(it) }

    internal fun attach(expectedJob: String, rendererId: String): JSONObject? {
        val job = request("/api/playback/active").optJSONObject("job") ?: return null
        val snapshot = session(job)
        if (snapshot.id != expectedJob || !snapshot.canJoin(rendererId)) return null
        jobId = snapshot.id
        return job
    }

    internal fun session(job: JSONObject): PcSession {
        val tv = job.getJSONObject("renderer")
        return PcSession(job.getString("id"), job.getString("movie_id"), job.getString("title"),
            tv.getString("id"), tv.optString("name", "TV"), job.getString("state"),
            if (job.isNull("position")) null else job.getLong("position"),
            if (job.isNull("duration")) null else job.getLong("duration"),
            job.optString("detail"), job.optJSONObject("error")?.optString("message"))
    }

    fun status(): JSONObject = request("/api/playback/${requireNotNull(jobId)}").getJSONObject("job")
    fun control(action: String, position: Long = 0) {
        request("/api/playback/${requireNotNull(jobId)}/control",
            JSONObject().put("action", action).put("position", position))
    }
}

class PcPlaybackError(val code: String, message: String) : IllegalStateException(message)

internal fun pcResumePosition(restart: Boolean, phonePosition: Long, phoneDuration: Long,
    phoneUpdated: Long, phoneFinished: Boolean, pcPosition: Long, pcDuration: Long, pcUpdated: Long): Long {
    if (restart) return 0
    if (pcUpdated > phoneUpdated) return if (pcDuration > 0 && pcPosition >= pcDuration - 30) 0 else pcPosition.coerceAtLeast(0)
    return if (phoneFinished || (phoneDuration > 0 && phonePosition >= phoneDuration - 30)) 0 else phonePosition.coerceAtLeast(0)
}
