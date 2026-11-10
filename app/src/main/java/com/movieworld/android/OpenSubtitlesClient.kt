package com.movieworld.android

import kotlinx.serialization.json.*
import java.net.URLEncoder

internal data class SubtitleIdentity(val title: String, val year: String?, val imdbId: String?, val tmdbId: String?)
internal data class SubtitleMatch(val fileId: Long, val label: String, val language: String, val downloads: Int)

/** Transport is injectable so matching and download behavior can be tested without credentials. */
internal class OpenSubtitlesClient(private val api: (String, String, String?) -> String,
    private val download: (String) -> ByteArray) {
    private val json = Json { ignoreUnknownKeys = true }
    fun search(movie: SubtitleIdentity, language: String): List<SubtitleMatch> {
        require(Regex("[a-z]{2,3}(-[a-z]{2})?").matches(language)) { "Enter a language code such as en, es or fr." }
        val params = sortedMapOf("languages" to language, "type" to "movie", "order_by" to "download_count", "order_direction" to "desc")
        val imdb = movie.imdbId?.removePrefix("tt")?.toLongOrNull()?.takeIf { it > 0 }
        val tmdb = movie.tmdbId?.toLongOrNull()?.takeIf { it > 0 }
        when {
            imdb != null -> params["imdb_id"] = imdb.toString()
            tmdb != null -> params["tmdb_id"] = tmdb.toString()
            else -> { params["query"] = movie.title; movie.year?.toIntOrNull()?.let { params["year"] = it.toString() } }
        }
        val query = params.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        val data = json.parseToJsonElement(api("GET", "/subtitles?$query", null)).jsonObject["data"] as? JsonArray ?: return emptyList()
        return buildList {
            for (entry in data) {
                val attrs = (entry as? JsonObject)?.get("attributes") as? JsonObject ?: continue
                val lang = attrs["language"]?.jsonPrimitive?.contentOrNull ?: continue
                if (lang != language || (attrs["nb_cd"]?.jsonPrimitive?.intOrNull ?: 1) > 1) continue
                val feature = attrs["feature_details"] as? JsonObject
                val type = feature?.get("feature_type")?.jsonPrimitive?.contentOrNull
                if (type != null && type != "Movie") continue
                val release = attrs["release"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val count = attrs["download_count"]?.jsonPrimitive?.intOrNull ?: 0
                val files = attrs["files"] as? JsonArray ?: continue
                // Multi-part releases do not fit one complete movie file.
                if (files.size != 1) continue
                val file = files[0] as? JsonObject ?: continue
                val id = file["file_id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: continue
                val name = file["file_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                add(SubtitleMatch(id, release.ifBlank { name }.ifBlank { "Subtitle $id" }.take(250), lang, count))
            }
        }.distinctBy { it.fileId }.take(20)
    }
    fun download(match: SubtitleMatch): ByteArray {
        require(match.fileId > 0)
        val response = json.parseToJsonElement(api("POST", "/download?file_id=${match.fileId}&sub_format=srt", "{}")).jsonObject
        val link = response["link"]?.jsonPrimitive?.contentOrNull ?: error("OpenSubtitles did not return a download link.")
        return download(link)
    }
}
