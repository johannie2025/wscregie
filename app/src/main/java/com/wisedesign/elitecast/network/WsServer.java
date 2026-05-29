package com.wisedesign.elitecast.network;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serveur WebSocket embarqué – port 9000
 * Compatible avec le WscWsClient du receiver APK Android TV Box.
 *
 * Protocole JSON identique à celui du receiver existant :
 *   { "type": "project", "text": "...", "ref": "...", "color": "#FFF", "bg": {...} }
 *   { "type": "clear" }
 *   { "type": "lt", "name": "...", "title": "..." }
 *   { "type": "bg", "bg": { "color": "#000" } }
 *   { "type": "tag", "label": "..." }
 *   { "type": "screen_id", "id": "TV-1", "role": "MAIN" }  ← identification écran
 */
public class WsServer extends WebSocketServer {

    private static final String TAG = "WsServer";

    public interface Listener {
        void onScreenConnected(String screenId, WebSocket conn);
        void onScreenDisconnected(String screenId);
        void onMessage(String screenId, String json);
        void onServerReady();
    }

    // screenId → WebSocket
    private final ConcurrentHashMap<String, WebSocket> screens = new ConcurrentHashMap<>();
    // WebSocket → screenId
    private final ConcurrentHashMap<WebSocket, String> connToId = new ConcurrentHashMap<>();

    private Listener listener;

    public WsServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
        setConnectionLostTimeout(30);
    }

    public void setListener(Listener l) { this.listener = l; }

    @Override
    public void onStart() {
        Log.i(TAG, "✅ Serveur WebSocket démarré sur le port " + getPort());
        if (listener != null) listener.onServerReady();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake hs) {
        String tempId = "TEMP_" + System.currentTimeMillis();
        connToId.put(conn, tempId);
        Log.i(TAG, "🔌 Nouvelle connexion: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            org.json.JSONObject p = new org.json.JSONObject(message);
            String type = p.optString("type", "");

            if ("screen_id".equals(type)) {
                // L'écran s'identifie
                String sid  = p.optString("id",   "TV-" + System.currentTimeMillis());
                String role = p.optString("role",  "MAIN");

                String oldId = connToId.get(conn);
                if (oldId != null) screens.remove(oldId);

                screens.put(sid, conn);
                connToId.put(conn, sid);
                Log.i(TAG, "📺 Écran identifié: " + sid + " (rôle: " + role + ")");

                if (listener != null) listener.onScreenConnected(sid, conn);

                // Acquitter
                conn.send("{\"type\":\"ack\",\"id\":\"" + sid + "\"}");
            } else {
                // Message entrant (statistiques, feedback, etc.)
                String sid = connToId.getOrDefault(conn, "?");
                if (listener != null) listener.onMessage(sid, message);
            }
        } catch (Exception e) {
            Log.e(TAG, "onMessage parse: " + e.getMessage());
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String sid = connToId.remove(conn);
        if (sid != null) {
            screens.remove(sid);
            Log.i(TAG, "❌ Écran déconnecté: " + sid);
            if (listener != null) listener.onScreenDisconnected(sid);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(TAG, "WS error: " + ex.getMessage());
    }

    // ═══════════════════════════════════════════════════
    //  DIFFUSION
    // ═══════════════════════════════════════════════════

    /** Broadcast à tous les écrans */
    public void broadcast(String json) {
        for (WebSocket ws : screens.values()) {
            if (ws.isOpen()) ws.send(json);
        }
    }

    /** Envoi ciblé vers un écran spécifique */
    public void sendTo(String screenId, String json) {
        WebSocket ws = screens.get(screenId);
        if (ws != null && ws.isOpen()) ws.send(json);
    }

    /** Envoi vers plusieurs écrans */
    public void sendToMany(Collection<String> ids, String json) {
        for (String id : ids) sendTo(id, json);
    }

    public Map<String, WebSocket> getScreens() { return screens; }
    public int getScreenCount() { return screens.size(); }

    public void stopServer() {
        try { stop(1000); } catch (Exception e) { Log.e(TAG, "stop: " + e.getMessage()); }
    }
}
