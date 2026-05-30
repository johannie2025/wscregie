package com.wisesmartchurch.system.db;

import android.content.Context;
import androidx.room.*;
import com.wisesmartchurch.system.model.*;

@Database(
    entities = {BibleVerse.class, BibleMeta.class, Song.class, Announce.class,
                Playlist.class, PlaylistItem.class, ScreenDevice.class},
    version = 1, exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        ctx.getApplicationContext(),
                        AppDatabase.class, "wsc_db")
                        // Permet les requêtes sur le main thread uniquement
                        // pour les cas exceptionnels (initialisation)
                        // Les DAO sont toujours appelés sur des threads séparés
                        .allowMainThreadQueries()
                        .fallbackToDestructiveMigration()
                        .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract BibleDao    bibleDao();
    public abstract SongDao     songDao();
    public abstract AnnounceDao announceDao();
    public abstract PlaylistDao playlistDao();
    public abstract ScreenDao   screenDao();
}
