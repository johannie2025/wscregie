package com.wisesmartchurch.system.db;
import androidx.room.*;
import com.wisesmartchurch.system.model.Song;
import java.util.List;
@Dao
public interface SongDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) long insert(Song s);
    @Update void update(Song s);
    @Delete void delete(Song s);
    @Query("SELECT * FROM songs ORDER BY title") List<Song> getAll();
    @Query("SELECT * FROM songs WHERE language=:lang ORDER BY title") List<Song> getByLang(String lang);
    @Query("SELECT * FROM songs WHERE title LIKE '%'||:q||'%' OR author LIKE '%'||:q||'%'") List<Song> search(String q);
    @Query("SELECT * FROM songs WHERE id=:id LIMIT 1") Song getById(long id);
}
