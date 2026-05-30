package com.wisesmartchurch.system.db;
import androidx.room.*;
import com.wisesmartchurch.system.model.*;
import java.util.List;
@Dao
public interface PlaylistDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) long insertPlaylist(Playlist p);
    @Update void updatePlaylist(Playlist p);
    @Delete void deletePlaylist(Playlist p);
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC") List<Playlist> getAllPlaylists();
    @Query("SELECT * FROM playlists WHERE id=:id LIMIT 1") Playlist getPlaylist(long id);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void insertItems(List<PlaylistItem> items);
    @Query("SELECT * FROM playlist_items WHERE playlistId=:uuid ORDER BY position") List<PlaylistItem> getItems(String uuid);
    @Query("DELETE FROM playlist_items WHERE playlistId=:uuid") void clearItems(String uuid);
    @Query("UPDATE playlist_items SET position=:pos WHERE id=:id") void updatePosition(long id,int pos);
}
