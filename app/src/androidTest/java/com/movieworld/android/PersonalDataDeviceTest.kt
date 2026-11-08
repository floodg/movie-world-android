package com.movieworld.android

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalDataDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun database() = Room.inMemoryDatabaseBuilder(context, PlaybackDatabase::class.java).build()

    @Test fun accountsAreArchivedAndRestoredWithoutMixing() {
        val db = database()
        try {
            val repo = PersonalDataRepository(db, "phone")
            repo.bind(1)
            db.access().saveFavourite(PlaybackDatabase.Favourite().apply { movieId = "1305"; updated = 1; dirty = true })
            repo.bind(2)
            assertTrue(repo.snapshot(2).favourites.isEmpty())
            db.access().save(PlaybackDatabase.Progress().apply { movieId = "1306"; updated = 2; position = 30 })
            repo.bind(1)
            assertEquals("1305", repo.snapshot(1).favourites.single().movieId)
            assertTrue(repo.snapshot(1).progress.isEmpty())
            repo.bind(2)
            assertTrue(repo.snapshot(2).favourites.isEmpty())
            assertEquals("1306", repo.snapshot(2).progress.single().movieId)
        } finally { db.close() }
    }
    @Test fun oldUploadCannotClearNewerPendingEdits() {
        val db = database()
        try {
            db.access().save(PlaybackDatabase.Progress().apply { movieId = "1305"; updated = 20; dirty = true })
            db.access().clearProgressDirty("1305", 10)
            assertTrue(db.access().find("1305").dirty)
            db.access().clearProgressDirty("1305", 20)
            assertFalse(db.access().find("1305").dirty)
        } finally { db.close() }
    }
    @Test fun remoteDeletionAndResetRestoreAndSurviveDuplicateMerges() {
        val db = database()
        try {
            val repo = PersonalDataRepository(db, "phone")
            repo.bind(1)
            val remote = PersonalSnapshot(accountId = 1, deviceId = "other",
                favourites = listOf(PersonalRecord("1305", 20, "other", removed = 20)),
                progress = listOf(PersonalRecord("1305", 20, "other", position = 0)))
            repo.merge(listOf(remote, remote), 1)
            assertTrue(db.access().favouriteIds().isEmpty())
            assertEquals(0L, db.access().find("1305").position)
            assertEquals("other", db.access().find("1305").origin)
        } finally { db.close() }
    }
    @Test fun foreignAccountSnapshotIsRejectedBeforeAnyWrites() {
        val db = database()
        try {
            val repo = PersonalDataRepository(db, "phone")
            repo.bind(1)
            try {
                repo.merge(listOf(PersonalSnapshot(accountId = 2, deviceId = "other", favourites = emptyList(), progress = emptyList())), 1)
                fail("Expected account mismatch")
            } catch (_: IllegalArgumentException) { }
            assertTrue(repo.snapshot(1).favourites.isEmpty())
        } finally { db.close() }
    }
    @Test fun versionTwoDatabaseMigratesWithoutLosingPersonalData() {
        val name = "sync-migration-test.sqlite"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE progress (movieId TEXT NOT NULL PRIMARY KEY, position INTEGER NOT NULL, duration INTEGER NOT NULL, updated INTEGER NOT NULL, finished INTEGER NOT NULL, dirty INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE favourites (movieId TEXT NOT NULL PRIMARY KEY, updated INTEGER NOT NULL, removed INTEGER, dirty INTEGER NOT NULL)")
            old.execSQL("INSERT INTO progress VALUES ('1305',300,7200,1,0,1)")
            old.execSQL("INSERT INTO favourites VALUES ('1305',1,NULL,1)")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, PlaybackDatabase::class.java, name)
            .addMigrations(PlaybackDatabase.MIGRATION_2_3).build()
        try {
            assertEquals(300L, db.access().find("1305").position)
            assertEquals("", db.access().find("1305").origin)
            assertEquals(listOf("1305"), db.access().favouriteIds())
            PersonalDataRepository(db, "phone").bind(1)
            assertEquals("1", db.access().syncValue("owner"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
