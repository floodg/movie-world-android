package com.movieworld.android

import org.junit.Assert.*
import org.junit.Test

class OpenSubtitlesClientTest {
    private val response = """{"data":[
      {"attributes":{"language":"en","release":"The Film BluRay","download_count":500,"nb_cd":1,"feature_details":{"feature_type":"Movie"},"files":[{"file_id":123,"file_name":"film.srt"}]}},
      {"attributes":{"language":"en","release":"Multipart","nb_cd":2,"files":[{"file_id":456}]}},
      {"attributes":{"language":"fr","files":[{"file_id":789}]}},
      {"attributes":{"language":"en","feature_details":{"feature_type":"Episode"},"files":[{"file_id":111}]}}
    ]}"""
    @Test fun prefersImdbThenTmdbThenEncodedTitleAndYear() {
        var path = ""
        val client=OpenSubtitlesClient({ method,p,_ -> assertEquals("GET",method); path=p; response }, { error("Not downloading") })
        val found=client.search(SubtitleIdentity("The Film","2000","tt0123456","999"),"en")
        assertTrue(path.contains("imdb_id=123456")); assertFalse(path.contains("tmdb_id="))
        assertEquals(listOf(123L),found.map { it.fileId })
        client.search(SubtitleIdentity("The Film","2000",null,"999"),"en")
        assertTrue(path.contains("tmdb_id=999"))
        client.search(SubtitleIdentity("A & B","2000",null,null),"en")
        assertTrue(path.contains("query=A+%26+B")); assertTrue(path.contains("year=2000"))
        assertThrows(IllegalArgumentException::class.java) { client.search(SubtitleIdentity("X",null,null,null),"en&bad=1") }
    }
    @Test fun downloadsTheChosenFileAndPropagatesQuotaFailures() {
        var path=""; var url=""
        val client=OpenSubtitlesClient({ method,p,body ->
            assertEquals("POST",method); assertEquals("{}",body); path=p
            """{"link":"https://dl.opensubtitles.com/download/chosen"}"""
        }, { value -> url=value; "captions".toByteArray() })
        assertEquals("captions",String(client.download(SubtitleMatch(123,"Release","en",50))))
        assertTrue(path.contains("file_id=123")); assertTrue(path.contains("sub_format=srt"))
        assertEquals("https://dl.opensubtitles.com/download/chosen",url)
        val limited=OpenSubtitlesClient({ _,_,_-> error("Download quota reached") }, { error("Must not download") })
        assertThrows(IllegalStateException::class.java) { limited.download(SubtitleMatch(123,"Release","en",50)) }
    }
    @Test fun emptyResultsStayEmpty() {
        val client=OpenSubtitlesClient({ _,_,_-> """{"data":[]}""" }, { error("Must not download") })
        assertTrue(client.search(SubtitleIdentity("X",null,null,null),"en").isEmpty())
    }
}
