package com.movieworld.android

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A session exposes exactly one completed file, reachable only through a random capability path. */
class MediaServer(address: InetAddress, private val file: File, private val mime: String, private val subtitle: File? = null) : Closeable {
    private val server = ServerSocket().apply { bind(InetSocketAddress(address, 0), 8) }
    private val token = "/media/${UUID.randomUUID()}"
    val url = "http://${address.hostAddress}:${server.localPort}$token"
    val subtitleUrl: String? = subtitle?.let { "$url/subtitles.srt" }
    private val pool = java.util.concurrent.ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(8))
    private val sockets = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    private val acceptor = Thread({
        while (!server.isClosed) {
            try {
                val socket = server.accept()
                sockets += socket
                try {
                    pool.execute {
                        try { socket.use { serve(it) } }
                        catch (_: Exception) { }
                        finally { sockets -= socket }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    sockets -= socket
                    socket.close()
                }
            } catch (_: java.io.IOException) {
                if (!server.isClosed) close()
            }
        }
    }, "MovieWorld-media").apply { isDaemon = true; start() }

    private fun serve(socket: Socket) {
        socket.soTimeout = 10_000
        val reader = socket.getInputStream().buffered()
        fun line(): String {
            val bytes = java.io.ByteArrayOutputStream()
            while (bytes.size() < 8_192) {
                val next = reader.read()
                if (next < 0 || next == 10) return bytes.toString("US-ASCII").trimEnd('\r')
                bytes.write(next)
            }
            error("Header too large")
        }
        val request = line().split(' ')
        val headers = mutableMapOf<String, String>()
        var total = 0
        while (true) {
            val text = line()
            if (text.isEmpty()) break
            total += text.length
            require(total < 32_768)
            headers[text.substringBefore(':').lowercase(java.util.Locale.US)] = text.substringAfter(':').trim()
        }
        val output = socket.getOutputStream()
        val isSubtitle = subtitle != null && request.getOrNull(1) == "$token/subtitles.srt"
        val servedFile = if (isSubtitle) subtitle!! else file
        val servedMime = if (isSubtitle) "application/x-subrip; charset=utf-8" else mime
        fun respond(code: String, size: Long, extra: String = "") {
            output.write(("HTTP/1.1 $code\r\nContent-Length: $size\r\nConnection: close\r\n" +
                "Accept-Ranges: bytes\r\nContent-Type: $servedMime\r\n" +
                "transferMode.dlna.org: Streaming\r\ncontentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0\r\n" +
                (if (!isSubtitle && request.getOrNull(1) == token && subtitleUrl != null)
                    "CaptionInfo.sec: $subtitleUrl\r\nCaptionInfoEx.sec: $subtitleUrl\r\n" else "") +
                extra + "\r\n").toByteArray(Charsets.US_ASCII))
        }
        if (request.size < 2 || (request[1] != token && !isSubtitle)) { respond("404 Not Found", 0); return }
        if (request[0] !in listOf("GET", "HEAD")) { respond("405 Method Not Allowed", 0, "Allow: GET, HEAD\r\n"); return }
        val length = servedFile.length()
        val bounds = byteRange(headers["range"], length)
        if (bounds == null) { respond("416 Range Not Satisfiable", 0, "Content-Range: bytes */$length\r\n"); return }
        val (start, end) = bounds
        val count = (end - start + 1).coerceAtLeast(0)
        respond(if (headers["range"] != null) "206 Partial Content" else "200 OK", count,
            if (headers["range"] != null) "Content-Range: bytes $start-$end/$length\r\n" else "")
        if (request[0] == "HEAD") return
        RandomAccessFile(servedFile, "r").use { media ->
            media.seek(start)
            val buffer = ByteArray(128 * 1024)
            var remaining = count
            while (remaining > 0) {
                val read = media.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
    }

    override fun close() {
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
        pool.shutdownNow()
    }
}

/** Inclusive bounds, including suffix and open-ended ranges. Multi-range requests return 416. */
internal fun byteRange(header: String?, length: Long): Pair<Long, Long>? {
    if (header == null) return 0L to length - 1
    if (length <= 0 || !header.startsWith("bytes=")) return null
    val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(header) ?: return null
    val left = match.groupValues[1]
    val right = match.groupValues[2]
    if (left.isEmpty()) {
        val suffix = right.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return (length - suffix).coerceAtLeast(0) to length - 1
    }
    val start = left.toLongOrNull()?.takeIf { it in 0 until length } ?: return null
    val end = if (right.isEmpty()) length - 1 else right.toLongOrNull() ?: return null
    return if (end >= start) start to minOf(end, length - 1) else null
}
