package com.wisesmartchurch.system.db;
import androidx.room.*;
import com.wisesmartchurch.system.model.Announce;
import java.util.List;
@Dao
public interface AnnounceDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) long insert(Announce a);
    @Update void update(Announce a);
    @Delete void delete(Announce a);
    @Query("SELECT * FROM announces ORDER BY createdAt DESC") List<Announce> getAll();
    @Query("SELECT * FROM announces WHERE category=:cat ORDER BY createdAt DESC") List<Announce> getByCategory(String cat);
}
