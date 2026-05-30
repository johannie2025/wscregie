package com.wisesmartchurch.system.db;

import androidx.room.*;
import com.wisesmartchurch.system.model.*;
import java.util.List;

@Dao
public interface BibleDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) void insertMeta(BibleMeta m);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void insertVerses(List<BibleVerse> v);
    @Query("SELECT * FROM bible_meta") List<BibleMeta> getAllMeta();
    @Query("SELECT * FROM bible_meta WHERE isActive=1") List<BibleMeta> getActiveMeta();
    @Query("SELECT * FROM bible_verses WHERE bibleCode=:code AND bookIndex=:book AND chapterIndex=:ch ORDER BY verseIndex") List<BibleVerse> getChapter(String code,int book,int ch);
    @Query("SELECT * FROM bible_verses WHERE bibleCode=:code AND bookIndex=:book AND chapterIndex=:ch AND verseIndex=:v LIMIT 1") BibleVerse getVerse(String code,int book,int ch,int v);
    @Query("SELECT * FROM bible_verses WHERE bibleCode=:code AND text LIKE '%'||:kw||'%' LIMIT 60") List<BibleVerse> searchText(String code,String kw);
    @Query("SELECT COUNT(*) FROM bible_verses WHERE bibleCode=:code AND chapterIndex=:ch AND bookIndex=:book") int chapterVerseCount(String code,int book,int ch);
    @Query("SELECT MAX(chapterIndex)+1 FROM bible_verses WHERE bibleCode=:code AND bookIndex=:book") int bookChapterCount(String code,int book);
    @Query("DELETE FROM bible_verses WHERE bibleCode=:code") void deleteVerses(String code);
    @Query("DELETE FROM bible_meta WHERE code=:code") void deleteMeta(String code);
    @Update void updateMeta(BibleMeta m);
}
