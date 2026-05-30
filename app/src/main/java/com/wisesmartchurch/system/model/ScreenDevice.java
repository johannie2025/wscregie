package com.wisesmartchurch.system.model;
import androidx.room.*;
@Entity(tableName="screen_devices")
public class ScreenDevice {
    @PrimaryKey public String id;   // e.g. "TV-1", "chorale", "podium"
    public String ip;public int wsPort;public String name;public String role; // main|chorale|back|front
    public boolean isOnline;public long lastSeen;
    public String layout;           // verse_only | lt_only | verse_lt | camera | full
    public boolean showVerse,showLt,showCamera,showClock,showPlan;
    public int overlayConfig;       // bitmask 1-4
}
