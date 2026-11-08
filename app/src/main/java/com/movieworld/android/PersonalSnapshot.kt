package com.movieworld.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersonalRecord(
    val movieId: String, val updated: Long, val origin: String,
    val removed: Long? = null, val position: Long = 0, val duration: Long = 0,
    val finished: Boolean = false,
)
@Serializable
data class PersonalSnapshot(
    val format: String = "Movie World Sync", val version: Int = 1,
    val accountId: Long, val deviceId: String,
    val favourites: List<PersonalRecord>, val progress: List<PersonalRecord>,
) {
    fun validate() {
        require(format == "Movie World Sync" && version == 1) { "Unsupported Movie World snapshot" }
        require(accountId > 0 && deviceId.isNotBlank())
        require(favourites.size <= 100000 && progress.size <= 100000)
        for (rows in listOf(favourites, progress)) {
            require(rows.map { it.movieId }.distinct().size == rows.size)
            rows.forEach {
                require(it.movieId.toLongOrNull()?.let { id -> id > 0 } == true)
                require(it.updated >= 0 && it.origin.isNotBlank() && it.position >= 0 && it.duration >= 0 && (it.removed == null || it.removed >= 0))
            }
        }
    }
}
object SnapshotCodec {
    const val MAX_BYTES = 16 * 1024 * 1024
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun newer(a: PersonalRecord, b: PersonalRecord): Boolean =
        a.updated > b.updated || (a.updated == b.updated && a.origin > b.origin)
    fun merge(rows: List<PersonalRecord>): List<PersonalRecord> = rows.groupBy { it.movieId }
        .values.map { versions -> versions.reduce { a, b -> if (newer(b, a)) b else a } }.sortedBy { it.movieId }
}
