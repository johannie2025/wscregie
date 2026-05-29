package com.wisedesign.elitecast;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.multidex.MultiDex;
import android.content.Context;

/**
 * EliteCast Régie – Application class
 * Wise Design | Prophète Josias | WhatsApp +240 555 445 514
 */
public class EliteCastApp extends Application {

    public static final String CHANNEL_SERVICE = "elitecast_service";
    public static final String PREFS            = "elitecast_prefs";

    // Thèmes disponibles
    public static final int THEME_BLUE_WHITE = 0;
    public static final int THEME_DARK       = 1;
    public static final int THEME_BLUE_RED   = 2;

    private static EliteCastApp instance;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannels();
    }

    public static EliteCastApp get() { return instance; }

    public SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getTheme_() {
        return prefs().getInt("app_theme", THEME_BLUE_WHITE);
    }

    public void setTheme_(int theme) {
        prefs().edit().putInt("app_theme", theme).apply();
    }

    public String getLang() {
        return prefs().getString("app_lang", "fr");
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_SERVICE, "EliteCast Service",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Serveur WebSocket et UDP");
            ((NotificationManager) getSystemService(NotificationManager.class))
                    .createNotificationChannel(ch);
        }
    }
}
