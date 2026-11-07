package com.movieworld.android;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.List;

@Database(entities = {PlaybackDatabase.Progress.class, PlaybackDatabase.Favourite.class}, version = 2, exportSchema = false)
public abstract class PlaybackDatabase extends RoomDatabase {
    public abstract Access access();
    private static volatile PlaybackDatabase instance;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE progress ADD COLUMN finished INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE progress ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0");
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS favourites (" +
                "movieId TEXT NOT NULL PRIMARY KEY," +
                "updated INTEGER NOT NULL," +
                "removed INTEGER," +
                "dirty INTEGER NOT NULL)"
            );
        }
    };

    public static PlaybackDatabase get(Context context) {
        if (instance == null) {
            synchronized (PlaybackDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.getApplicationContext(), PlaybackDatabase.class, "playback.sqlite"
                    ).addMigrations(MIGRATION_1_2).build();
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
        public boolean finished;
        public boolean dirty;
    }

    @Entity(tableName = "favourites")
    public static class Favourite {
        @PrimaryKey @NonNull public String movieId = "";
        public long updated;
        @Nullable public Long removed;
        public boolean dirty;
    }

    @Dao
    public interface Access {
        @Query("SELECT * FROM progress WHERE finished = 0 AND position >= 15 AND (duration = 0 OR position < duration - 30) ORDER BY updated DESC LIMIT 20")
        List<Progress> continuing();

        @Query("SELECT * FROM progress ORDER BY updated DESC LIMIT :limit")
        List<Progress> recentlyWatched(int limit);

        @Query("SELECT * FROM progress WHERE movieId = :id LIMIT 1")
        Progress find(String id);

        @Query("SELECT * FROM progress WHERE dirty = 1")
        List<Progress> dirtyProgress();

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void save(Progress progress);

        @Query("UPDATE progress SET dirty = 0 WHERE movieId = :id AND updated = :updated")
        void clearProgressDirty(String id, long updated);

        @Query("SELECT movieId FROM favourites WHERE removed IS NULL ORDER BY updated DESC")
        List<String> favouriteIds();

        @Query("SELECT * FROM favourites")
        List<Favourite> allFavourites();

        @Query("SELECT * FROM favourites WHERE dirty = 1")
        List<Favourite> dirtyFavourites();

        @Query("SELECT * FROM favourites WHERE movieId = :id LIMIT 1")
        Favourite findFavourite(String id);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void saveFavourite(Favourite favourite);

        @Query("UPDATE favourites SET dirty = 0 WHERE movieId = :id AND updated = :updated")
        void clearFavouriteDirty(String id, long updated);
    }
}
