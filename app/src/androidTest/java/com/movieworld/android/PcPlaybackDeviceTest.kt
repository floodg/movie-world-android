package com.movieworld.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual Android HTTP client against a Wi-Fi-bound fake PC. */
@RunWith(AndroidJUnit4::class)
class PcPlaybackDeviceTest {
    private data class Request(val path: String, val headers: Map<String, String>, val bytes: ByteArray)

    private fun withPc(responses: List<String>, test: (PcPlayback) -> Unit): List<Request> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val net = TvNetwork(context)
        val server = ServerSocket().apply { bind(InetSocketAddress(net.address, 0)); soTimeout = 15000 }
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<List<Request>> {
            responses.map { response ->
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream().buffered()
                    fun line(): String {
                        val output = java.io.ByteArrayOutputStream()
                        while (true) {
                            val byte = input.read()
                            check(byte >= 0) { "Unexpected request EOF" }
                            if (byte == 10) return output.toString("UTF-8").trimEnd('\r')
                            output.write(byte)
                        }
                    }
                    val path = line().split(" ")[1]
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val value = line()
                        if (value.isEmpty()) break
                        headers[value.substringBefore(':').lowercase()] = value.substringAfter(':').trim()
                    }
                    val bytes = ByteArray(headers["content-length"]?.toInt() ?: 0)
                    var offset = 0
                    while (offset < bytes.size) {
                        val count = input.read(bytes, offset, bytes.size - offset)
                        check(count > 0)
                        offset += count
                    }
                    val body = response.toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                    Request(path, headers, bytes)
                }
            }
        }
        try {
            test(PcPlayback(net, "http://${net.address.hostAddress}:${server.localPort}"))
            return future.get(15, TimeUnit.SECONDS)
        } finally { server.close(); executor.shutdownNow() }
    }

    @Test fun explicitPcStartAllowsDownloadAndUsesSharedControls() {
        val requests = withPc(listOf(
            """{"enabled":true}""",
            """{"renderers":[{"id":"uuid:tv"}]}""",
            """{"resume":{"position":100,"duration":1000,"updated_at":9999999999}}""",
            """{"cached":false}""",
            """{"job":{"id":"shared-job"}}""",
            """{"job":{}}"""
        )) { pc ->
            pc.prepare("42", "uuid:tv")
            assertFalse(pc.hasMovie("42"))
            assertNotNull(pc.start("42", "uuid:tv", false, null, cachedOnly = false))
            pc.control("seek", 250)
        }
        val start = JSONObject(String(requests[4].bytes))
        assertFalse(start.getBoolean("cached_only"))
        assertEquals(100L, start.getLong("start_at"))
        assertEquals("uuid:tv", start.getString("renderer_id"))
        assertEquals("/api/playback/shared-job/control", requests[5].path)
        assertEquals(250L, JSONObject(String(requests[5].bytes)).getLong("position"))
    }

    @Test fun uploadPreservesBytesAndReportsProgress() {
        val video = ByteArray(700000) { (it % 251).toByte() }
        var sent = 0L
        val requests = withPc(listOf("""{"uploaded":true}""")) { pc ->
            video.inputStream().use { input ->
                pc.upload("42", "movie.mkv", video.size.toLong(), input) { done, total ->
                    assertEquals(video.size.toLong(), total)
                    assertTrue(done >= sent)
                    sent = done
                }
            }
        }
        assertEquals(video.size.toLong(), sent)
        assertEquals("/api/movies/42/upload", requests.single().path)
        assertEquals("movie.mkv", requests.single().headers["x-file-name"])
        assertEquals("application/octet-stream", requests.single().headers["content-type"])
        assertArrayEquals(video, requests.single().bytes)
    }

    @Test fun disabledPlaybackIsNotAvailable() {
        withPc(listOf("""{"enabled":false}""")) { pc ->
            try { pc.check(); fail("Disabled server must be unavailable") }
            catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("Enable TV playback")) }
        }
    }
}
