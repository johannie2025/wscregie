package com.wisesmartchurch.system;

import android.app.Application;
import android.content.Intent;
import com.wisesmartchurch.system.server.WscServerService;
import com.wisesmartchurch.system.server.WscUdpAnnounceService;
import com.wisesmartchurch.system.db.AppDatabase;

/** Application singleton — démarre les services réseau au boot */
public class WscApplication extends Application {

    private static WscApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Init Room DB
        AppDatabase.getInstance(this);
        // Démarrer le serveur WebSocket et UDP
        startService(new Intent(this, WscServerService.class));
        startService(new Intent(this, WscUdpAnnounceService.class));
    }

    public static WscApplication get() { return instance; }
}
