package com.wisesmartchurch.system.server;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.wisesmartchurch.system.db.AppDatabase;
import com.wisesmartchurch.system.model.ScreenDevice;
import com.wisesmartchurch.system.utils.NetworkUtils;
import java.io.IOException;
import java.util.*;

public class WscServerService extends Service {

    private static final String TAG          = "WscServerSvc";
    public  static final int    WS_PORT      = 9000;
    private static final String NOTIF_CHANNEL = "wsc_server";

    private WscWebSocketServer wsServer;
    private final IBinder binder = new LocalBinder();
    private final List<OnClientEventListener> listeners = new ArrayList<>();

    public interface OnClientEventListener {
        void onClientConnected(String id, String ip);
        void onClientDisconnected(String id);
        void onScreenCount(int count, List<String> ips);
    }

    public class LocalBinder extends Binder {
        public WscServerService getService() { return WscServerService.this; }
    }

    @Override public IBinder onBind(Intent i) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotifChannel();
        try {
            startForeground(1, buildNotif("Wise Smart Church — Serveur actif"));
        } catch (Exception e) {
            Log.w(TAG, "startForeground failed: " + e.getMessage());
        }
        new Thread(this::startWsServer, "WscServerInit").start();
        return START_STICKY;
    }

    private void startWsServer() {
        try {
            if (wsServer != null) { try { wsServer.stop(); } catch (Exception ignored) {} }
            wsServer = new WscWebSocketServer(WS_PORT);
            wsServer.setCallback(new WscWebSocketServer.OnMessageCallback() {
                @Override
                public void onClientConnected(String id, String ip) {
                    Log.i(TAG, "📺 Client: " + ip);
                    updateNotif(getClientCount() + " écran(s) connecté(s)");
                    notifyListeners();
                    // Enregistrer en DB (thread séparé)
                    new Thread(() -> {
                        try {
                            ScreenDevice sd = new ScreenDevice();
                            sd.id = ip; sd.ip = ip; sd.wsPort = WS_PORT;
                            sd.name = "Écran " + ip; sd.isOnline = true;
                            sd.lastSeen = System.currentTimeMillis();
                            sd.showVerse = true; sd.showLt = true;
                            sd.layout = "verse_only";
                            AppDatabase.getInstance(WscServerService.this).screenDao().upsert(sd);
                        } catch (Exception e) { Log.w(TAG, "DB error: " + e.getMessage()); }
                    }).start();
                }

                @Override
                public void onClientDisconnected(String id) {
                    updateNotif(getClientCount() + " écran(s)");
                    notifyListeners();
                }

                @Override
                public void onClientMessage(String id, String json) {
                    Log.d(TAG, "← " + json.substring(0, Math.min(json.length(), 80)));
                }
            });
            wsServer.start(15000);
            String ip = NetworkUtils.getLocalIp(this);
            Log.i(TAG, "✅ WS Server port " + WS_PORT + " IP:" + ip);
            updateNotif("Actif: " + ip + ":" + WS_PORT);
        } catch (IOException e) {
            Log.e(TAG, "WS Server error", e);
            updateNotif("⚠ Erreur: " + e.getMessage());
        }
    }

    public void broadcast(String json) {
        if (wsServer != null) wsServer.broadcast(json);
    }

    public void broadcastTo(String target, String json) {
        if (wsServer != null) wsServer.broadcastToTargets(target, json);
    }

    public int          getClientCount()  { return wsServer != null ? wsServer.getClientCount() : 0; }
    public List<String> getConnectedIps() { return wsServer != null ? wsServer.getConnectedIps() : new ArrayList<>(); }
    public String       getServerIp()     { return NetworkUtils.getLocalIp(this); }
    public int          getPort()         { return WS_PORT; }

    public void addListener(OnClientEventListener l)    { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(OnClientEventListener l) { listeners.remove(l); }

    private void notifyListeners() {
        List<String> ips = getConnectedIps();
        int cnt = getClientCount();
        for (OnClientEventListener l : listeners) {
            try { l.onScreenCount(cnt, ips); } catch (Exception ignored) {}
        }
    }

    private void createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL, "WSC Server", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Wise Smart Church — Serveur de diffusion");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotif(String msg) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) intent = new Intent();
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Wise Smart Church System")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotif(String msg) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(1, buildNotif(msg));
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wsServer != null) { try { wsServer.stop(); } catch (Exception ignored) {} wsServer = null; }
    }
}
