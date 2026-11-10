package com.movieworld.android

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Bounded, validated SRT sidecars, normalized to UTF-8 for the TV. */
internal object SrtSubtitles {
    private const val LIMIT = 2 * 1024 * 1024
    private val timing = Regex("(\\d{2,3}):([0-5]\\d):([0-5]\\d)[,.](\\d{3})\\s+-->\\s+(\\d{2,3}):([0-5]\\d):([0-5]\\d)[,.](\\d{3})")

    fun prepare(input: InputStream, delayMs: Int = 0): String {
        require(delayMs in -30000..30000) { "Subtitle timing must be within 30 seconds." }
        val buffer = ByteArray(8192)
        val output = java.io.ByteArrayOutputStream()
        while (output.size() <= LIMIT) {
            val count = input.read(buffer, 0, minOf(buffer.size, LIMIT + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        require(bytes.size <= LIMIT) { "Choose an SRT file smaller than 2 MB." }
        val bom = bytes.take(2).map { it.toInt() and 255 }
        val charset = when (bom) {
            listOf(255, 254), listOf(254, 255) -> Charsets.UTF_16
            else -> Charsets.UTF_8
        }
        val text = try {
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            error("Save the subtitles as UTF-8 or UTF-16 SRT and choose them again.")
        }
        require('\u0000' !in text) { "Choose a text SRT file." }
        val blocks = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
            .trim().split(Regex("\n[ \\t]*\n+"))
        val result = mutableListOf<String>()
        for (block in blocks) {
            val lines = block.lines()
            val at = if (lines.firstOrNull()?.trim()?.toIntOrNull() != null) 1 else 0
            val match = timing.matchEntire(lines.getOrNull(at)?.trim().orEmpty())
            require(match != null && lines.size > at + 1 && lines.drop(at + 1).any { it.isNotBlank() }) {
                "Choose a valid SubRip (.srt) subtitle file."
            }
            fun time(offset: Int): Long = match.groupValues[offset].toLong() * 3600000 +
                match.groupValues[offset + 1].toLong() * 60000 +
                match.groupValues[offset + 2].toLong() * 1000 + match.groupValues[offset + 3].toLong()
            val originalStart = time(1)
            val originalEnd = time(5)
            require(originalEnd > originalStart) { "Subtitle cue ends before it starts." }
            val end = originalEnd + delayMs
            if (end <= 0) continue
            val start = (originalStart + delayMs).coerceAtLeast(0)
            fun stamp(ms: Long) = String.format(Locale.US, "%02d:%02d:%02d,%03d",
                ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)
            result += "${result.size + 1}\n${stamp(start)} --> ${stamp(end)}\n${lines.drop(at + 1).joinToString("\n")}"
        }
        require(result.isNotEmpty()) { "No subtitle cues remain at this timing offset." }
        return result.joinToString("\n\n", postfix = "\n")
    }
}
