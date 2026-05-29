package com.wisedesign.elitecast.ui.control;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.wisedesign.elitecast.EliteCastApp;
import com.wisedesign.elitecast.R;
import com.wisedesign.elitecast.license.LicenseManager;
import com.wisedesign.elitecast.network.UdpAnnounce;
import com.wisedesign.elitecast.service.RegieService;
import com.wisedesign.elitecast.ui.screens.ScreenConfigSheet;
import com.wisedesign.elitecast.ui.settings.AdminActivity;
import com.wisedesign.elitecast.ui.settings.SettingsActivity;

import org.json.JSONObject;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 *  RegieActivity – Régie principale EliteCast Pro
 *
 *  Layout portrait/paysage adaptatif (phone/tablette) :
 *  ┌─────────────────────────────────────────────────┐
 *  │  TOPBAR : Logo | IP | Écrans | Thème | Admin    │
 *  ├──────────────┬──────────────────────────────────┤
 *  │  SIDEBAR     │  ZONE CENTRALE                   │
 *  │  Bible/Chants│  Aperçu Live | Programme         │
 *  │  /Média      │                                  │
 *  ├──────────────┴──────────────────────────────────┤
 *  │  PANEL BAS : Contrôles Live | Transition | LT   │
 *  ├─────────────────────────────────────────────────┤
 *  │  BOTTOMNAV : Bible | Chants | Médias | Écrans   │
 *  └─────────────────────────────────────────────────┘
 *
 *  Sans PeerJS, sans MQTT. WebSocket pur Java.
 *  Wise Design | Prophète Josias | WhatsApp +240555445514
 * ═══════════════════════════════════════════════════════════════
 */
