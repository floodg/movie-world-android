package com.movieworld.android

/** TDLib may replace punctuation in document filenames with underscores. */
internal object SyncAttachment {
    fun isSnapshot(filename: String): Boolean = filename.replace('_', '-').startsWith("movie-world-sync-v1-") && filename.endsWith(".json")
    fun isDeviceSnapshot(filename: String, caption: String, device: String): Boolean =
        isSnapshot(filename) && filename.replace('_', '-').startsWith("movie-world-sync-v1-$device-") &&
            caption == "Movie World Sync v1 · $device"
}

/** Older backups can be deleted only when retained copies contain every record at the same or newer version. */
internal fun backupsCover(retained: List<PersonalSnapshot>, older: PersonalSnapshot): Boolean {
    fun covers(current: List<PersonalRecord>, old: List<PersonalRecord>): Boolean {
        val merged = SnapshotCodec.merge(current).associateBy { it.movieId }
        return old.all { row -> merged[row.movieId]?.let { it == row || SnapshotCodec.newer(it, row) } == true }
    }
    return covers(retained.flatMap { it.favourites }, older.favourites) &&
        covers(retained.flatMap { it.progress }, older.progress)
}
