package com.wisedesign.elitecast.license;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.wisedesign.elitecast.EliteCastApp;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * ═══════════════════════════════════════════════════════════════
 *  LicenseManager – Gestion des licences EliteCast Régie
 *
 *  3 modes :
 *  ① TRIAL (gratuit, sans login) – fonctions limitées
 *    • Max 3 écrans connectés
 *    • Watermark "ELITECAST TRIAL" sur les écrans
 *    • Pas de téléchargement Bible
 *
 *  ② USER  (login + clé licence) – fonctions complètes
 *    • Durées : 90 / 180 / 365 jours
 *    • Licences générées par l'admin
 *
 *  ③ ADMIN (login: admin | pw: Jesus@_@2026)
 *    • Génère les clés de licence
 *    • Voit toutes les licences actives
 *
 *  Format de clé de licence :
 *    ECPRO-XXXXXX-XXXXXX-[90|180|365]-CHECKSUM
 *
 *  Wise Design | Prophète Josias | WhatsApp +240 555 445 514
 * ═══════════════════════════════════════════════════════════════
 */
public class LicenseManager {

    private static final String TAG = "LicenseManager";

    // ─── Comptes ──────────────────────────────────────
    public static final String ADMIN_LOGIN    = "admin";
    public static final String ADMIN_PASSWORD = "Jesus@_@2026";

    // ─── Préférences ──────────────────────────────────
    private static final String K_LICENSE_KEY  = "license_key";
    private static final String K_LICENSE_EXP  = "license_exp";
    private static final String K_LICENSE_DAYS = "license_days";
    private static final String K_USER_LOGIN   = "user_login";
    private static final String K_IS_LOGGED    = "is_logged";
    private static final String K_IS_ADMIN     = "is_admin";

    // ─── Durées licences (jours) ──────────────────────
    public static final int[] LICENSE_DURATIONS = { 90, 180, 365 };

    // ─── Clé secrète HMAC (embarquée) ─────────────────
    private static final String SECRET = "W1seDes1gn_3lit3C4st_@2026_ProphèteJosias";

    public enum LicenseStatus { TRIAL, VALID, EXPIRED, INVALID }

    public static class LicenseInfo {
        public LicenseStatus status;
        public int daysRemaining;
        public int totalDays;
        public String expiryDate;
        public String licenseKey;
        public String userLogin;
        public boolean isAdmin;

        public boolean isFullAccess() {
            return status == LicenseStatus.VALID || isAdmin;
        }
    }

    private final SharedPreferences prefs;

    public LicenseManager(Context ctx) {
        this.prefs = ctx.getSharedPreferences(EliteCastApp.PREFS, Context.MODE_PRIVATE);
    }

    // ═══════════════════════════════════════════════════
    //  LOGIN
    // ═══════════════════════════════════════════════════
    public boolean loginAdmin(String password) {
        if (ADMIN_PASSWORD.equals(password)) {
            prefs.edit()
                    .putBoolean(K_IS_LOGGED, true)
                    .putBoolean(K_IS_ADMIN, true)
                    .putString(K_USER_LOGIN, ADMIN_LOGIN)
                    .apply();
            return true;
        }
        return false;
    }

    public boolean loginUser(String login, String licenseKey) {
        LicenseStatus st = validateKey(licenseKey);
        if (st == LicenseStatus.VALID) {
            prefs.edit()
                    .putBoolean(K_IS_LOGGED, true)
                    .putBoolean(K_IS_ADMIN, false)
                    .putString(K_USER_LOGIN, login)
                    .putString(K_LICENSE_KEY, licenseKey)
                    .apply();
            return true;
        }
        return false;
    }

    public void logout() {
        prefs.edit()
                .putBoolean(K_IS_LOGGED, false)
                .putBoolean(K_IS_ADMIN, false)
                .remove(K_USER_LOGIN)
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(K_IS_LOGGED, false);
    }

    public boolean isAdmin() {
        return prefs.getBoolean(K_IS_ADMIN, false);
    }

    // ═══════════════════════════════════════════════════
    //  LICENCE – GÉNÉRATION (admin only)
    // ═══════════════════════════════════════════════════
    public String generateLicense(int durationDays) {
        // Timestamp + random pour unicité
        String rand   = randomHex(6).toUpperCase();
        String ts     = randomHex(6).toUpperCase();
        String durStr = String.valueOf(durationDays);

        // Corps de la clé : ECPRO-RANDOM-TS-DURATION
        String body = "ECPRO-" + rand + "-" + ts + "-" + durStr;

        // Checksum HMAC simplifié
        String checksum = hmacShort(body + SECRET);

        String key = body + "-" + checksum;
        Log.i(TAG, "Licence générée: " + key + " (" + durationDays + "j)");
        return key;
    }

