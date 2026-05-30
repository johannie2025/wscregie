package com.wisesmartchurch.system.model;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName="bible_meta")
public class BibleMeta {
    @PrimaryKey public String code;
    public String name,lang;public boolean isActive,isMain;public long downloadedAt;public int verseCount;
}
