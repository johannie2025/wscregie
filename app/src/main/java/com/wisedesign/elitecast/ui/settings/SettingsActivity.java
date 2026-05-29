package com.wisedesign.elitecast.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.elitecast.EliteCastApp;
import com.wisedesign.elitecast.R;
import com.wisedesign.elitecast.license.LicenseManager;
import com.wisedesign.elitecast.ui.setup.SplashActivity;

/**
 * Paramètres utilisateur :
 * • Thème (Bleu/Blanc, Sombre, Bleu/Rouge)
 * • Langue (FR / EN / ES)
 * • Infos licence
 * • Déconnexion
 * • Contact & Support
 *
 * Wise Design | Prophète Josias | WhatsApp +240 555 445 514
 */
public class SettingsActivity extends AppCompatActivity {

    private LicenseManager licenseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        licenseManager = new LicenseManager(this);
        bindAll();
    }

    private void bindAll() {
        setupThemeSection();
        setupLangSection();
        setupLicenseSection();
        setupSupportSection();
        setupLogout();

        View btnBack = findViewById(R.id.btn_back_settings);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void setupThemeSection() {
        // 3 boutons thème
        View btnBlue = findViewById(R.id.btn_theme_blue);
        View btnDark = findViewById(R.id.btn_theme_dark);
        View btnRed  = findViewById(R.id.btn_theme_red);

        int current = EliteCastApp.get().getTheme_();
        if (btnBlue != null) { btnBlue.setSelected(current == EliteCastApp.THEME_BLUE_WHITE); btnBlue.setOnClickListener(v -> setTheme_(EliteCastApp.THEME_BLUE_WHITE)); }
        if (btnDark != null) { btnDark.setSelected(current == EliteCastApp.THEME_DARK);       btnDark.setOnClickListener(v -> setTheme_(EliteCastApp.THEME_DARK)); }
        if (btnRed  != null) { btnRed.setSelected(current == EliteCastApp.THEME_BLUE_RED);    btnRed.setOnClickListener(v -> setTheme_(EliteCastApp.THEME_BLUE_RED)); }
    }

    private void setTheme_(int theme) {
        EliteCastApp.get().setTheme_(theme);
        recreate();
    }

    private void setupLangSection() {
        View btnFr = findViewById(R.id.btn_lang_fr);
        View btnEn = findViewById(R.id.btn_lang_en);
        View btnEs = findViewById(R.id.btn_lang_es);
        String cur = EliteCastApp.get().getLang();
        if (btnFr != null) { btnFr.setSelected("fr".equals(cur)); btnFr.setOnClickListener(v -> setLang("fr")); }
        if (btnEn != null) { btnEn.setSelected("en".equals(cur)); btnEn.setOnClickListener(v -> setLang("en")); }
        if (btnEs != null) { btnEs.setSelected("es".equals(cur)); btnEs.setOnClickListener(v -> setLang("es")); }
    }

    private void setLang(String lang) {
        EliteCastApp.get().prefs().edit().putString("app_lang", lang).apply();
        recreate();
    }

    private void setupLicenseSection() {
        TextView tvLic = findViewById(R.id.tv_license_detail);
        if (tvLic == null) return;

        LicenseManager.LicenseInfo info = licenseManager.getInfo();
        String detail;
        switch (info.status) {
            case VALID:
                detail = "✅ Licence active\n" +
                         "Durée : " + info.totalDays + " jours\n" +
                         "Expire le : " + info.expiryDate + "\n" +
                         "Reste : " + info.daysRemaining + " jours";
                break;
            case EXPIRED:
                detail = "❌ Licence expirée depuis le " + info.expiryDate;
                break;
            case TRIAL:
                detail = "🆓 Version d'essai\n" +
                         "• Max 3 écrans\n" +
                         "• Filigrane sur les écrans\n" +
                         "• Pas de téléchargement Bible\n\n" +
                         "📞 Pour une licence Pro :\nWhatsApp +240 555 445 514";
                break;
            default:
                detail = "Aucune licence";
        }
        if (info.isAdmin) detail = "👑 Compte Administrateur\nAccès illimité";
        tvLic.setText(detail);
    }

    private void setupSupportSection() {
        View btnWA = findViewById(R.id.btn_whatsapp);
        if (btnWA != null) {
            btnWA.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://wa.me/240555445514"));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(this, "WhatsApp : +240 555 445 514", Toast.LENGTH_LONG).show();
                }
            });
        }

        TextView tvSupport = findViewById(R.id.tv_support_info);
        if (tvSupport != null) {
            tvSupport.setText(
                    "EliteCast Régie v1.0\n" +
                    "Wise Design\n" +
                    "Prophète Josias\n" +
                    "📞 WhatsApp : +240 555 445 514\n" +
                    "🌐 Bata, Guinée Équatoriale");
        }
    }

    private void setupLogout() {
        Button btnLogout = findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                licenseManager.logout();
                Intent i = new Intent(this, SplashActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
        }
    }
}
