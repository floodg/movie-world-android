package com.movieworld.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackDeviceTest {
    @Test fun nativeTelegramLibraryLoadsWithoutAccountCredentials() {
        System.loadLibrary("tdjni")
        Client.execute(TdApi.SetLogVerbosityLevel(0))
        val version = Client.execute(TdApi.GetOption("version"))
        assertTrue(version is TdApi.OptionValueString)
    }

    @Test fun xmlAndWatchProgressWorkOnAndroid() {
        assertEquals("PLAYING", xml("<result><CurrentTransportState>PLAYING</CurrentTransportState></result>")
            .text("CurrentTransportState"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, PlaybackDatabase::class.java).build()
        try {
            val entry = PlaybackDatabase.Progress().apply {
                movieId = "sample"
                position = 300
                duration = 7200
                updated = 1
            }
            db.access().save(entry)
            assertEquals(300L, db.access().find("sample").position)
            assertEquals(1, db.access().continuing().size)
            entry.position = 7190
            db.access().save(entry)
            assertTrue(db.access().continuing().isEmpty())
        } finally { db.close() }
    }
}
