package com.movieworld.android

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** Transactional personal data operations; no network or UI dependencies. */
internal class PersonalDataRepository(private val db: PlaybackDatabase, private val device: String) {
    private val access = db.access()
    fun state(key: String, value: String) = access.saveSyncValue(PlaybackDatabase.SyncState().apply {
        this.key = key; this.value = value
    })
    fun snapshot(owner: Long): PersonalSnapshot = PersonalSnapshot(
        accountId = owner, deviceId = device,
        favourites = access.allFavourites().sortedBy { it.movieId }.map {
            PersonalRecord(it.movieId, it.updated, it.origin.ifBlank { device }, removed = it.removed)
        },
        progress = access.allProgress().sortedBy { it.movieId }.map {
            PersonalRecord(it.movieId, it.updated, it.origin.ifBlank { device }, position = it.position,
                duration = it.duration, finished = it.finished)
        },
    )
    fun merge(snapshots: List<PersonalSnapshot>, owner: Long) {
        snapshots.forEach { it.validate(); require(it.accountId == owner) { "Snapshot belongs to another account" } }
        require(access.syncValue("owner") == owner.toString()) { "Personal data account is not bound" }
        db.runInTransaction {
            val local = snapshot(owner)
            SnapshotCodec.merge(local.favourites + snapshots.flatMap { it.favourites }).forEach { row ->
                val old = access.findFavourite(row.movieId)
                if (old == null || SnapshotCodec.newer(row, PersonalRecord(old.movieId, old.updated, old.origin.ifBlank { device }))) {
                    access.saveFavourite(PlaybackDatabase.Favourite().apply {
                        movieId = row.movieId; updated = row.updated; origin = row.origin; removed = row.removed
                    })
                }
            }
            SnapshotCodec.merge(local.progress + snapshots.flatMap { it.progress }).forEach { row ->
                val old = access.find(row.movieId)
                if (old == null || SnapshotCodec.newer(row, PersonalRecord(old.movieId, old.updated, old.origin.ifBlank { device }))) {
                    access.save(PlaybackDatabase.Progress().apply {
                        movieId = row.movieId; updated = row.updated; origin = row.origin
                        position = row.position; duration = row.duration; finished = row.finished
                    })
                }
            }
        }
    }
    fun bind(owner: Long) {
        val previous = access.syncValue("owner")
        if (previous == owner.toString()) return
        require(owner > 0)
        db.runInTransaction {
            if (previous != null) {
                state("archive-$previous", SnapshotCodec.json.encodeToString(snapshot(previous.toLong())))
                access.deleteFavourites(); access.deleteProgress()
            }
            state("owner", owner.toString())
            val archive = access.syncValue("archive-$owner")
            if (archive != null) {
                val restored = SnapshotCodec.json.decodeFromString<PersonalSnapshot>(archive)
                restored.validate()
                restored.favourites.forEach { row -> access.saveFavourite(PlaybackDatabase.Favourite().apply {
                    movieId = row.movieId; updated = row.updated; origin = row.origin; removed = row.removed
                }) }
                restored.progress.forEach { row -> access.save(PlaybackDatabase.Progress().apply {
                    movieId = row.movieId; updated = row.updated; origin = row.origin
                    position = row.position; duration = row.duration; finished = row.finished
                }) }
            }
        }
    }

}
