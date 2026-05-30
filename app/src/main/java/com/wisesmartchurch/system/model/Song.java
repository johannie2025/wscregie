package com.wisesmartchurch.system.model;
import androidx.room.Entity;import androidx.room.PrimaryKey;import androidx.room.Ignore;import java.util.*;
@Entity(tableName="songs")
public class Song {
    @PrimaryKey(autoGenerate=true) public long id;
    public String title,author,language,lyrics,bgMode,bgUrl,textColor;
    public long createdAt,updatedAt;
    public List<Strophe> parseStrophes(){
        List<Strophe> list=new ArrayList<>();
        if(lyrics==null||lyrics.trim().isEmpty())return list;
        String[]blocks=lyrics.split("\n\\s*\n");
        for(int i=0;i<blocks.length;i++){String b=blocks[i].trim();if(!b.isEmpty()){Strophe s=new Strophe();s.index=i;s.text=b;s.label=detectLabel(b,i);list.add(s);}}
        return list;
    }
    private String detectLabel(String b,int i){
        String f=b.split("\n")[0].toLowerCase();
        if(f.startsWith("refrain")||f.startsWith("chorus")||f.startsWith("coro"))return"🎵 Refrain";
        if(f.startsWith("bridge")||f.startsWith("pont"))return"🌉 Bridge";
        if(f.startsWith("intro"))return"▶ Intro";
        return"📖 Strophe "+(i+1);
    }
    public static class Strophe{public int index;public String label,text;}
}
