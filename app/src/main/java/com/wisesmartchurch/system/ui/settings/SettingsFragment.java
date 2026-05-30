package com.wisesmartchurch.system.ui.settings;

import android.content.*;
import android.view.*;
import android.widget.*;
import com.wisesmartchurch.system.R;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public class SettingsFragment {

    public interface OnSettingsChangedListener { void onChanged(SharedPreferences prefs); }

    private final Context ctx;
    private final LinearLayout container;
    private OnSettingsChangedListener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public SettingsFragment(Context ctx, LinearLayout container) {
        this.ctx = ctx; this.container = container;
        render();
    }

    public void setOnSettingsChanged(OnSettingsChangedListener l) { this.listener = l; }

    private void render() {
        SharedPreferences prefs = ctx.getSharedPreferences("wsc_prefs", Context.MODE_PRIVATE);
        container.removeAllViews();

        // ── Church Identity
        addSection("⛪ IDENTITÉ DE L'ÉGLISE");
        EditText etName = addInput("Nom de l'église", prefs.getString("church_name", "Wise Smart Church System"));
        EditText etLogo = addInput("Logo URL ou base64", prefs.getString("church_logo", ""));
        EditText etTag  = addInput("Slogan TV (affiché sous le logo)", prefs.getString("church_tag", "Église — Culte de Louange"));
        EditText etLogoDur = addInput("Durée animation logo (secondes)", String.valueOf(prefs.getInt("logo_dur", 6)));

        // ── Network
        addDivider();
        addSection("🌐 RÉSEAU");
        EditText etBroker = addInput("Broker MQTT (laisser vide = WebSocket local)", prefs.getString("mqtt_broker", ""));
        TextView tvHelp = new TextView(ctx);
        tvHelp.setText("Le serveur WebSocket intégré écoute sur le port 9000.\nLes écrans TV se connectent automatiquement via UDP.\nMQTT optionnel pour connexion Internet.");
        tvHelp.setTextSize(10f); tvHelp.setTextColor(0xFF64748B); tvHelp.setPadding(0,6,0,12);
        container.addView(tvHelp);

        // ── Display
        addDivider();
        addSection("📺 AFFICHAGE TV");
        Spinner spTransition = addSpinner("Transition", new String[]{"Fondu (fade)","Glissement haut","Zoom avant","Aucune"});
        SeekBar sbOverlay = addSeekBar("Opacité overlay fond", prefs.getInt("overlay_opacity", 55));
        Spinner spFont = addSpinner("Police TV", new String[]{"Cinzel (Serif)","Sans-Serif","Monospace"});

        // ── Live Streaming
        addDivider();
        addSection("📡 STREAMING LIVE");
        EditText etRtmpFb  = addInput("RTMP Facebook Live (clé de flux)", prefs.getString("rtmp_fb", ""));
        EditText etRtmpYt  = addInput("RTMP YouTube Live (clé de flux)", prefs.getString("rtmp_yt", ""));
        EditText etRtmpTt  = addInput("RTMP TikTok Live (clé de flux)", prefs.getString("rtmp_tk", ""));
        EditText etRtmpCustom = addInput("RTMP personnalisé (URL complète)", prefs.getString("rtmp_custom", ""));

        // ── Recording
        addDivider();
        addSection("🎙 ENREGISTREMENT");
        EditText etRecPath = addInput("Dossier d'enregistrement", prefs.getString("rec_path", "/sdcard/WiseSmartChurch/Recordings/"));
        Spinner spRecQuality = addSpinner("Qualité vidéo", new String[]{"HD 1080p","HD 720p","SD 480p","Audio seul (MP3)"});

        // ── Save button
        addDivider();
        Button btnSave = new Button(ctx);
        btnSave.setText("💾  Sauvegarder les réglages");
        btnSave.setTextColor(0xFF000000); btnSave.setBackgroundResource(R.drawable.bg_btn_project);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48);
        lp.setMargins(0, 12, 0, 8);
        btnSave.setLayoutParams(lp);
        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putString("church_name",  etName.getText().toString().trim());
            ed.putString("church_logo",  etLogo.getText().toString().trim());
            ed.putString("church_tag",   etTag.getText().toString().trim());
            ed.putInt("logo_dur", parseInt(etLogoDur.getText().toString(), 6));
            ed.putString("mqtt_broker",  etBroker.getText().toString().trim());
            ed.putInt("overlay_opacity", sbOverlay.getProgress());
            ed.putString("rtmp_fb",      etRtmpFb.getText().toString().trim());
            ed.putString("rtmp_yt",      etRtmpYt.getText().toString().trim());
            ed.putString("rtmp_tk",      etRtmpTt.getText().toString().trim());
            ed.putString("rtmp_custom",  etRtmpCustom.getText().toString().trim());
            ed.putString("rec_path",     etRecPath.getText().toString().trim());
            ed.apply();
            Toast.makeText(ctx, "✅ Réglages sauvegardés", Toast.LENGTH_SHORT).show();
            if (listener != null) listener.onChanged(prefs);
        });
        container.addView(btnSave);

        // About
        TextView tvAbout = new TextView(ctx);
        tvAbout.setText("Wise Smart Church System v2.0\nDéveloppé pour la projection pro en milieu ecclésiastique.\nWebSocket · Bible Engine · Lower Thirds · Streaming · Multi-écrans");
        tvAbout.setTextSize(10f); tvAbout.setTextColor(0xFF3D4459);
        tvAbout.setPadding(0, 16, 0, 8); tvAbout.setGravity(android.view.Gravity.CENTER);
        container.addView(tvAbout);
    }

    private void addSection(String title) {
        TextView tv = new TextView(ctx);
        tv.setText(title); tv.setTextSize(10f); tv.setTextColor(0xFF64748B);
        tv.setAllCaps(true); tv.setLetterSpacing(0.1f); tv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 6); tv.setLayoutParams(lp);
        container.addView(tv);
    }

    private EditText addInput(String hint, String value) {
        EditText et = new EditText(ctx);
        et.setHint(hint); et.setText(value);
        et.setTextColor(0xFFE2E8F0); et.setTextColorHint(0xFF64748B);
        et.setTextSize(12f); et.setBackgroundResource(R.drawable.bg_search);
        et.setPadding(10, 8, 10, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 6); et.setLayoutParams(lp);
        container.addView(et);
        return et;
    }

    private Spinner addSpinner(String label, String[] items) {
        TextView tv = new TextView(ctx);
        tv.setText(label); tv.setTextSize(10f); tv.setTextColor(0xFF64748B); tv.setPadding(0, 4, 0, 2);
        container.addView(tv);
        Spinner sp = new Spinner(ctx);
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setBackgroundResource(R.drawable.bg_btn_icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 38);
        lp.setMargins(0, 0, 0, 8); sp.setLayoutParams(lp);
        container.addView(sp);
        return sp;
    }

    private SeekBar addSeekBar(String label, int value) {
        TextView tv = new TextView(ctx);
        tv.setText(label + ": " + value + "%"); tv.setTextSize(10f); tv.setTextColor(0xFF64748B); tv.setPadding(0, 4, 0, 2);
        container.addView(tv);
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(100); sb.setProgress(value);
        sb.getProgressDrawable().setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFF0C040, android.graphics.PorterDuff.Mode.SRC_IN));
        sb.getThumb().setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFF0C040, android.graphics.PorterDuff.Mode.SRC_IN));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean b) { tv.setText(label + ": " + p + "%"); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8); sb.setLayoutParams(lp);
        container.addView(sb);
        return sb;
    }

    private void addDivider() {
        View v = new View(ctx); v.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, 10, 0, 10); v.setLayoutParams(lp);
        container.addView(v);
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }
}
