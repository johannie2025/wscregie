package com.wisesmartchurch.system.model;
import androidx.room.*;
@Entity(tableName="bible_verses",indices={@Index(value={"bibleCode","bookIndex","chapterIndex","verseIndex"},unique=true),@Index(value={"bibleCode","bookIndex"})})
public class BibleVerse {
    @PrimaryKey(autoGenerate=true) public long id;
    public String bibleCode;public int bookIndex,chapterIndex,verseIndex;public String text;
}
