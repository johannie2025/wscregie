package com.wisesmartchurch.system.db;
import androidx.room.*;
import com.wisesmartchurch.system.model.ScreenDevice;
import java.util.List;
@Dao
public interface ScreenDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) void upsert(ScreenDevice s);
    @Update void update(ScreenDevice s);
    @Delete void delete(ScreenDevice s);
    @Query("SELECT * FROM screen_devices ORDER BY name") List<ScreenDevice> getAll();
    @Query("SELECT * FROM screen_devices WHERE isOnline=1") List<ScreenDevice> getOnline();
    @Query("UPDATE screen_devices SET isOnline=:online,lastSeen=:ts WHERE id=:id") void setOnline(String id,boolean online,long ts);
}
