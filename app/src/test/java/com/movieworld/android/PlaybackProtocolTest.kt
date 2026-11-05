package com.movieworld.android

import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class PlaybackProtocolTest {
    @Test fun rangeBoundariesAndLargeFiles() {
        assertEquals(0L to 99L, byteRange(null, 100))
        assertEquals(5L to 9L, byteRange("bytes=5-9", 100))
        assertEquals(95L to 99L, byteRange("bytes=95-", 100))
        assertEquals(95L to 99L, byteRange("bytes=-5", 100))
        assertEquals(0L to 99L, byteRange("bytes=-500", 100))
        assertEquals(5L to 99L, byteRange("bytes=5-500", 100))
        assertEquals(4_000_000_000L to 4_999_999_999L, byteRange("bytes=4000000000-", 5_000_000_000))
        listOf("bytes=100-", "bytes=-0", "bytes=-", "bytes=9-5", "bytes=1-2,4-5",
            "bytes=9999999999999999999999-", "garbage").forEach {
            assertNull(it, byteRange(it, 100))
        }
        assertNull(byteRange("bytes=0-", 0))
    }

    @Test fun serverSupportsTvHeadAndSeekingAndRejectsOtherPaths() {
        val movie = File.createTempFile("movie-world-test", ".mp4")
        movie.writeBytes(ByteArray(128) { it.toByte() })
        try {
            MediaServer(InetAddress.getLoopbackAddress(), movie, "video/mp4").use { server ->
                fun request(method: String, range: String? = null, path: String = server.url): HttpURLConnection =
                    (URL(path).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 2_000
                        readTimeout = 2_000
                        requestMethod = method
                        if (range != null) setRequestProperty("Range", range)
                    }
                val head = request("HEAD")
                try {
                    assertEquals(200, head.responseCode)
                    assertEquals("128", head.getHeaderField("Content-Length"))
                    assertEquals(0, head.inputStream.readBytes().size)
                } finally { head.disconnect() }
                val seek = request("GET", "bytes=64-79")
                try {
                    assertEquals(206, seek.responseCode)
                    assertEquals("bytes 64-79/128", seek.getHeaderField("Content-Range"))
                    assertArrayEquals(ByteArray(16) { (it + 64).toByte() }, seek.inputStream.readBytes())
                } finally { seek.disconnect() }
                val invalid = request("GET", "bytes=128-")
                try { assertEquals(416, invalid.responseCode) } finally { invalid.disconnect() }
                val missing = request("GET", path = server.url.substringBefore("/media/") + "/other")
                try { assertEquals(404, missing.responseCode) } finally { missing.disconnect() }
            }
        } finally { movie.delete() }
    }

    @Test fun timeAndXmlHandleActualDlnaResponses() {
        assertEquals(3723L, parseTime("01:02:03.000"))
        assertEquals(-1L, parseTime("NOT_IMPLEMENTED"))
        assertEquals("01:02:03", formatTime(3723))
        val response = xml("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
            <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <RelTime>00:02:10</RelTime></u:GetPositionInfoResponse></s:Body></s:Envelope>""")
        assertEquals("00:02:10", response.text("RelTime"))
        try {
            xml("""<!DOCTYPE tv [<!ENTITY leak SYSTEM "file:///private">]><tv>&leak;</tv>""")
            fail("Document types must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
