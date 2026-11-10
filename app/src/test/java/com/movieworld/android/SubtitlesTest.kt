package com.movieworld.android

import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class SubtitlesTest {
    private val srt = "1\r\n00:00:00,100 --> 00:00:01,000\r\nHello café\r\n\r\n2\r\n00:00:02,000 --> 00:00:03,000\r\nWorld\r\n"

    @Test fun preservesUnicodeAndNormalizesBothEncodings() {
        val normalized = SrtSubtitles.prepare(srt.byteInputStream())
        assertTrue(normalized.contains("Hello café"))
        assertFalse(normalized.contains('\r'))
        assertEquals(normalized, SrtSubtitles.prepare(srt.toByteArray(Charsets.UTF_16).inputStream()))
    }

    @Test fun positiveAndNegativeTimingPreserveValidCues() {
        assertTrue(SrtSubtitles.prepare(srt.byteInputStream(), 500).contains("00:00:00,600 --> 00:00:01,500"))
        assertTrue(SrtSubtitles.prepare(srt.byteInputStream(), -500).contains("00:00:00,000 --> 00:00:00,500"))
        val shifted = SrtSubtitles.prepare(srt.byteInputStream(), -1500)
        assertFalse(shifted.contains("Hello"))
        assertTrue(shifted.startsWith("1\n00:00:00,500 --> 00:00:01,500"))
    }

    @Test fun invalidInputsAreRejectedBeforePlayback() {
        listOf("", "<html>Not subtitles</html>", "1\n00:00:99,000 --> 00:00:01,000\nHello",
            "1\n00:00:02,000 --> 00:00:01,000\nHello", "1\n00:00:00,000 --> 00:00:01,000").forEach {
            assertThrows(IllegalArgumentException::class.java) { SrtSubtitles.prepare(it.byteInputStream()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SrtSubtitles.prepare(ByteArray(2 * 1024 * 1024 + 1).inputStream())
        }
        assertThrows(IllegalArgumentException::class.java) { SrtSubtitles.prepare(srt.byteInputStream(), 30001) }
        assertThrows(IllegalArgumentException::class.java) { SrtSubtitles.prepare(srt.byteInputStream(), -30000) }
        assertThrows(IllegalStateException::class.java) { SrtSubtitles.prepare(byteArrayOf(0xff.toByte(), 0x80.toByte()).inputStream()) }
    }

    @Test fun samsungHeadersAndSubtitleCapabilityServeTheActualSrt() {
        val movie = File.createTempFile("subtitle-movie", ".mp4").apply { writeBytes(byteArrayOf(1,2,3)) }
        val captions = File.createTempFile("subtitle-track", ".srt").apply { writeText(srt) }
        try {
            MediaServer(InetAddress.getLoopbackAddress(), movie, "video/mp4", captions).use { server ->
                fun request(url: String, method: String = "GET", range: String? = null) =
                    (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = method; connectTimeout = 2000; readTimeout = 2000
                        if (range != null) setRequestProperty("Range", range)
                    }
                val video = request(server.url, "HEAD")
                try {
                    assertEquals(200, video.responseCode)
                    assertEquals(server.subtitleUrl, video.getHeaderField("CaptionInfo.sec"))
                    assertEquals(server.subtitleUrl, video.getHeaderField("CaptionInfoEx.sec"))
                } finally { video.disconnect() }
                val subtitle = request(server.subtitleUrl!!)
                try {
                    assertEquals(200, subtitle.responseCode)
                    assertTrue(subtitle.contentType.startsWith("application/x-subrip"))
                    assertArrayEquals(captions.readBytes(), subtitle.inputStream.readBytes())
                    assertNull(subtitle.getHeaderField("CaptionInfo.sec"))
                } finally { subtitle.disconnect() }
                val head = request(server.subtitleUrl!!, "HEAD")
                try { assertEquals(200, head.responseCode); assertEquals(captions.length(), head.contentLengthLong) }
                finally { head.disconnect() }
                val range = request(server.subtitleUrl!!, range = "bytes=0-9")
                try { assertEquals(206, range.responseCode); assertArrayEquals(captions.readBytes().take(10).toByteArray(), range.inputStream.readBytes()) }
                finally { range.disconnect() }
                val invalid = request(server.url.substringBefore("/media/") + "/media/wrong/subtitles.srt")
                try { assertEquals(404, invalid.responseCode) } finally { invalid.disconnect() }
            }
            MediaServer(InetAddress.getLoopbackAddress(), movie, "video/mp4").use { server ->
                assertNull(server.subtitleUrl)
                val video = URL(server.url).openConnection() as HttpURLConnection
                try { assertEquals(200, video.responseCode); assertNull(video.getHeaderField("CaptionInfo.sec")) }
                finally { video.disconnect() }
            }
        } finally { movie.delete(); captions.delete() }
    }
}
