package com.wisesmartchurch.system.utils;
import android.content.Context;
import android.net.wifi.WifiManager;
import java.net.*;
import java.util.Enumeration;
public class NetworkUtils {
    public static String getLocalIp(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) return (ip&0xff)+"."+((ip>>8)&0xff)+"."+((ip>>16)&0xff)+"."+((ip>>24)&0xff);
            }
        } catch (Exception ignored) {}
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en!=null&&en.hasMoreElements();) {
                for (Enumeration<InetAddress> a = en.nextElement().getInetAddresses(); a.hasMoreElements();) {
                    InetAddress addr = a.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
