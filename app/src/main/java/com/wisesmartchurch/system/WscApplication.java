package com.wisesmartchurch.system;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import com.wisesmartchurch.system.server.WscServerService;
import com.wisesmartchurch.system.server.WscUdpAnnounceService;
import com.wisesmartchurch.system.db.AppDatabase;

public class WscApplication extends Application {

    private static WscApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Init DB en arrière-plan (évite blocage MainThread)
        new Thread(() -> AppDatabase.getInstance(this)).start();
        // Démarrer les services réseau
        safeStartService(WscServerService.class);
        safeStartService(WscUdpAnnounceService.class);
    }

    private void safeStartService(Class<?> cls) {
        try {
            Intent i = new Intent(this, cls);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Exception e) {
            Log.e("WSC", "safeStartService " + cls.getSimpleName() + ": " + e.getMessage());
        }
    }

    public static WscApplication get() { return instance; }
}
