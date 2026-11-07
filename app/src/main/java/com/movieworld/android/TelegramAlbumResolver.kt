package com.movieworld.android

import org.drinkless.tdlib.TdApi

/** Follow poster links only within their own album, with a bounded search. */
internal suspend fun resolveAlbumVideo(
    original: TdApi.Message,
    nextMessage: suspend (TdApi.Message) -> TdApi.Message?,
): TelegramMedia? {
    telegramVideo(original)?.let { return it }
    if (original.mediaAlbumId == 0L) return null
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
