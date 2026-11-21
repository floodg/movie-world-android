package com.movieworld.android

import org.drinkless.tdlib.TdApi

/** Follow poster links only within their own album, with a bounded search. */
internal suspend fun resolveAlbumVideo(
    original: TdApi.Message,
    expectedTitle: String? = null,
    nextMessage: suspend (TdApi.Message) -> TdApi.Message?,
): TelegramMedia? {
    telegramVideo(original)?.let { return it }
    if (original.mediaAlbumId == 0L) {
        // Some catalogue posts are a standalone poster followed by the movie.
        // Never accept an arbitrary neighbouring video or search past this post.
        if (original.content !is TdApi.MessagePhoto || expectedTitle.isNullOrBlank()) return null
        val next = nextMessage(original) ?: return null
        if (next.chatId != original.chatId || next.id <= original.id || next.mediaAlbumId != 0L) return null
        val name = when (val content = next.content) {
            is TdApi.MessageVideo -> content.video.fileName
            is TdApi.MessageDocument -> content.document.fileName
            else -> return null
        }
        fun normalized(value: String) = value.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val stem = normalized(name.substringBeforeLast('.', name))
        val title = normalized(expectedTitle)
        if (!Regex(Regex.escape(title) + " (?:19|20)[0-9]{2}").matches(stem)) return null
        return telegramVideo(next)
    }
    var current = original
    repeat(20) {
        val next = nextMessage(current) ?: return null
        if (next.chatId != original.chatId || next.id <= current.id ||
            next.mediaAlbumId != original.mediaAlbumId) return null
        telegramVideo(next)?.let { return it }
        current = next
    }
    return null
}

private fun telegramVideo(message: TdApi.Message): TelegramMedia? = when (val content = message.content) {
    is TdApi.MessageVideo -> TelegramMedia(content.video.video, content.video.mimeType.ifBlank { "video/mp4" })
    is TdApi.MessageDocument -> {
        val document = content.document
        val mime = document.mimeType.ifBlank {
            when (document.fileName.substringAfterLast('.', "").lowercase()) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                else -> ""
            }
        }
        if (mime.startsWith("video/")) TelegramMedia(document.document, mime) else null
    }
    else -> null
}
