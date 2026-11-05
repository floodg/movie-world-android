package com.movieworld.android;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.util.List;

@Database(entities = {PlaybackDatabase.Progress.class}, version = 1, exportSchema = false)
public abstract class PlaybackDatabase extends RoomDatabase {
    public abstract Access access();
    private static volatile PlaybackDatabase instance;

    public static PlaybackDatabase get(Context context) {
        if (instance == null) {
            synchronized (PlaybackDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.getApplicationContext(), PlaybackDatabase.class, "playback.sqlite"
                    ).build();
                }
            }
        }
        return instance;
    }

    @Entity(tableName = "progress")
    public static class Progress {
        @PrimaryKey @NonNull public String movieId = "";
        public long position;
        public long duration;
        public long updated;
    }

    @Dao
    public interface Access {
        @Query("SELECT * FROM progress WHERE position >= 15 AND (duration = 0 OR position < duration - 30) ORDER BY updated DESC LIMIT 20")
        List<Progress> continuing();
        @Query("SELECT * FROM progress WHERE movieId = :id LIMIT 1")
        Progress find(String id);
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void save(Progress progress);
    }
}
