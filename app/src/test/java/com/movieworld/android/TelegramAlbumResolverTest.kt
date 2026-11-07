package com.movieworld.android

import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class TelegramAlbumResolverTest {
    private fun post(id: Long, album: Long = 7, video: Boolean = false) = TdApi.Message().apply {
        this.id = id
        chatId = 42
        mediaAlbumId = album
        content = if (video) TdApi.MessageVideo().apply {
            this.video = TdApi.Video().apply { mimeType = "video/mp4"; this.video = TdApi.File() }
        } else TdApi.MessagePhoto()
    }

    @Test fun directVideoDoesNotSearch() = runBlocking {
        assertNotNull(resolveAlbumVideo(post(10, video = true)) { error("Unexpected search") })
    }

    @Test fun skipsSeveralPostersUsingOpaqueIds() = runBlocking {
        val messages = listOf(post(100), post(700), post(900, video = true))
        assertNotNull(resolveAlbumVideo(post(10)) { current -> messages.firstOrNull { it.id > current.id } })
    }

    @Test fun neverSelectsNextMoviesVideo() = runBlocking {
        assertNull(resolveAlbumVideo(post(10)) { post(20, album = 8, video = true) })
    }

    @Test fun standalonePosterDoesNotSearch() = runBlocking {
        assertNull(resolveAlbumVideo(post(10, album = 0)) { error("Unexpected search") })
    }

    @Test fun endOfHistoryStopsSearch() = runBlocking {
        assertNull(resolveAlbumVideo(post(10)) { null })
    }

    @Test fun nonAdvancingHistoryStopsSearch() = runBlocking {
        assertNull(resolveAlbumVideo(post(10)) { post(10) })
    }

    @Test fun searchIsBounded() = runBlocking {
        var calls = 0
        assertNull(resolveAlbumVideo(post(10)) { calls++; post(it.id + 10) })
        assertEquals(20, calls)
    }
}
