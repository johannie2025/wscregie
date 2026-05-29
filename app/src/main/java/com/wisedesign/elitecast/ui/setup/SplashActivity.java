package com.wisedesign.elitecast.ui.setup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.wisedesign.elitecast.EliteCastApp;
import com.wisedesign.elitecast.R;
import com.wisedesign.elitecast.license.LicenseManager;
import com.wisedesign.elitecast.ui.control.RegieActivity;

/**
 * ═══════════════════════════════════════════════════════
 *  SplashActivity – Écran de démarrage / Authentification
 *
 *  3 chemins :
 *  ① Essai gratuit (sans login) → accès limité
 *  ② Login utilisateur (login + clé licence) → accès complet
 *  ③ Login admin (admin + Jesus@_@2026) → panneau admin
 *
 *  Design : Bleu profond + Blanc + Rouge
 *  Trilingue FR/EN/ES
 * ═══════════════════════════════════════════════════════
 */
public class SplashActivity extends AppCompatActivity {

    private LicenseManager licenseManager;
    private boolean showLogin = false;

    // ── I18N simplifié ──────────────────────────────
    private String t(String fr, String en, String es) {
        String lang = EliteCastApp.get().getLang();
        if ("en".equals(lang)) return en;
        if ("es".equals(lang)) return es;
        return fr;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        licenseManager = new LicenseManager(this);
        applyTheme_();
        setContentView(R.layout.activity_splash);
        animateLogo();

        // Si déjà connecté → aller directement à la régie
        if (licenseManager.isLoggedIn()) {
            new Handler(Looper.getMainLooper()).postDelayed(this::goToRegie, 1200);
            return;
        }

        // Afficher les boutons après l'animation
        new Handler(Looper.getMainLooper()).postDelayed(this::showAuthButtons, 1500);
    }

    private void applyTheme_() {
        int theme = EliteCastApp.get().getTheme_();
        switch (theme) {
            case EliteCastApp.THEME_DARK:
                setTheme(R.style.Theme_EliteCast_Dark); break;
            case EliteCastApp.THEME_BLUE_RED:
                setTheme(R.style.Theme_EliteCast_BlueRed); break;
            default:
                setTheme(R.style.Theme_EliteCast); break;
        }
    }

