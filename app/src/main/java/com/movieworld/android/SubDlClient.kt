package com.movieworld.android

import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

internal class SubDlClient(private val get: (String) -> String, private val fetch: (String) -> ByteArray) {
    fun search(movie: SubtitleIdentity, language: String): List<SubtitleMatch> {
        val params = linkedMapOf("type" to "movie", "languages" to language.uppercase(), "subs_per_page" to "30")
        when {
            !movie.imdbId.isNullOrBlank() -> params["imdb_id"] = movie.imdbId
            !movie.tmdbId.isNullOrBlank() -> params["tmdb_id"] = movie.tmdbId
            else -> { params["film_name"] = movie.title; movie.year?.let { params["year"] = it } }
        }
        val query = params.entries.joinToString("&") { (k,v) -> "$k=${URLEncoder.encode(v,"UTF-8")}" }
        val data = Json.parseToJsonElement(get(query)).jsonObject
        check(data["status"]?.jsonPrimitive?.booleanOrNull == true) { "SubDL search failed. Check your API key or try later." }
        return (data["subtitles"] as? JsonArray).orEmpty().mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val returnedUrl = row["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            // SubDL can append api_key to relative links. Keep credentials out of result metadata;
            // the transport authenticates the download host with an encrypted settings key.
            val url = returnedUrl.substringBefore('?')
            if (!Regex("/subtitle/[A-Za-z0-9_-]+\\.zip").matches(url)) return@mapNotNull null
            val lang = row["language"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!lang.equals(language, ignoreCase = true)) return@mapNotNull null
            val label = row["release_name"]?.jsonPrimitive?.contentOrNull ?: row["name"]?.jsonPrimitive?.contentOrNull ?: "SubDL subtitle"
            SubtitleMatch(1, "SubDL · ${label.take(230)}", language, 0, "subdl", url)
        }.distinctBy { it.url }.take(30)
    }

    fun download(match: SubtitleMatch): ByteArray {
        require(Regex("/subtitle/[A-Za-z0-9_-]+\\.zip").matches(match.url))
        val files = mutableListOf<ByteArray>()
        ZipInputStream(ByteArrayInputStream(fetch("https://dl.subdl.com${match.url}"))).use { zip ->
            var count = 0
            var total = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 64) { "Subtitle archive contains too many files." }
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 4 * 1024 * 1024) { "Subtitle archive is too large." }
                    if (!entry.isDirectory && entry.name.endsWith(".srt", true)) out.write(buffer, 0, n)
                }
                if (!entry.isDirectory && entry.name.endsWith(".srt", true)) files.add(out.toByteArray())
            }
        }
        require(files.size == 1) { "Choose a single-file SRT release, or import an SRT manually." }
        return files.single()
    }
}
