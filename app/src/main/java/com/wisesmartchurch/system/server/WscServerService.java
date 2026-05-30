package com.wisesmartchurch.system.server;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.utils.NetworkUtils;
import com.wisesmartchurch.system.db.AppDatabase;
import com.wisesmartchurch.system.model.ScreenDevice;
import java.io.IOException;
import java.util.*;

/** Service foreground qui héberge le serveur WebSocket dans l'APK régie */
public class WscServerService extends Service {

    private static final String TAG     = "WscServerSvc";
    private static final int    WS_PORT = 9000;
    private static final String NOTIF_CHANNEL = "wsc_server";
    private static final String ACTION_BROADCAST = "com.wsc.BROADCAST";

    private WscWebSocketServer wsServer;
    private final IBinder binder = new LocalBinder();
    private List<OnClientEventListener> listeners = new ArrayList<>();

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
        startForeground(1, buildNotif("En attente de connexions…"));
        startWsServer();
        return START_STICKY;
    }

    private void startWsServer() {
        try {
            wsServer = new WscWebSocketServer(WS_PORT);
            wsServer.setCallback(new WscWebSocketServer.OnMessageCallback() {
                @Override
                public void onClientConnected(String id, String ip) {
                    Log.i(TAG, "▶ Client: " + id + " IP:" + ip);
                    updateNotif(wsServer.getClientCount() + " écran(s) connecté(s)");
                    notifyListeners();
                    // Register in DB
                    new Thread(() -> {
                        ScreenDevice sd = new ScreenDevice();
                        sd.id = ip; sd.ip = ip; sd.wsPort = WS_PORT;
                        sd.name = "Écran " + ip; sd.isOnline = true;
                        sd.lastSeen = System.currentTimeMillis();
                        sd.showVerse = true; sd.showLt = true;
                        sd.layout = "verse_only";
                        AppDatabase.getInstance(WscServerService.this).screenDao().upsert(sd);
                    }).start();
                }
                @Override
                public void onClientDisconnected(String id) {
                    updateNotif(wsServer.getClientCount() + " écran(s) connecté(s)");
                    notifyListeners();
                }
                @Override
                public void onClientMessage(String id, String json) {
                    Log.d(TAG, "← " + id + ": " + json);
                }
            });
            wsServer.start(10000);
            Log.i(TAG, "✅ Serveur WS démarré port " + WS_PORT);
            String ip = NetworkUtils.getLocalIp(this);
            updateNotif("Serveur actif: " + ip + ":" + WS_PORT);
        } catch (IOException e) {
            Log.e(TAG, "Erreur démarrage serveur", e);
            updateNotif("⚠ Erreur serveur: " + e.getMessage());
        }
    }

    /** Broadcast JSON à tous les écrans */
    public void broadcast(String json) {
        if (wsServer != null) wsServer.broadcast(json);
    }

    /** Broadcast ciblé */
    public void broadcastTo(String targets, String json) {
        if (wsServer != null) wsServer.broadcastToTargets(targets, json);
    }

    public int getClientCount() { return wsServer != null ? wsServer.getClientCount() : 0; }
    public List<String> getConnectedIps() { return wsServer != null ? wsServer.getConnectedIps() : new ArrayList<>(); }
    public String getServerIp() { return NetworkUtils.getLocalIp(this); }
    public int getPort() { return WS_PORT; }

    public void addListener(OnClientEventListener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(OnClientEventListener l) { listeners.remove(l); }

    private void notifyListeners() {
        List<String> ips = getConnectedIps();
        for (OnClientEventListener l : listeners) l.onScreenCount(getClientCount(), ips);
    }

    private void createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(NOTIF_CHANNEL, "WSC Server", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Wise Smart Church — Serveur de diffusion");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotif(String msg) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Wise Smart Church System")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotif(String msg) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(1, buildNotif(msg));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wsServer != null) { wsServer.stop(); wsServer = null; }
    }
}
