package com.wisesmartchurch.system.model;
import androidx.room.Entity;import androidx.room.PrimaryKey;import androidx.room.Ignore;
@Entity(tableName="playlist_items")
public class PlaylistItem {
    @PrimaryKey(autoGenerate=true) public long id;
    public String playlistId;public int position;
    public String type,label,text,ref,bgMode,bgUrl,bgColor,textColor,fontSize,svcType,mediaPath,mediaUrl,thumbnailPath,ltName,ltRole,ltStyle;
    public int overlaySlot;public long duration;public boolean autoAdvance;
    @Ignore public boolean isActive;
    public ProjectionPayload toPayload(){
        ProjectionPayload p=new ProjectionPayload();
        p.type="project";p.text=text;p.ref=ref;
        p.color=textColor!=null?textColor:"#FFFFFF";p.fontSize=fontSize!=null?fontSize:"5.5vw";p.overlaySlot=overlaySlot;
        ProjectionPayload.BgInfo bg=new ProjectionPayload.BgInfo();bg.mode=bgMode!=null?bgMode:"black";bg.url=bgUrl!=null?bgUrl:"";bg.color=bgColor!=null?bgColor:"#000";p.bg=bg;
        if("lower_third".equals(type)){p.lt=new ProjectionPayload.LtInfo();p.lt.show=true;p.lt.name=ltName;p.lt.role=ltRole;p.lt.style=ltStyle!=null?ltStyle:"standard";p.lt.type=svcType!=null?svcType:"pred";}
        return p;
    }
}
