package com.wisesmartchurch.system.model;
import androidx.room.Entity;import androidx.room.PrimaryKey;
@Entity(tableName="announces")
public class Announce {
    @PrimaryKey(autoGenerate=true) public long id;
    public String title,body,subtitle,bgMode,bgUrl,textColor,category,dateEvent;
    public int displayDuration;public long createdAt;
}
