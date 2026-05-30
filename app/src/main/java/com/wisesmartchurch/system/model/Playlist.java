package com.wisesmartchurch.system.model;
import androidx.room.Entity;import androidx.room.PrimaryKey;
@Entity(tableName="playlists")
public class Playlist {
    @PrimaryKey(autoGenerate=true) public long id;
    public String uuid,name,serviceType,language;
    public long createdAt,updatedAt;public boolean autoSave;
}
