package com.movieworld.android

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SubtitleCacheTest {
    private val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello world\n"
    private fun <T> cache(block: (File) -> T): T {
        val root = Files.createTempDirectory("movie-subtitles-test").toFile()
        try { return block(root) } finally { root.deleteRecursively() }
    }
    @Test fun subtitleLinkAndPreferencesSurviveRepositoryRecreation() = cache { root ->
        val source = subtitleSourceKey("42", "https://t.me/c/1/2")
        SubtitleCache(root).save(source, srt.byteInputStream(), "Film release", "en")
        SubtitleCache(root).configure(source, delayMs = 500, enabled = false)
        val restored = SubtitleCache(root).get(source)!!
        assertEquals("Film release", restored.label); assertEquals(500, restored.delayMs); assertFalse(restored.enabled)
        assertNull(SubtitleCache(root).get(subtitleSourceKey("43", "https://t.me/c/1/2")))
        assertNull(SubtitleCache(root).get(subtitleSourceKey("42", "https://t.me/c/1/3")))
    }
    @Test fun failedReplacementKeepsTheExistingFileAndTiming() = cache { root ->
        val cache = SubtitleCache(root)
        cache.save("film", srt.byteInputStream(), "Original", "en")
        cache.configure("film", delayMs = -500)
        assertThrows(IllegalArgumentException::class.java) { cache.save("film", "not SRT".byteInputStream(), "Wrong", "en") }
        assertEquals("Original", cache.get("film")!!.label)
        assertEquals(-500, cache.get("film")!!.delayMs)
        assertEquals(srt, cache.get("film")!!.file.readText())
    }
    @Test fun playbackSnapshotSurvivesCacheReplacementAndOffPreventsExternalSubtitles() = cache { root ->
        val directory = File(root,"cache")
        val cache = SubtitleCache(directory)
        cache.save("film", srt.byteInputStream(), "First", "en")
        cache.configure("film", delayMs = 500)
        val snapshot = File(root,"playback.srt")
        assertTrue(cache.prepareForPlayback("film",snapshot))
        assertTrue(snapshot.readText().contains("00:00:01,500 --> 00:00:02,500"))
        cache.save("film", srt.replace("world","replacement").byteInputStream(), "Second", "en")
        assertTrue(snapshot.readText().contains("Hello world"))
        assertEquals(0,cache.get("film")!!.delayMs)
        cache.configure("film",enabled=false)
        assertFalse(cache.prepareForPlayback("film",File(root,"off.srt")))
        assertNotNull(cache.get("film"))
    }
    @Test fun cacheIsBoundedAndMissingFilesBecomeMisses() = cache { root ->
        val cache=SubtitleCache(root,maxEntries=2)
        cache.save("first",srt.byteInputStream(),"First","en")
        Thread.sleep(5)
        cache.save("second",srt.byteInputStream(),"Second","en")
        Thread.sleep(5)
        cache.save("third",srt.byteInputStream(),"Third","en")
        assertNull(cache.get("first")); assertNotNull(cache.get("second")); assertNotNull(cache.get("third"))
        assertEquals(2,root.listFiles()!!.count { it.extension=="srt" })
        cache.get("third")!!.file.delete()
        assertNull(cache.get("third"))
    }
}
