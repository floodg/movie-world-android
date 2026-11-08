package com.movieworld.android

import org.junit.Assert.*
import org.junit.Test

class SyncAttachmentTest {
    private val device = "f5ff6a6a-2fab-4031-944d-8c7be5478ea8"
    private val caption = "Movie World Sync v1 · $device"
    @Test fun uploadedUnderscoreNamesAreRecognized() {
        assertTrue(SyncAttachment.isDeviceSnapshot("movie_world_sync_v1_${device.replace('-', '_')}_d3a6eeda.json", caption, device))
    }
    @Test fun originalHyphenNamesRemainCompatible() {
        assertTrue(SyncAttachment.isDeviceSnapshot("movie-world-sync-v1-$device-backup.json", caption, device))
    }
    @Test fun unrelatedFilesAreNotSnapshots() {
        assertFalse(SyncAttachment.isSnapshot("holiday.json"))
        assertFalse(SyncAttachment.isSnapshot("movie_world_sync_v1_backup.jpg"))
    }
    @Test fun otherDevicesAreNotCleanupCandidates() {
        assertFalse(SyncAttachment.isDeviceSnapshot("movie-world-sync-v1-other-backup.json", caption, device))
        assertFalse(SyncAttachment.isDeviceSnapshot("movie-world-sync-v1-$device-backup.json", "Other document", device))
    }
    private fun snapshot(rows: List<PersonalRecord>) = PersonalSnapshot(accountId = 1, deviceId = device, favourites = rows, progress = emptyList())
    @Test fun uniqueOlderDataPreventsDeletion() {
        assertFalse(backupsCover(listOf(snapshot(emptyList())), snapshot(listOf(PersonalRecord("1305", 10, device)))))
    }
    @Test fun newerRetainedRecordsCoverOldBackups() {
        assertTrue(backupsCover(listOf(snapshot(listOf(PersonalRecord("1305", 20, device, removed = 20)))),
            snapshot(listOf(PersonalRecord("1305", 10, device)))))
    }
    @Test fun sameVersionDifferentContentPreventsDeletion() {
        assertFalse(backupsCover(listOf(snapshot(listOf(PersonalRecord("1305", 10, device, removed = 10)))),
            snapshot(listOf(PersonalRecord("1305", 10, device)))))
    }
}
