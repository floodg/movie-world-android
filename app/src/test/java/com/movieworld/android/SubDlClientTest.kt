package com.movieworld.android

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SubDlClientTest {
    private fun zip(vararg names: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> names.forEach {
            zip.putNextEntry(ZipEntry(it)); zip.write("1\n00:00:00,000 --> 00:00:01,000\nHello\n".toByteArray()); zip.closeEntry()
        } }
        return out.toByteArray()
    }
    @Test fun searchesByIdentityAndKeepsOnlyRequestedLanguage() {
        var query = ""
        val client = SubDlClient({ q -> query = q; """{"status":true,"subtitles":[
            {"url":"/subtitle/123-456.zip?api_key=fixture-secret","language":"EN","release_name":"Test"},
            {"url":"/subtitle/456-789.zip","language":"ES"},
            {"url":"https://untrusted.test/file.zip","language":"EN"}]}""" }, { byteArrayOf() })
        val found = client.search(SubtitleIdentity("Movie", "2000", "tt12345", null), "en")
        assertTrue(query.contains("imdb_id=tt12345"))
        assertTrue(query.contains("languages=EN"))
        assertEquals(1, found.size)
        assertEquals("subdl", found.single().provider)
        assertEquals("/subtitle/123-456.zip", found.single().url)
        assertFalse(found.single().toString().contains("fixture-secret"))
    }
    @Test fun readsSrtWithoutExtractingArchivePaths() {
        val client = SubDlClient({ "" }, { address ->
            assertEquals("https://dl.subdl.com/subtitle/123.zip", address)
            zip("../../captions.srt")
        })
        val srt = client.download(SubtitleMatch(1,"Test","en",0,"subdl","/subtitle/123.zip"))
        assertTrue(String(srt).contains("Hello"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMultipartArchives() {
        SubDlClient({ "" }, { zip("part1.srt", "part2.srt") }).download(SubtitleMatch(1,"Test","en",0,"subdl","/subtitle/123.zip"))
    }
    @Test(expected = IllegalStateException::class) fun reportsApiFailure() {
        SubDlClient({ """{"status":false,"error":"quota"}""" }, { byteArrayOf() })
            .search(SubtitleIdentity("Movie",null,null,null), "en")
    }
}
