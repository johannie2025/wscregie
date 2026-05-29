package com.wisedesign.elitecast.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.elitecast.R;
import com.wisedesign.elitecast.license.LicenseManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Panneau Admin – Génération de licences
 * Accessible uniquement avec login admin (Jesus@_@2026)
 *
 * Fonctions :
 *  • Générer 1 à 10 licences (90 / 180 / 365 jours)
 *  • Copier / partager les clés
 *  • Afficher les stats d'utilisation
 *
 * Wise Design | Prophète Josias | WhatsApp +240 555 445 514
 */
public class AdminActivity extends AppCompatActivity {

    private LicenseManager licenseManager;

    private RadioGroup   rgDuration;
    private NumberPicker npCount;
    private LinearLayout generatedKeys;
    private TextView     tvAdminInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        licenseManager = new LicenseManager(this);

        // Vérifier accès admin
        if (!licenseManager.isAdmin()) {
            Toast.makeText(this, "Accès refusé", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupUI();
    }

    private void bindViews() {
        rgDuration   = findViewById(R.id.rg_duration);
        npCount      = findViewById(R.id.np_count);
        generatedKeys= findViewById(R.id.generated_keys);
        tvAdminInfo  = findViewById(R.id.tv_admin_info);
    }

    private void setupUI() {
        // Titre
        if (tvAdminInfo != null) {
            tvAdminInfo.setText(
                    "👑 Panneau Administrateur\n" +
                    "Wise Design | EliteCast Régie\n" +
                    "📞 WhatsApp +240 555 445 514");
        }

        // NumberPicker count (1–10)
        if (npCount != null) {
            npCount.setMinValue(1);
            npCount.setMaxValue(10);
            npCount.setValue(1);
        }

        // Bouton Générer
        Button btnGenerate = findViewById(R.id.btn_generate);
        if (btnGenerate != null) {
            btnGenerate.setOnClickListener(v -> generateLicenses());
        }

        // Bouton Tout copier
        Button btnCopyAll = findViewById(R.id.btn_copy_all);
        if (btnCopyAll != null) {
            btnCopyAll.setVisibility(View.GONE);
            btnCopyAll.setOnClickListener(v -> copyAll());
        }

        // Bouton retour
        View btnBack = findViewById(R.id.btn_back_admin);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void generateLicenses() {
        int duration = getSelectedDuration();
        int count    = npCount != null ? npCount.getValue() : 1;

        if (generatedKeys != null) generatedKeys.removeAllViews();

        String[] keys = licenseManager.generateBatch(count, duration);
        List<String> keyList = new ArrayList<>();

        for (String key : keys) {
            keyList.add(key);
            addKeyRow(key, duration);
        }

        // Afficher le bouton "Tout copier"
        Button btnCopyAll = findViewById(R.id.btn_copy_all);
        if (btnCopyAll != null) {
            btnCopyAll.setVisibility(View.VISIBLE);
            btnCopyAll.setTag(String.join("\n", keyList));
        }

        Toast.makeText(this, "✅ " + count + " licence(s) générée(s) – " + duration + " jours",
                Toast.LENGTH_SHORT).show();
    }

    private void addKeyRow(String key, int duration) {
        if (generatedKeys == null) return;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(12, 10, 12, 10);
        row.setBackgroundResource(R.drawable.card_bg);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 4, 0, 4);
        row.setLayoutParams(rowLp);

        // Clé de licence
        TextView tvKey = new TextView(this);
        tvKey.setText(key);
        tvKey.setTextSize(12f);
        tvKey.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvKey.setTextColor(0xFF42A5F5);
        tvKey.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Badge durée
        TextView tvDur = new TextView(this);
        tvDur.setText(duration + "j");
        tvDur.setTextSize(10f);
        tvDur.setPadding(6, 3, 6, 3);
        tvDur.setTextColor(0xFFFFFFFF);
        tvDur.setBackgroundResource(duration == 365 ? R.drawable.badge_gold
                : duration == 180 ? R.drawable.badge_blue
                : R.drawable.badge_grey);

        // Bouton copie
        Button btnCopy = new Button(this);
        btnCopy.setText("📋");
        btnCopy.setTextSize(14f);
        btnCopy.setPadding(8, 4, 8, 4);
        btnCopy.setAllCaps(false);
        btnCopy.setBackgroundResource(android.R.color.transparent);
        btnCopy.setOnClickListener(v -> copyKey(key));

        // Bouton partage
        Button btnShare = new Button(this);
        btnShare.setText("📤");
        btnShare.setTextSize(14f);
        btnShare.setPadding(8, 4, 8, 4);
        btnShare.setAllCaps(false);
        btnShare.setBackgroundResource(android.R.color.transparent);
        btnShare.setOnClickListener(v -> shareKey(key, duration));

        row.addView(tvKey);
        row.addView(tvDur);
        row.addView(btnCopy);
        row.addView(btnShare);
        generatedKeys.addView(row);
    }

    private int getSelectedDuration() {
        if (rgDuration == null) return 90;
        int id = rgDuration.getCheckedRadioButtonId();
        if (id == R.id.rb_180) return 180;
        if (id == R.id.rb_365) return 365;
        return 90;
    }

    private void copyKey(String key) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("LicenseKey", key));
        Toast.makeText(this, "✅ Clé copiée", Toast.LENGTH_SHORT).show();
    }

    private void copyAll() {
        Button btn = findViewById(R.id.btn_copy_all);
        if (btn == null) return;
        String all = (String) btn.getTag();
        if (all == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("LicenseKeys", all));
        Toast.makeText(this, "✅ Toutes les clés copiées", Toast.LENGTH_SHORT).show();
    }

    private void shareKey(String key, int days) {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(android.content.Intent.EXTRA_TEXT,
                "🔑 Votre licence EliteCast Pro (" + days + " jours) :\n\n" + key +
                "\n\n📥 Installez l'app EliteCast Régie et entrez cette clé.\n\n" +
                "📞 Support : Prophète Josias – Wise Design\n" +
                "WhatsApp : +240 555 445 514");
        startActivity(android.content.Intent.createChooser(i, "Partager la licence"));
    }
}
