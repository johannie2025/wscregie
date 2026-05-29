package com.wisedesign.elitecast.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.wisedesign.elitecast.EliteCastApp;
import com.wisedesign.elitecast.R;
import com.wisedesign.elitecast.network.UdpAnnounce;
import com.wisedesign.elitecast.network.WsServer;
import com.wisedesign.elitecast.ui.control.RegieActivity;

import org.java_websocket.WebSocket;

/**
 * Service foreground qui maintient :
 *  • WsServer (Java-WebSocket) port 9000
 *  • UdpAnnounce broadcast port 9002
 * en vie même si l'UI est en arrière-plan.
 */
public class RegieService extends Service implements WsServer.Listener {

    private static final String TAG  = "RegieService";
    public  static final int    WS_PORT  = 9000;
    public  static final int    UDP_PORT = 9002;

    private WsServer    wsServer;
    private UdpAnnounce udpAnnounce;

    // Binder pour communication avec RegieActivity
    public class LocalBinder extends Binder {
        public RegieService getService() { return RegieService.this; }
    }
    private final IBinder binder = new LocalBinder();

    // Listener pour l'UI
    public interface ServiceListener {
        void onScreenConnected(String screenId);
        void onScreenDisconnected(String screenId);
        void onMessage(String screenId, String json);
    }
    private ServiceListener uiListener;
    public void setUiListener(ServiceListener l) { this.uiListener = l; }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification("En attente d'écrans…"));
        startNetworking();
        return START_STICKY;
    }

    private void startNetworking() {
        // Démarrer le serveur WebSocket
        try {
            wsServer = new WsServer(WS_PORT);
            wsServer.setListener(this);
            wsServer.start();
        } catch (Exception e) {
            Log.e(TAG, "WsServer start error: " + e.getMessage());
        }

        // Démarrer l'annonce UDP
        udpAnnounce = new UdpAnnounce(this, WS_PORT, UDP_PORT);
        udpAnnounce.start();

        Log.i(TAG, "✅ Service démarré | WS:" + WS_PORT + " UDP:" + UDP_PORT);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wsServer != null) wsServer.stopServer();
        if (udpAnnounce != null) udpAnnounce.stopAnnounce();
    }

    // ═════════════════════════════════════════ WsServer.Listener
    @Override
    public void onScreenConnected(String screenId, WebSocket conn) {
        updateNotification(wsServer.getScreenCount() + " écran(s) connecté(s)");
        if (uiListener != null) uiListener.onScreenConnected(screenId);
    }

    @Override
    public void onScreenDisconnected(String screenId) {
        updateNotification(wsServer.getScreenCount() + " écran(s) connecté(s)");
        if (uiListener != null) uiListener.onScreenDisconnected(screenId);
    }

    @Override
    public void onMessage(String screenId, String json) {
        if (uiListener != null) uiListener.onMessage(screenId, json);
    }

    @Override
    public void onServerReady() {
        Log.i(TAG, "WS Server prêt");
    }

    // ═════════════════════════════════════════ Diffusion
    public void broadcast(String json) {
        if (wsServer != null) wsServer.broadcast(json);
    }

    public void sendTo(String screenId, String json) {
        if (wsServer != null) wsServer.sendTo(screenId, json);
    }

    public int getScreenCount() {
        return wsServer != null ? wsServer.getScreenCount() : 0;
    }

    public java.util.Set<String> getScreenIds() {
        return wsServer != null ? wsServer.getScreens().keySet() : new java.util.HashSet<>();
    }

    public String getLocalIp() {
        return udpAnnounce != null ? udpAnnounce.getLocalIp() : "—";
    }

    // ═════════════════════════════════════════ Notification
    private Notification buildNotification(String text) {
        Intent i = new Intent(this, RegieActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, EliteCastApp.CHANNEL_SERVICE)
                .setContentTitle("EliteCast Régie")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_broadcast)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        Notification n = buildNotification(text);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, n);
    }
}
