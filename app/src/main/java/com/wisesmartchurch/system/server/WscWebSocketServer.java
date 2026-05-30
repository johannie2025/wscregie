package com.wisesmartchurch.system.server;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Serveur WebSocket embarqué dans l'APK régie.
 * Port 9000 — protocole WSS identique à l'APK Display.
 * Gère N clients connectés (multi-écrans).
 */
public class WscWebSocketServer extends NanoWSD {

    private static final String TAG = "WscWsServer";
    private final ConcurrentHashMap<String, WscWebSocket> clients = new ConcurrentHashMap<>();
    private OnMessageCallback callback;

    public interface OnMessageCallback {
        void onClientConnected(String clientId, String ip);
        void onClientDisconnected(String clientId);
        void onClientMessage(String clientId, String json);
    }

    public WscWebSocketServer(int port) throws IOException {
        super(port);
    }

    public void setCallback(OnMessageCallback cb) { this.callback = cb; }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        String ip = handshake.getRemoteIpAddress();
        String id = ip + "_" + System.currentTimeMillis();
        WscWebSocket ws = new WscWebSocket(handshake, id, ip);
        clients.put(id, ws);
        Log.i(TAG, "📺 Client connecté: " + id + " ← " + ip);
        if (callback != null) callback.onClientConnected(id, ip);
        return ws;
    }

    /** Envoie un JSON à TOUS les clients connectés */
    public void broadcast(String json) {
        for (Map.Entry<String, WscWebSocket> e : clients.entrySet()) {
            try {
                if (e.getValue().isOpen()) e.getValue().send(json);
            } catch (IOException ex) {
                Log.w(TAG, "broadcast error: " + ex.getMessage());
            }
        }
    }

    /** Envoie à un client spécifique par ID écran */
    public void sendTo(String clientId, String json) {
        WscWebSocket ws = clients.get(clientId);
        if (ws != null && ws.isOpen()) {
            try { ws.send(json); } catch (IOException e) { Log.w(TAG, "sendTo error"); }
        }
    }

    /** Envoie aux clients ayant un rôle/tag spécifique */
    public void broadcastToTargets(String targets, String json) {
        if ("all".equals(targets) || targets == null) { broadcast(json); return; }
        for (WscWebSocket ws : clients.values()) {
            if (ws.isOpen() && ws.matchesTarget(targets)) {
                try { ws.send(json); } catch (IOException ignored) {}
            }
        }
    }

    public int getClientCount() { return clients.size(); }

    public List<String> getConnectedIps() {
        List<String> ips = new ArrayList<>();
        for (WscWebSocket ws : clients.values()) { if (ws.isOpen()) ips.add(ws.getClientIp()); }
        return ips;
    }

    // ── Inner WebSocket per client ──────────────────────
    class WscWebSocket extends WebSocket {
        private final String clientId;
        private final String clientIp;
        private String screenId = "";
        private String screenRole = "main";

        WscWebSocket(IHTTPSession hs, String id, String ip) {
            super(hs); this.clientId = id; this.clientIp = ip;
        }

        @Override protected void onOpen() { /* already handled in openWebSocket */ }

        @Override
        protected void onMessage(WebSocketFrame frame) {
            try {
                String txt = frame.getTextPayload();
                // Parse client identification message
                JSONObject j = new JSONObject(txt);
                if ("identify".equals(j.optString("type"))) {
                    screenId   = j.optString("screenId",  "screen");
                    screenRole = j.optString("role",      "main");
                    Log.i(TAG, "Screen ID: " + screenId + " role:" + screenRole);
                    // Send ack
                    JSONObject ack = new JSONObject();
                    ack.put("type", "ack");
                    ack.put("serverVersion", "2.0.0");
                    ack.put("clientId", clientId);
                    send(ack.toString());
                }
                if (callback != null) callback.onClientMessage(clientId, txt);
            } catch (Exception e) { Log.w(TAG, "onMessage: " + e.getMessage()); }
        }

        @Override
        protected void onClose(NanoWSD.WebSocketFrame.CloseCode code, String reason, boolean init) {
            clients.remove(clientId);
            Log.i(TAG, "Client déconnecté: " + clientId);
            if (callback != null) callback.onClientDisconnected(clientId);
        } // <--- Cette accolade fermante était manquante!

        @Override 
        protected void onPong(WebSocketFrame f) {}

        @Override 
        protected void onException(IOException e) {
            clients.remove(clientId);
            if (callback != null) callback.onClientDisconnected(clientId);
        }

        boolean matchesTarget(String target) {
            return screenId.equalsIgnoreCase(target) || screenRole.equalsIgnoreCase(target);
        }

        String getClientIp() { return clientIp; }
        String getScreenId()  { return screenId; }
    }
}
}