    private void animateLogo() {
        View logo = findViewById(R.id.splash_logo_container);
        if (logo == null) return;
        ScaleAnimation scale = new ScaleAnimation(0.7f, 1f, 0.7f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(600);
        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        fade.setDuration(700);
        AnimationSet set = new AnimationSet(false);
        set.addAnimation(scale);
        set.addAnimation(fade);
        logo.startAnimation(set);
    }

    private void showAuthButtons() {
        View authPanel = findViewById(R.id.auth_panel);
        if (authPanel == null) return;
        authPanel.setVisibility(View.VISIBLE);
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(400);
        authPanel.startAnimation(anim);

        // Bouton Essai gratuit
        Button btnTrial = findViewById(R.id.btn_trial);
        if (btnTrial != null) {
            btnTrial.setText(t("Essai gratuit", "Free Trial", "Prueba gratuita"));
            btnTrial.setOnClickListener(v -> startTrial());
        }

        // Bouton Connexion
        Button btnLogin = findViewById(R.id.btn_login);
        if (btnLogin != null) {
            btnLogin.setText(t("Se connecter", "Sign In", "Iniciar sesión"));
            btnLogin.setOnClickListener(v -> showLoginForm());
        }

        // Texte info version
        TextView tvVersion = findViewById(R.id.tv_version);
        if (tvVersion != null) {
            tvVersion.setText("EliteCast Régie v1.0  |  Wise Design");
        }

        // Sélecteur de langue
        setupLangSelector();

        // Sélecteur de thème
        setupThemeSelector();
    }

    private void setupLangSelector() {
        LinearLayout langRow = findViewById(R.id.lang_row);
        if (langRow == null) return;
        String[] langs = {"fr", "en", "es"};
        String[] labels = {"FR", "EN", "ES"};
        for (int i = 0; i < langs.length; i++) {
            final String lang = langs[i];
            Button b = (Button) langRow.getChildAt(i);
            if (b != null) {
                b.setText(labels[i]);
                b.setSelected(lang.equals(EliteCastApp.get().getLang()));
                b.setOnClickListener(v -> {
                    EliteCastApp.get().prefs().edit().putString("app_lang", lang).apply();
                    recreate();
                });
            }
        }
    }

    private void setupThemeSelector() {
        LinearLayout themeRow = findViewById(R.id.theme_row);
        if (themeRow == null) return;
        int[] themes = {EliteCastApp.THEME_BLUE_WHITE, EliteCastApp.THEME_DARK, EliteCastApp.THEME_BLUE_RED};
        String[] icons = {"☀️", "🌙", "🔴"};
        for (int i = 0; i < themes.length; i++) {
            final int theme = themes[i];
            Button b = (Button) themeRow.getChildAt(i);
            if (b != null) {
                b.setText(icons[i]);
                b.setOnClickListener(v -> {
                    EliteCastApp.get().setTheme_(theme);
                    recreate();
                });
            }
        }
    }

    private void startTrial() {
        // Accès gratuit sans login
        EliteCastApp.get().prefs().edit().putBoolean("trial_mode", true).apply();
        goToRegie();
    }

    private void showLoginForm() {
        View authPanel = findViewById(R.id.auth_panel);
        View loginForm = findViewById(R.id.login_form);
        if (authPanel == null || loginForm == null) return;
        authPanel.setVisibility(View.GONE);
        loginForm.setVisibility(View.VISIBLE);

        EditText etLogin   = findViewById(R.id.et_login);
        EditText etPassword= findViewById(R.id.et_password);
        EditText etLicense = findViewById(R.id.et_license);
        TextView tvLabel   = findViewById(R.id.tv_login_label);
        Button   btnSubmit = findViewById(R.id.btn_submit_login);
        Button   btnBack   = findViewById(R.id.btn_back_auth);
        TextView tvError   = findViewById(R.id.tv_login_error);
        ImageView ivTogglePw = findViewById(R.id.iv_toggle_password);

        if (tvLabel != null)
            tvLabel.setText(t("Connexion", "Sign In", "Iniciar sesión"));

        // Toggle mot de passe
        if (ivTogglePw != null && etPassword != null) {
            ivTogglePw.setOnClickListener(v -> {
                int type = etPassword.getInputType();
                if (type == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                    etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    ivTogglePw.setImageResource(R.drawable.ic_eye_off);
                } else {
                    etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    ivTogglePw.setImageResource(R.drawable.ic_eye);
                }
                etPassword.setSelection(etPassword.getText().length());
            });
        }

        if (btnBack != null) btnBack.setOnClickListener(v -> {
            loginForm.setVisibility(View.GONE);
            authPanel.setVisibility(View.VISIBLE);
        });

        if (btnSubmit != null) btnSubmit.setOnClickListener(v -> {
            String login = etLogin != null ? etLogin.getText().toString().trim() : "";
            String pw    = etPassword != null ? etPassword.getText().toString().trim() : "";
            String key   = etLicense != null ? etLicense.getText().toString().trim() : "";

            if (tvError != null) tvError.setVisibility(View.GONE);

            // Tenter login admin
            if (LicenseManager.ADMIN_LOGIN.equals(login)) {
                if (licenseManager.loginAdmin(pw)) {
                    goToRegie();
                    return;
                }
                if (tvError != null) {
                    tvError.setText(t("Mot de passe admin incorrect", "Wrong admin password", "Contraseña incorrecta"));
                    tvError.setVisibility(View.VISIBLE);
                }
                return;
            }

            // Login utilisateur avec licence
            if (login.isEmpty()) {
                if (tvError != null) {
                    tvError.setText(t("Saisissez votre identifiant", "Enter your username", "Ingrese su usuario"));
                    tvError.setVisibility(View.VISIBLE);
                }
                return;
            }
            if (licenseManager.loginUser(login, key)) {
                goToRegie();
            } else {
                if (tvError != null) {
                    tvError.setText(t("Clé de licence invalide ou expirée",
                            "Invalid or expired license key",
                            "Clave de licencia inválida o expirada"));
                    tvError.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void goToRegie() {
        startActivity(new Intent(this, RegieActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