    /** Génère N licences d'un coup */
    public String[] generateBatch(int count, int durationDays) {
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            try { Thread.sleep(2); } catch (Exception ignored) {}
            keys[i] = generateLicense(durationDays);
        }
        return keys;
    }

    // ═══════════════════════════════════════════════════
    //  VALIDATION
    // ═══════════════════════════════════════════════════
    public LicenseStatus validateKey(String key) {
        if (key == null || key.trim().isEmpty()) return LicenseStatus.INVALID;
        key = key.trim().toUpperCase();

        // Format : ECPRO-XXXXXX-XXXXXX-[90|180|365]-CHECKSUM(8)
        String[] parts = key.split("-");
        if (parts.length != 5) return LicenseStatus.INVALID;
        if (!"ECPRO".equals(parts[0])) return LicenseStatus.INVALID;

        int days;
        try { days = Integer.parseInt(parts[3]); }
        catch (NumberFormatException e) { return LicenseStatus.INVALID; }

        boolean validDays = false;
        for (int d : LICENSE_DURATIONS) { if (d == days) { validDays = true; break; } }
        if (!validDays) return LicenseStatus.INVALID;

        // Vérifier le checksum
        String body = parts[0]+"-"+parts[1]+"-"+parts[2]+"-"+parts[3];
        String expectedChecksum = hmacShort(body + SECRET);
        if (!expectedChecksum.equals(parts[4])) return LicenseStatus.INVALID;

        // Vérifier l'expiration (stockée localement à l'activation)
        long exp = prefs.getLong(K_LICENSE_EXP + "_" + key.hashCode(), -1L);
        if (exp == -1L) {
            // Première utilisation → activer
            activateLicense(key, days);
            return LicenseStatus.VALID;
        }

        long now = System.currentTimeMillis();
        if (now > exp) return LicenseStatus.EXPIRED;
        return LicenseStatus.VALID;
    }

    private void activateLicense(String key, int days) {
        long expiry = System.currentTimeMillis() + (long) days * 24 * 3600 * 1000L;
        prefs.edit()
                .putLong(K_LICENSE_EXP + "_" + key.hashCode(), expiry)
                .putInt(K_LICENSE_DAYS, days)
                .putString(K_LICENSE_KEY, key)
                .apply();
        Log.i(TAG, "Licence activée: " + days + " jours, expire: " + formatDate(expiry));
    }

    // ═══════════════════════════════════════════════════
    //  INFO
    // ═══════════════════════════════════════════════════
    public LicenseInfo getInfo() {
        LicenseInfo info = new LicenseInfo();
        info.isAdmin   = isAdmin();
        info.userLogin = prefs.getString(K_USER_LOGIN, "");

        if (info.isAdmin) {
            info.status = LicenseStatus.VALID;
            info.daysRemaining = 9999;
            info.totalDays = 9999;
            info.expiryDate = "Admin";
            return info;
        }

        String key = prefs.getString(K_LICENSE_KEY, "");
        if (key.isEmpty()) {
            info.status = LicenseStatus.TRIAL;
            return info;
        }

        info.licenseKey = key;
        info.totalDays  = prefs.getInt(K_LICENSE_DAYS, 0);
        long exp        = prefs.getLong(K_LICENSE_EXP + "_" + key.hashCode(), -1L);

        if (exp == -1L) {
            info.status = LicenseStatus.TRIAL;
            return info;
        }

        long now = System.currentTimeMillis();
        if (now > exp) {
            info.status = LicenseStatus.EXPIRED;
            info.daysRemaining = 0;
        } else {
            info.status = LicenseStatus.VALID;
            info.daysRemaining = (int) ((exp - now) / (24 * 3600 * 1000L));
        }
        info.expiryDate = formatDate(exp);
        return info;
    }

    public boolean isTrialMode() {
        if (!isLoggedIn()) return true;
        return getInfo().status == LicenseStatus.TRIAL;
    }

    // ═══════════════════════════════════════════════════
    //  UTILITAIRES CRYPTO
    // ═══════════════════════════════════════════════════
    private String hmacShort(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02X", hash[i]));
            }
            return sb.toString(); // 8 caractères hex
        } catch (Exception e) {
            return "00000000";
        }
    }

    private String randomHex(int len) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(Integer.toHexString(r.nextInt(16)));
        }
        return sb.toString();
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(new Date(millis));
    }
}