public class RegieActivity extends AppCompatActivity
        implements RegieService.ServiceListener {

    private static final String TAG = "RegieActivity";

    // ── Service ─────────────────────────────────────
    private RegieService service;
    private boolean      serviceBound = false;
    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            service = ((RegieService.LocalBinder) b).getService();
            service.setUiListener(RegieActivity.this);
            serviceBound = true;
            updateServerInfo();
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            serviceBound = false;
        }
    };

    // ── State ────────────────────────────────────────
    private LicenseManager licenseManager;
    private String currentVerseText = "";
    private String currentVerseRef  = "";
    private String currentTransition = "FADE";
    private String currentTarget    = null; // null = ALL
    private final List<String> connectedScreenIds = new ArrayList<>();

    // ── Vues ─────────────────────────────────────────
    private View          rootView;
    private TextView      tvIpBadge, tvScreenCount, tvLicenseInfo;
    private View          wsStatusDot;
    private BottomNavigationView bottomNav;

    // Panneau gauche / contenu
    private LinearLayout  panelBible, panelSongs, panelMedia, panelScreens;

    // Aperçu Live
    private FrameLayout   previewFrame;
    private TextView      prevVerseText, prevVerseRef;
    private View          prevLowerThird;
    private TextView      prevLtTitle, prevLtSub;

    // Contrôles Live
    private Button        btnSendNow, btnClear, btnBlackout, btnPrev, btnNext;
    private LinearLayout  transitionRow, targetRow;

    // Lower Third
    private EditText      etLtTitle, etLtSub;

    // Bible
    private EditText      etVerseSearch;
    private RecyclerView  rvVerses;
    private Spinner       spBook, spChapter;
    private LinearLayout  versionRow;

    // Songs
    private RecyclerView  rvSongs;
    private LinearLayout  slidesContainer;

    // Screens panel
    private RecyclerView  rvScreens;

    private final Handler ui = new Handler(Looper.getMainLooper());

    // ── I18N ─────────────────────────────────────────
    private String t(String fr, String en, String es) {
        String lang = EliteCastApp.get().getLang();
        if ("en".equals(lang)) return en;
        if ("es".equals(lang)) return es;
        return fr;
    }

    // ═══════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTheme_();
        setContentView(R.layout.activity_regie);

        licenseManager = new LicenseManager(this);
        bindViews();
        setupTopbar();
        setupBottomNav();
        setupControls();
        setupBiblePanel();
        setupSongsPanel();
        setupTransitions();
        applyI18n();
        showPanel("bible");

        // Démarrer service réseau
        Intent svc = new Intent(this, RegieService.class);
        startForegroundService(svc);
        bindService(svc, serviceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            service.setUiListener(null);
            unbindService(serviceConn);
        }
    }

    private void applyTheme_() {
        int theme = EliteCastApp.get().getTheme_();
        switch (theme) {
            case EliteCastApp.THEME_DARK:    setTheme(R.style.Theme_EliteCast_Dark);   break;
            case EliteCastApp.THEME_BLUE_RED:setTheme(R.style.Theme_EliteCast_BlueRed);break;
            default:                          setTheme(R.style.Theme_EliteCast);        break;
        }
    }

    // ═══════════════════════════════════════════════════
    //  BIND VUES
    // ═══════════════════════════════════════════════════
    private void bindViews() {
        rootView      = findViewById(R.id.root_view);
        tvIpBadge     = findViewById(R.id.tv_ip_badge);
        tvScreenCount = findViewById(R.id.tv_screen_count);
        tvLicenseInfo = findViewById(R.id.tv_license_info);
        wsStatusDot   = findViewById(R.id.ws_status_dot);
        bottomNav     = findViewById(R.id.bottom_navigation);

        // Panels
        panelBible   = findViewById(R.id.panel_bible);
        panelSongs   = findViewById(R.id.panel_songs);
        panelMedia   = findViewById(R.id.panel_media);
        panelScreens = findViewById(R.id.panel_screens);

        // Aperçu
        previewFrame  = findViewById(R.id.preview_frame);
        prevVerseText = findViewById(R.id.prev_verse_text);
        prevVerseRef  = findViewById(R.id.prev_verse_ref);
        prevLowerThird= findViewById(R.id.prev_lower_third);
        prevLtTitle   = findViewById(R.id.prev_lt_title);
        prevLtSub     = findViewById(R.id.prev_lt_sub);

        // Contrôles
        btnSendNow = findViewById(R.id.btn_send_now);
        btnClear   = findViewById(R.id.btn_clear);
        btnBlackout= findViewById(R.id.btn_blackout);
        btnPrev    = findViewById(R.id.btn_prev);
        btnNext    = findViewById(R.id.btn_next);
        transitionRow = findViewById(R.id.transition_row);
        targetRow     = findViewById(R.id.target_row);

        // Lower Third
        etLtTitle = findViewById(R.id.et_lt_title);
        etLtSub   = findViewById(R.id.et_lt_sub);

        // Bible
        etVerseSearch = findViewById(R.id.et_verse_search);
        rvVerses      = findViewById(R.id.rv_verses);
        spBook        = findViewById(R.id.sp_book);
        spChapter     = findViewById(R.id.sp_chapter);
        versionRow    = findViewById(R.id.version_row);

        // Songs
        rvSongs        = findViewById(R.id.rv_songs);
        slidesContainer= findViewById(R.id.slides_container);

        // Screens
        rvScreens = findViewById(R.id.rv_screens);
    }

    // ═══════════════════════════════════════════════════
    //  TOPBAR
    // ═══════════════════════════════════════════════════
    private void setupTopbar() {
        // Bouton admin (visible seulement si admin)
        View btnAdmin = findViewById(R.id.btn_admin);
        if (btnAdmin != null) {
            btnAdmin.setVisibility(licenseManager.isAdmin() ? View.VISIBLE : View.GONE);
            btnAdmin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        }

        // Bouton paramètres
        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null)
            btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // Licence info
        LicenseManager.LicenseInfo info = licenseManager.getInfo();
        if (tvLicenseInfo != null) {
            String label;
            if (info.isAdmin) {
                label = "👑 Admin";
            } else if (info.status == LicenseManager.LicenseStatus.TRIAL) {
                label = "🆓 " + t("Essai", "Trial", "Prueba");
            } else {
                label = "✅ Pro · " + info.daysRemaining + "j";
            }
            tvLicenseInfo.setText(label);
        }
    }

    private void updateServerInfo() {
        if (!serviceBound) return;
        String ip = service.getLocalIp();
        if (tvIpBadge != null)
            tvIpBadge.setText("📡 " + ip + ":9000");
        if (tvIpBadge != null) {
            tvIpBadge.setOnClickListener(v -> {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("IP", ip));
                showSnack(t("IP copiée", "IP copied", "IP copiada") + ": " + ip);
            });
        }
    }

    // ═══════════════════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════════════════
    private void setupBottomNav() {
        if (bottomNav == null) return;
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_bible)   { showPanel("bible");   return true; }
            if (id == R.id.nav_songs)   { showPanel("songs");   return true; }
            if (id == R.id.nav_media)   { showPanel("media");   return true; }
            if (id == R.id.nav_screens) { showPanel("screens"); return true; }
            return false;
        });
    }

    private void showPanel(String name) {
        if (panelBible   != null) panelBible.setVisibility("bible".equals(name)   ? View.VISIBLE : View.GONE);
        if (panelSongs   != null) panelSongs.setVisibility("songs".equals(name)   ? View.VISIBLE : View.GONE);
        if (panelMedia   != null) panelMedia.setVisibility("media".equals(name)   ? View.VISIBLE : View.GONE);
        if (panelScreens != null) panelScreens.setVisibility("screens".equals(name) ? View.VISIBLE : View.GONE);
    }

    // ═══════════════════════════════════════════════════
    //  CONTRÔLES LIVE
    // ═══════════════════════════════════════════════════
    private void setupControls() {
        if (btnSendNow != null) btnSendNow.setOnClickListener(v -> sendCurrentContent());
        if (btnClear   != null) btnClear.setOnClickListener(v   -> sendClear());
        if (btnBlackout!= null) btnBlackout.setOnClickListener(v -> sendBlackout());
        if (btnPrev    != null) btnPrev.setOnClickListener(v    -> onPrev());
        if (btnNext    != null) btnNext.setOnClickListener(v    -> onNext());

        // Lower Third
        View btnSendLt = findViewById(R.id.btn_send_lt);
        View btnHideLt = findViewById(R.id.btn_hide_lt);
        if (btnSendLt != null) btnSendLt.setOnClickListener(v -> sendLowerThird());
        if (btnHideLt != null) btnHideLt.setOnClickListener(v -> hideLowerThird());

        // Raccourcis LT
        setupLtPresets();
    }

    private void setupLtPresets() {
        View btnLtPasteur = findViewById(R.id.btn_lt_pasteur);
        View btnLtChorale = findViewById(R.id.btn_lt_chorale);
        View btnLtAnnonce = findViewById(R.id.btn_lt_annonce);
        if (btnLtPasteur != null) btnLtPasteur.setOnClickListener(v -> quickLt("Pasteur", "Prédicateur"));
        if (btnLtChorale != null) btnLtChorale.setOnClickListener(v -> quickLt("Chorale", "Équipe de Louange"));
        if (btnLtAnnonce != null) btnLtAnnonce.setOnClickListener(v -> quickLt("Annonce", ""));
    }

    private void setupTransitions() {
        if (transitionRow == null) return;
        String[] names = {"FADE","SLIDE","NONE"};
        String[] labels = {"Fondu","Glisse","Coupé"};
        for (int i = 0; i < transitionRow.getChildCount(); i++) {
            View btn = transitionRow.getChildAt(i);
            if (btn instanceof Button && i < names.length) {
                final String t = names[i];
                ((Button) btn).setText(labels[i]);
                btn.setSelected("FADE".equals(t));
                btn.setOnClickListener(v -> {
                    currentTransition = t;
                    for (int j = 0; j < transitionRow.getChildCount(); j++)
                        transitionRow.getChildAt(j).setSelected(false);
                    v.setSelected(true);
                });
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  DIFFUSION
    // ═══════════════════════════════════════════════════
    private void sendVerseToScreens(String text, String ref) {
        currentVerseText = text;
        currentVerseRef  = ref;
        updatePreview(text, ref);

        try {
            JSONObject p = new JSONObject();
            p.put("type",  "project");
            p.put("text",  text);
            p.put("ref",   ref);
            p.put("color", "#FFFFFF");
            p.put("transition", currentTransition);
            // Trial watermark
            if (licenseManager.isTrialMode()) {
                p.put("watermark", "ELITECAST TRIAL");
            }
            broadcastOrTarget(p.toString());
            showSnack("📡 " + ref);
        } catch (Exception e) { showSnack("❌ " + e.getMessage()); }
    }

    private void sendCurrentContent() {
        if (currentVerseText.isEmpty()) {
            showSnack(t("Sélectionnez un verset", "Select a verse", "Seleccione un versículo"));
            return;
        }
        sendVerseToScreens(currentVerseText, currentVerseRef);
    }

    private void sendClear() {
        try {
            JSONObject p = new JSONObject();
            p.put("type", "clear");
            broadcastOrTarget(p.toString());
            updatePreview("", "");
            if (prevLowerThird != null) prevLowerThird.setVisibility(View.GONE);
        } catch (Exception ignored) {}
    }

    private void sendBlackout() {
        try {
            JSONObject p = new JSONObject();
            p.put("type", "bg");
            JSONObject bg = new JSONObject();
            bg.put("color", "#000000");
            p.put("bg", bg);
            broadcastOrTarget(p.toString());
            sendClear();
            showSnack("⬛ Noir");
        } catch (Exception ignored) {}
    }

    private void sendLowerThird() {
        String title = etLtTitle != null ? etLtTitle.getText().toString().trim() : "";
        String sub   = etLtSub   != null ? etLtSub.getText().toString().trim()   : "";
        if (title.isEmpty()) return;
        try {
            JSONObject p = new JSONObject();
            p.put("type",  "lt");
            p.put("name",  title);
            p.put("title", sub);
            broadcastOrTarget(p.toString());
            showPreviewLt(title, sub);
        } catch (Exception ignored) {}
    }

    private void hideLowerThird() {
        try {
            JSONObject p = new JSONObject();
            p.put("type", "lt_hide");
            broadcastOrTarget(p.toString());
            if (prevLowerThird != null) prevLowerThird.setVisibility(View.GONE);
        } catch (Exception ignored) {}
    }

    private void quickLt(String title, String sub) {
        if (etLtTitle != null) etLtTitle.setText(title);
        if (etLtSub   != null) etLtSub.setText(sub);
        sendLowerThird();
    }

    private void broadcastOrTarget(String json) {
        if (!serviceBound) return;
        if (currentTarget == null) {
            service.broadcast(json);
        } else {
            service.sendTo(currentTarget, json);
        }
    }

    // ═══════════════════════════════════════════════════
    //  BIBLE
    // ═══════════════════════════════════════════════════
    private static final String[][] DEMO_VERSES_FR = {
        {"Jean 3:16", "Car Dieu a tant aimé le monde qu'il a donné son Fils unique, afin que quiconque croit en lui ne périsse point, mais qu'il ait la vie éternelle."},
        {"Jean 3:17", "Dieu, en effet, n'a pas envoyé son Fils dans le monde pour qu'il condamne le monde, mais pour que le monde soit sauvé par lui."},
        {"Psaume 23:1", "L'Éternel est mon berger : je ne manquerai de rien."},
        {"Psaume 23:4", "Quand je marche dans la vallée de l'ombre de la mort, je ne crains aucun mal, car tu es avec moi : ta houlette et ton bâton me rassurent."},
        {"Philippiens 4:13", "Je puis tout par celui qui me fortifie."},
        {"Philippiens 4:7", "Et la paix de Dieu, qui surpasse toute intelligence, gardera vos cœurs et vos pensées en Jésus-Christ."},
        {"Ésaïe 41:10", "Ne crains rien, car je suis avec toi ; ne promène pas des regards inquiets, car je suis ton Dieu."},
        {"Romains 8:28", "Nous savons, du reste, que toutes choses concourent au bien de ceux qui aiment Dieu."},
        {"Romains 8:1", "Il n'y a donc maintenant aucune condamnation pour ceux qui sont en Jésus-Christ."},
        {"Ésaïe 53:5", "Mais il était blessé pour nos péchés, brisé pour nos iniquités ; le châtiment qui nous donne la paix est tombé sur lui."},
        {"Luc 4:18", "L'Esprit du Seigneur est sur moi, parce qu'il m'a oint pour annoncer une bonne nouvelle aux pauvres."},
        {"Matthieu 28:19", "Allez, faites de toutes les nations des disciples, les baptisant au nom du Père, du Fils et du Saint-Esprit."},
    };

    private void setupBiblePanel() {
        if (rvVerses == null) return;
        rvVerses.setLayoutManager(new LinearLayoutManager(this));
        loadDemoVerses("");

        // Recherche
        if (etVerseSearch != null) {
            etVerseSearch.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                public void onTextChanged(CharSequence s, int st, int b, int c) { loadDemoVerses(s.toString()); }
                public void afterTextChanged(Editable s) {}
            });
            etVerseSearch.setHint(t("Rechercher un verset…", "Search a verse…", "Buscar un versículo…"));
        }

        // Version pills
        setupVersionPills();
    }

    private void loadDemoVerses(String query) {
        List<String[]> filtered = new ArrayList<>();
        String q = query.toLowerCase().trim();
        for (String[] v : DEMO_VERSES_FR) {
            if (q.isEmpty() || v[0].toLowerCase().contains(q) || v[1].toLowerCase().contains(q)) {
                filtered.add(v);
            }
        }
        VerseAdapter adapter = new VerseAdapter(filtered, (ref, text) -> {
            selectVerse(ref, text);
        });
        rvVerses.setAdapter(adapter);
    }

    private void selectVerse(String ref, String text) {
        currentVerseText = text;
        currentVerseRef  = ref;
        updatePreview(text, ref);

        // Animation flash sur le bouton ENVOYER
        if (btnSendNow != null) {
            AlphaAnimation flash = new AlphaAnimation(1f, 0.5f);
            flash.setDuration(150);
            flash.setRepeatMode(Animation.REVERSE);
            flash.setRepeatCount(1);
            btnSendNow.startAnimation(flash);
        }
    }

    private void setupVersionPills() {
        if (versionRow == null) return;
        String[] versions = {"LSG", "NBS", "RVR60", "KJV"};
        boolean isTrial = licenseManager.isTrialMode();
        for (int i = 0; i < versionRow.getChildCount(); i++) {
            View v = versionRow.getChildAt(i);
            if (v instanceof Button && i < versions.length) {
                final String ver = versions[i];
                ((Button) v).setText(ver);
                if (isTrial && i > 0) {
                    ((Button) v).setAlpha(0.4f);
                    v.setOnClickListener(x -> showSnack(
                            t("Téléchargement disponible en version Pro",
                              "Download available in Pro version",
                              "Descarga disponible en versión Pro")));
                } else {
                    v.setSelected(i == 0);
                    v.setOnClickListener(x -> {
                        for (int j = 0; j < versionRow.getChildCount(); j++)
                            versionRow.getChildAt(j).setSelected(false);
                        x.setSelected(true);
                    });
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  CHANTS
    // ═══════════════════════════════════════════════════
    private static final Object[][] DEMO_SONGS = {
        {"Dieu est bon", "Louange", new String[][]{
            {"VERSE", "Dieu est bon, tout le temps\nTout le temps, Dieu est bon"},
            {"CHORUS", "Loué soit le Seigneur à jamais\nSon amour est éternel"},
        }},
        {"Tu es Dieu", "SW0L", new String[][]{
            {"VERSE", "Tu es Dieu, tu es Roi\nTu règnes sur ma vie"},
            {"CHORUS", "Je t'adore, je t'honore\nTu es le Seigneur"},
            {"BRIDGE", "Gloire à toi, gloire à toi\nPour toujours et à jamais"},
        }},
        {"Hosanna", "Louange", new String[][]{
            {"VERSE", "Hosanna au plus haut des cieux\nHosanna à l'Éternel"},
            {"CHORUS", "Sanctifié soit son Nom\nSanctifié soit son Nom"},
        }},
    };

    private void setupSongsPanel() {
        if (rvSongs == null) return;
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        List<String[]> songHeaders = new ArrayList<>();
        for (Object[] s : DEMO_SONGS) {
            songHeaders.add(new String[]{(String) s[0], (String) s[1]});
        }
        SongAdapter adapter = new SongAdapter(songHeaders, index -> openSong(index));
        rvSongs.setAdapter(adapter);
    }

    private void openSong(int index) {
        if (slidesContainer == null || index >= DEMO_SONGS.length) return;
        slidesContainer.removeAllViews();
        slidesContainer.setVisibility(View.VISIBLE);

        String[][] slides = (String[][]) DEMO_SONGS[index][2];
        String songTitle = (String) DEMO_SONGS[index][0];

        for (String[] slide : slides) {
            String type = slide[0];
            String text = slide[1];

            Button btn = new Button(this);
            String typeLabel = "VERSE".equals(type) ? "📝" : "CHORUS".equals(type) ? "🔁" : "🌉";
            btn.setText(typeLabel + " " + text.replace("\n", " / ").substring(0, Math.min(40, text.length())) + "…");
            btn.setTextSize(12f);
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> sendVerseToScreens(text, songTitle));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 4, 0, 4);
            btn.setLayoutParams(lp);
            slidesContainer.addView(btn);
        }
    }

    // ═══════════════════════════════════════════════════
    //  SERVICE LISTENER
    // ═══════════════════════════════════════════════════
    @Override
    public void onScreenConnected(String screenId) {
        ui.post(() -> {
            if (!connectedScreenIds.contains(screenId)) connectedScreenIds.add(screenId);
            updateScreenCount();
            updateTargetRow();
            showSnack("📺 " + screenId + " connecté");
        });
    }

    @Override
    public void onScreenDisconnected(String screenId) {
        ui.post(() -> {
            connectedScreenIds.remove(screenId);
            if (screenId.equals(currentTarget)) currentTarget = null;
            updateScreenCount();
            updateTargetRow();
            showSnack("❌ " + screenId + " déconnecté");
        });
    }

    @Override
    public void onMessage(String screenId, String json) {
        // Messages entrants des écrans (stats, ack, etc.) – loguer seulement
    }

    private void updateScreenCount() {
        int n = serviceBound ? service.getScreenCount() : 0;
        if (tvScreenCount != null)
            tvScreenCount.setText(n + " " + t("écran(s)", "screen(s)", "pantalla(s)"));
        // Indicateur couleur
        if (wsStatusDot != null) {
            wsStatusDot.setBackgroundResource(n > 0 ? R.drawable.dot_green : R.drawable.dot_orange);
        }
    }

    private void updateTargetRow() {
        if (targetRow == null) return;
        targetRow.removeAllViews();

        // Bouton "Tous"
        Button btnAll = new Button(this);
        btnAll.setText(t("Tous", "All", "Todos"));
        btnAll.setSelected(currentTarget == null);
        btnAll.setTextSize(11f);
        btnAll.setAllCaps(false);
        btnAll.setOnClickListener(v -> {
            currentTarget = null;
            updateTargetRow();
        });
        targetRow.addView(btnAll);

        for (String sid : connectedScreenIds) {
            Button btn = new Button(this);
            btn.setText(sid.length() > 8 ? sid.substring(0, 8) : sid);
            btn.setSelected(sid.equals(currentTarget));
            btn.setTextSize(10f);
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> {
                currentTarget = sid;
                updateTargetRow();
            });
            targetRow.addView(btn);
        }
    }

    // ═══════════════════════════════════════════════════
    //  APERÇU
    // ═══════════════════════════════════════════════════
    private void updatePreview(String text, String ref) {
        if (prevVerseText != null) prevVerseText.setText(text);
        if (prevVerseRef  != null) prevVerseRef.setText(ref);
    }

    private void showPreviewLt(String title, String sub) {
        if (prevLowerThird != null) {
            prevLowerThird.setVisibility(View.VISIBLE);
            if (prevLtTitle != null) prevLtTitle.setText(title);
            if (prevLtSub   != null) prevLtSub.setText(sub);
        }
    }

    // ═══════════════════════════════════════════════════
    //  PLAYLIST NAVIGATION
    // ═══════════════════════════════════════════════════
    private int playlistIndex = -1;
    private final List<String[]> playlist = new ArrayList<>();

    private void onPrev() {
        if (playlistIndex > 0) {
            playlistIndex--;
            String[] item = playlist.get(playlistIndex);
            sendVerseToScreens(item[1], item[0]);
        }
    }

    private void onNext() {
        if (playlistIndex < playlist.size() - 1) {
            playlistIndex++;
            String[] item = playlist.get(playlistIndex);
            sendVerseToScreens(item[1], item[0]);
        }
    }

    // ═══════════════════════════════════════════════════
    //  I18N
    // ═══════════════════════════════════════════════════
    private void applyI18n() {
        if (btnSendNow != null)
            btnSendNow.setText(t("📡 DIFFUSER", "📡 BROADCAST", "📡 TRANSMITIR"));
        if (btnClear != null)
            btnClear.setText(t("Effacer", "Clear", "Borrar"));
        if (btnBlackout != null)
            btnBlackout.setText(t("⬛ Noir", "⬛ Black", "⬛ Negro"));
        if (btnPrev != null) btnPrev.setText("◀ " + t("Préc.", "Prev", "Ant."));
        if (btnNext != null) btnNext.setText(t("Suiv.", "Next", "Sig.") + " ▶");
    }

    // ═══════════════════════════════════════════════════
    //  UTILITAIRES
    // ═══════════════════════════════════════════════════
    private void showSnack(String msg) {
        if (rootView != null)
            Snackbar.make(rootView, msg, Snackbar.LENGTH_SHORT).show();
    }

    // ─── Adaptateurs internes simples ─────────────────

    interface VerseClickListener { void onClick(String ref, String text); }

    static class VerseAdapter extends RecyclerView.Adapter<VerseAdapter.VH> {
        private final List<String[]> verses;
        private final VerseClickListener listener;
        VerseAdapter(List<String[]> v, VerseClickListener l) { this.verses = v; this.listener = l; }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            LinearLayout ll = new LinearLayout(p.getContext());
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setPadding(24, 16, 24, 16);
            ll.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(ll);
        }
        @Override public void onBindViewHolder(VH h, int i) {
            h.tvRef.setText(verses.get(i)[0]);
            h.tvText.setText(verses.get(i)[1]);
            h.root.setOnClickListener(v -> listener.onClick(verses.get(i)[0], verses.get(i)[1]));
        }
        @Override public int getItemCount() { return verses.size(); }
        static class VH extends RecyclerView.ViewHolder {
            LinearLayout root;
            TextView tvRef, tvText;
            VH(LinearLayout v) {
                super(v);
                root = v;
                tvRef = new TextView(v.getContext());
                tvRef.setTextSize(11f);
                tvRef.setTextColor(0xFF42A5F5);
                tvRef.setTypeface(null, android.graphics.Typeface.BOLD);
                tvText = new TextView(v.getContext());
                tvText.setTextSize(13f);
                tvText.setMaxLines(2);
                tvText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                v.addView(tvRef);
                v.addView(tvText);
            }
        }
    }

    interface SongClickListener { void onClick(int index); }

    static class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {
        private final List<String[]> songs;
        private final SongClickListener listener;
        SongAdapter(List<String[]> s, SongClickListener l) { this.songs = s; this.listener = l; }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            LinearLayout ll = new LinearLayout(p.getContext());
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setPadding(24, 14, 24, 14);
            ll.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(ll);
        }
        @Override public void onBindViewHolder(VH h, int i) {
            h.tvTitle.setText("🎵 " + songs.get(i)[0]);
            h.tvArtist.setText(songs.get(i)[1]);
            h.root.setOnClickListener(v -> listener.onClick(i));
        }
        @Override public int getItemCount() { return songs.size(); }
        static class VH extends RecyclerView.ViewHolder {
            LinearLayout root;
            TextView tvTitle, tvArtist;
            VH(LinearLayout v) {
                super(v);
                root = v;
                tvTitle  = new TextView(v.getContext());
                tvTitle.setTextSize(14f);
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                tvArtist = new TextView(v.getContext());
                tvArtist.setTextSize(11f);
                tvArtist.setTextColor(0xFF90A4AE);
                v.addView(tvTitle);
                v.addView(tvArtist);
            }
        }
    }
}
