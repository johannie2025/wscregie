package com.wisedesign.elitecast.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Optionnel : démarrer le service au boot si l'utilisateur est déjà connecté
            if (context.getSharedPreferences("elitecast_prefs", Context.MODE_PRIVATE)
                    .getBoolean("is_logged", false)) {
                Intent svc = new Intent(context, RegieService.class);
                context.startForegroundService(svc);
            }
        }
    }
}
