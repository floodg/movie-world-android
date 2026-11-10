package com.movieworld.android

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

internal data class CachedSubtitle(val file: File, val label: String, val language: String,
    val enabled: Boolean, val delayMs: Int)

/** Movie ID plus Telegram source keeps title collisions and catalogue re-links isolated. */
internal fun subtitleSourceKey(movieId: String, telegram: String?) = "$movieId\n${telegram.orEmpty()}"

/** Discardable app cache; atomic manifests point to validated, immutable SRT files. */
internal class SubtitleCache(private val directory: File, private val maxBytes: Long = 20L * 1024 * 1024,
    private val maxEntries: Int = 100) {
    companion object { private val lock = Any() }
    private fun key(source: String) = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private fun manifest(source: String) = File(directory, "${key(source)}.properties")
    private fun read(file: File): Properties? = try {
        if (!file.isFile) null else Properties().apply { file.inputStream().use { load(it) } }
    } catch (_: java.io.IOException) { null } catch (_: IllegalArgumentException) { null }
    private fun content(props: Properties): File? {
        val name = props.getProperty("file") ?: return null
        if (!Regex("[a-f0-9]{64}-[a-f0-9-]{36}\\.srt").matches(name)) return null
        return File(directory, name).takeIf { it.isFile && it.length() in 1..(2L * 1024 * 1024) }
    }
    private fun writeManifest(file: File, props: Properties) {
        directory.mkdirs()
        val part = File.createTempFile("manifest-", ".tmp", directory)
        try {
            part.outputStream().use { props.store(it, null) }
            Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { part.delete() }
    }
    fun get(source: String): CachedSubtitle? = synchronized(lock) {
        val file = manifest(source)
        val props = read(file) ?: return@synchronized null
        val srt = content(props) ?: return@synchronized null
        file.setLastModified(System.currentTimeMillis())
        CachedSubtitle(srt, props.getProperty("label", "Cached subtitles"), props.getProperty("language", "en"),
            props.getProperty("enabled") != "false", (props.getProperty("delay")?.toIntOrNull() ?: 0).coerceIn(-30000, 30000))
    }
    fun save(source: String, input: InputStream, label: String, language: String): CachedSubtitle = synchronized(lock) {
        val normalized = SrtSubtitles.prepare(input)
        val bytes = normalized.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 2 * 1024 * 1024) { "Normalized subtitles exceed 2 MB." }
        directory.mkdirs()
        val target = File(directory, "${key(source)}-${UUID.randomUUID()}.srt")
        val file = manifest(source)
        val old = read(file)?.let(::content)
        try {
            target.writeBytes(bytes)
            val props = Properties().apply {
                setProperty("file", target.name); setProperty("label", label.take(250))
                setProperty("language", language.take(20)); setProperty("enabled", "true"); setProperty("delay", "0")
            }
            writeManifest(file, props)
        } catch (error: Exception) { target.delete(); throw error }
        old?.delete()
        prune(file)
        requireNotNull(get(source))
    }
    fun configure(source: String, enabled: Boolean? = null, delayMs: Int? = null): CachedSubtitle? = synchronized(lock) {
        val file = manifest(source)
        val props = read(file) ?: return@synchronized null
        if (enabled != null) props.setProperty("enabled", enabled.toString())
        if (delayMs != null) props.setProperty("delay", delayMs.coerceIn(-30000, 30000).toString())
        writeManifest(file, props)
        get(source)
    }
    /** Copy while holding the cache lock so replacement/eviction cannot race a new cast. */
    fun prepareForPlayback(source: String, destination: File): Boolean = synchronized(lock) {
        val cached = get(source)?.takeIf { it.enabled } ?: return@synchronized false
        val shifted = cached.file.inputStream().use { SrtSubtitles.prepare(it, cached.delayMs) }
        destination.writeText(shifted, Charsets.UTF_8)
        true
    }
    private fun prune(protected: File) {
        val entries = directory.listFiles { f -> f.extension == "properties" }.orEmpty().sortedBy { it.lastModified() }.toMutableList()
        var used = entries.sumOf { read(it)?.let(::content)?.length() ?: 0 }
        for (entry in entries.toList()) {
            if (entry == protected) continue
            if (used <= maxBytes && entries.size <= maxEntries) break
            read(entry)?.let(::content)?.let { used -= it.length(); it.delete() }
            entry.delete(); entries.remove(entry)
        }
        val linked = entries.mapNotNull { read(it)?.getProperty("file") }.toSet()
        directory.listFiles { f -> f.extension == "srt" && f.name !in linked }.orEmpty().forEach { it.delete() }
    }
}
