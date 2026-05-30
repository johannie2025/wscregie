package com.wisesmartchurch.system.ui.main;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.tabs.TabLayout;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.bible.BibleEngine;
import com.wisesmartchurch.system.db.AppDatabase;
import com.wisesmartchurch.system.model.*;
import com.wisesmartchurch.system.server.WscServerService;
import com.wisesmartchurch.system.streaming.StreamingManager;
import com.wisesmartchurch.system.ui.bible.BiblePanelController;
import com.wisesmartchurch.system.ui.bible.BiblesFragment;
import com.wisesmartchurch.system.ui.songs.SongsPanelController;
import com.wisesmartchurch.system.ui.playlist.PlaylistPanelController;
import com.wisesmartchurch.system.ui.announce.AnnouncePanelController;
import com.wisesmartchurch.system.ui.screens.ScreensManagerActivity;
import com.wisesmartchurch.system.ui.settings.SettingsFragment;
import com.wisesmartchurch.system.utils.NetworkUtils;
import org.json.JSONObject;
import java.util.*;

public class MainActivity extends AppCompatActivity implements WscServerService.OnClientEventListener {

    private static final String TAG  = "WSC_Main";
    private static final int REQ_PERM = 100;

    // ── Services ──────────────────────────────────────
    private WscServerService server;
    private boolean bound = false;
    private final ServiceConnection svcConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            server = ((WscServerService.LocalBinder) b).getService();
            server.addListener(MainActivity.this);
            bound = true;
            updateStatus();
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            bound = false; server = null;
        }
    };

    // ── Core ──────────────────────────────────────────
    private BibleEngine      bibleEngine;
    private StreamingManager streamMgr;
    private AppDatabase      db;

    // ── Panels ────────────────────────────────────────
    private BiblePanelController    biblePanel;
    private SongsPanelController    songsPanel;
    private PlaylistPanelController playlistPanel;
    private AnnouncePanelController announcePanel;
    private BiblesFragment          biblesFragment;
    private SettingsFragment        settingsFragment;

    // ── Projection state ──────────────────────────────
    private ProjectionPayload currentPayload;
    private String textColor = "#FFFFFF";
    private String fontSize  = "5.5vw";
    private String bgMode    = "black";
    private String bgUrl     = "";
    private int    activeSlot = 1;
    private boolean camActive = false;

    // ── Views ─────────────────────────────────────────
    private TextView  tvStatus, tvScreens, tvIp, tvRef, tvText, tvLtPreview;
    private TabLayout tabs;
    private View panelBible, panelSongs, panelAnnounce, panelPlaylist,
                 panelBibles, panelSettings, panelScreens;
    private EditText etLtName, etLtRole, etBgUrl;
    private Button   btnProject, btnClear, btnNavPrev, btnNavNext;
    private Button   btnLtShow, btnLtHide, btnLogoAnim, btnCamera;
    private Button   btnBgBlack, btnBgGradient, btnBgImage;
    private Button   btnFsS, btnFsM, btnFsL, btnFsXL;
    private Button   btnSlot1, btnSlot2, btnSlot3, btnSlot4;
    private Spinner  spLtType, spLtStyle;
    private View     btnColorWhite, btnColorGold, btnColorGreen, btnColorBlue, btnColorRed;

    private final Handler ui = new Handler(Looper.getMainLooper());

    // ── Lifecycle ─────────────────────────────────────
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        // Empêcher crash "Feature must be requested before adding content"
        try { requestWindowFeature(Window.FEATURE_NO_TITLE); } catch (Exception ignored) {}
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        db          = AppDatabase.getInstance(this);
        bibleEngine = new BibleEngine(this);
        streamMgr   = new StreamingManager(this);

        requestPermissions();
        bindViews();
        initPanels();
        bindServerService();
        restorePrefs();
        initBgButtons();
        initColorButtons();
        initFontButtons();
        initSlotButtons();
        initRemoteButtons();
        setupKeyboard();
    }

    // ── View bindings ─────────────────────────────────
    private void bindViews() {
        tvStatus  = findViewById(R.id.tv_server_status);
        tvScreens = findViewById(R.id.tv_screen_count);
        tvIp      = findViewById(R.id.tv_server_ip);
        tvRef     = findViewById(R.id.tv_current_ref);
        tvText    = findViewById(R.id.tv_current_text);
        tvLtPreview = findViewById(R.id.tv_lt_preview);
        tabs      = findViewById(R.id.center_tabs);

        panelBible    = findViewById(R.id.panel_bible);
        panelSongs    = findViewById(R.id.panel_songs);
        panelAnnounce = findViewById(R.id.panel_announce);
        panelPlaylist = findViewById(R.id.panel_playlist);
        panelBibles   = findViewById(R.id.panel_bibles);
        panelSettings = findViewById(R.id.panel_settings);
        panelScreens  = findViewById(R.id.panel_screens);

        etLtName    = findViewById(R.id.et_lt_name);
        etLtRole    = findViewById(R.id.et_lt_role);
        etBgUrl     = findViewById(R.id.et_bg_url);
        btnProject  = findViewById(R.id.btn_project);
        btnClear    = findViewById(R.id.btn_clear);
        btnNavPrev  = findViewById(R.id.btn_nav_prev);
        btnNavNext  = findViewById(R.id.btn_nav_next);
        btnLtShow   = findViewById(R.id.btn_lt_show);
        btnLtHide   = findViewById(R.id.btn_lt_hide);
        btnLogoAnim = findViewById(R.id.btn_logo_anim);
        btnCamera   = findViewById(R.id.btn_camera);
        btnBgBlack  = findViewById(R.id.btn_bg_black);
        btnBgGradient = findViewById(R.id.btn_bg_gradient);
        btnBgImage  = findViewById(R.id.btn_bg_image);
        btnFsS      = findViewById(R.id.btn_fs_s);
        btnFsM      = findViewById(R.id.btn_fs_m);
        btnFsL      = findViewById(R.id.btn_fs_l);
        btnFsXL     = findViewById(R.id.btn_fs_xl);
        btnSlot1    = findViewById(R.id.btn_slot1);
        btnSlot2    = findViewById(R.id.btn_slot2);
        btnSlot3    = findViewById(R.id.btn_slot3);
        btnSlot4    = findViewById(R.id.btn_slot4);
        spLtType    = findViewById(R.id.sp_lt_type);
        spLtStyle   = findViewById(R.id.sp_lt_style);
        btnColorWhite = findViewById(R.id.btn_color_white);
        btnColorGold  = findViewById(R.id.btn_color_gold);
        btnColorGreen = findViewById(R.id.btn_color_green);
        btnColorBlue  = findViewById(R.id.btn_color_blue);
        btnColorRed   = findViewById(R.id.btn_color_red);

        if (btnProject  != null) btnProject.setOnClickListener(v -> projectCurrent());
        if (btnClear    != null) btnClear.setOnClickListener(v -> projectClear());
        if (btnNavPrev  != null) btnNavPrev.setOnClickListener(v -> navigatePrev());
        if (btnNavNext  != null) btnNavNext.setOnClickListener(v -> navigateNext());
        if (btnLtShow   != null) btnLtShow.setOnClickListener(v -> showLt());
        if (btnLtHide   != null) btnLtHide.setOnClickListener(v -> hideLt());
        if (btnLogoAnim != null) btnLogoAnim.setOnClickListener(v -> broadcastLogo());
        if (btnCamera   != null) btnCamera.setOnClickListener(v -> toggleCamera());
        if (tvIp        != null) tvIp.setOnClickListener(v -> copyIp());
        if (etBgUrl != null) {
            etBgUrl.setOnEditorActionListener((v, action, event) -> {
                setBgUrl(v.getText().toString().trim());
                return false;
            });
        }

        if (tabs != null) {
            tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab t) { showPanel(t.getPosition()); }
                @Override public void onTabUnselected(TabLayout.Tab t) {}
                @Override public void onTabReselected(TabLayout.Tab t) {}
            });
        }

        View btnScreensBtn = findViewById(R.id.btn_screens);
        if (btnScreensBtn != null)
            btnScreensBtn.setOnClickListener(v -> startActivity(
                new Intent(this, ScreensManagerActivity.class)));

        View btnRP = findViewById(R.id.btn_remote_preacher);
        View btnRM = findViewById(R.id.btn_remote_maestro);
        if (btnRP != null) btnRP.setOnClickListener(v -> startActivity(
            new Intent(this, com.wisesmartchurch.system.remote.RemotePreacherActivity.class)));
        if (btnRM != null) btnRM.setOnClickListener(v -> startActivity(
            new Intent(this, com.wisesmartchurch.system.remote.RemoteMaestroActivity.class)));
    }

    // ── Panels init ───────────────────────────────────
    private void initPanels() {
        // ── Bible panel
        if (panelBible != null) {
            biblePanel = new BiblePanelController(this, panelBible, bibleEngine);
            biblePanel.setOnVerseSelected((text, ref, bm, bu) -> {
                if (bm != null) bgMode = bm;
                if (bu != null) bgUrl  = bu;
                setContent(text, ref);
            });
        }

        // ── Songs panel
        if (panelSongs != null) {
            songsPanel = new SongsPanelController(this, panelSongs, db);
            songsPanel.setOnStropheSelected((text, label, bm, bu) -> {
                if (bm != null) bgMode = bm;
                if (bu != null) bgUrl  = bu;
                setContent(text, label);
            });
        }

        // ── Playlist panel
        if (panelPlaylist != null) {
            playlistPanel = new PlaylistPanelController(this, panelPlaylist, db);
            playlistPanel.setOnItemSelected(item -> {
                if (item.bgMode != null) bgMode = item.bgMode;
                if (item.bgUrl  != null) bgUrl  = item.bgUrl;
                setContent(item.text, item.ref != null ? item.ref : item.label);
            });
            playlistPanel.setOnProjectRequested(item -> {
                if (item.bgMode != null) bgMode = item.bgMode;
                if (item.bgUrl  != null) bgUrl  = item.bgUrl;
                setContent(item.text, item.ref != null ? item.ref : item.label);
                projectCurrent();
            });
        }

        // ── Announce panel
        if (panelAnnounce != null) {
            announcePanel = new AnnouncePanelController(this, panelAnnounce, db);
            announcePanel.setOnAnnounceSelected((text, title, bm, bu) -> {
                if (bm != null) bgMode = bm;
                if (bu != null) bgUrl  = bu;
                setContent(text, title);
            });
        }

        // ── Bibles panel
        // panel_bibles est un LinearLayout dans le XML — trouver ll_bibles_content directement
        if (panelBibles != null) {
            LinearLayout llBibles = panelBibles.findViewById(R.id.ll_bibles_content);
            if (llBibles == null) {
                // Si le LinearLayout racine n'a pas l'ID, chercher le premier LinearLayout enfant de ScrollView
                if (panelBibles instanceof LinearLayout) {
                    LinearLayout root = (LinearLayout) panelBibles;
                    for (int i = 0; i < root.getChildCount(); i++) {
                        View child = root.getChildAt(i);
                        if (child instanceof ScrollView) {
                            View sv0 = ((ScrollView) child).getChildAt(0);
                            if (sv0 instanceof LinearLayout) { llBibles = (LinearLayout) sv0; break; }
                        }
                        if (child instanceof LinearLayout) { llBibles = (LinearLayout) child; break; }
                    }
                }
            }
            if (llBibles == null) {
                // Dernier recours: utiliser panelBibles lui-même s'il est un LinearLayout
                if (panelBibles instanceof LinearLayout) llBibles = (LinearLayout) panelBibles;
            }
            if (llBibles != null) {
                biblesFragment = new BiblesFragment(this, llBibles, bibleEngine);
                biblesFragment.setOnBibleChanged((active, main) -> {
                    if (biblePanel != null && main != null && !main.isEmpty())
                        biblePanel.setBibleCode(main);
                    toast("Bible: " + main);
                });
            }
        }

        // ── Settings panel (ScrollView → LinearLayout child)
        if (panelSettings != null) {
            LinearLayout llSettings = panelSettings.findViewById(R.id.ll_settings_content);
            if (llSettings == null && panelSettings instanceof ScrollView) {
                View child = ((ScrollView) panelSettings).getChildAt(0);
                if (child instanceof LinearLayout) llSettings = (LinearLayout) child;
            }
            if (llSettings != null) {
                settingsFragment = new SettingsFragment(this, llSettings);
                settingsFragment.setOnSettingsChanged(prefs -> applySettings(prefs));
            }
        }

        showPanel(0);
    }

    private void showPanel(int idx) {
        View[] panels = {panelBible, panelSongs, panelAnnounce, panelPlaylist,
                         panelBibles, panelSettings, panelScreens};
        for (int i = 0; i < panels.length; i++)
            if (panels[i] != null)
                panels[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
    }

    // ── Content + projection ──────────────────────────
    private void setContent(String text, String ref) {
        if (tvRef  != null) tvRef.setText(ref  != null ? ref  : "");
        if (tvText != null) tvText.setText(text != null ? text : "");
        currentPayload = new ProjectionPayload();
        currentPayload.type       = "project";
        currentPayload.text       = text;
        currentPayload.ref        = ref;
        currentPayload.color      = textColor;
        currentPayload.fontSize   = fontSize;
        currentPayload.overlaySlot = activeSlot;
        ProjectionPayload.BgInfo bg = new ProjectionPayload.BgInfo();
        bg.mode = bgMode; bg.url = bgUrl; bg.color = "#000000";
        currentPayload.bg = bg;
    }

    public void projectCurrent() {
        if (currentPayload == null) { toast("Aucun contenu sélectionné"); return; }
        broadcast(currentPayload.toJson().toString());
        flash(btnProject);
    }

    public void projectClear() {
        try {
            JSONObject j = new JSONObject(); j.put("type", "clear");
            broadcast(j.toString());
        } catch (Exception ignored) {}
    }

    private void broadcast(String json) {
        if (bound && server != null) server.broadcast(json);
    }

    // ── Navigation ────────────────────────────────────
    private void navigatePrev() {
        int tab = tabs != null ? tabs.getSelectedTabPosition() : 0;
        if (tab == 0 && biblePanel   != null) biblePanel.navigatePrev();
        else if (tab == 3 && playlistPanel != null) playlistPanel.navigatePrev();
    }
    private void navigateNext() {
        int tab = tabs != null ? tabs.getSelectedTabPosition() : 0;
        if (tab == 0 && biblePanel   != null) biblePanel.navigateNext();
        else if (tab == 3 && playlistPanel != null) playlistPanel.navigateNext();
    }

    // ── Lower thirds ──────────────────────────────────
    private void showLt() {
        try {
            JSONObject j = new JSONObject(); j.put("type", "lt");
            JSONObject lt = new JSONObject();
            lt.put("show",  true);
            lt.put("name",  etLtName  != null ? etLtName.getText().toString()  : "");
            lt.put("role",  etLtRole  != null ? etLtRole.getText().toString()   : "");
            lt.put("type",  spLtType  != null ? ltTypeCode(spLtType.getSelectedItemPosition())  : "pred");
            lt.put("style", spLtStyle != null ? ltStyleCode(spLtStyle.getSelectedItemPosition()): "standard");
            j.put("lt", lt);
            broadcast(j.toString());
            if (tvLtPreview != null) {
                tvLtPreview.setText(lt.optString("name") + "\n" + lt.optString("role"));
                tvLtPreview.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) { Log.e(TAG, "showLt", e); }
    }

    private void hideLt() {
        try {
            JSONObject j = new JSONObject(); j.put("type", "lt");
            JSONObject lt = new JSONObject(); lt.put("show", false);
            j.put("lt", lt); broadcast(j.toString());
            if (tvLtPreview != null) tvLtPreview.setVisibility(View.GONE);
        } catch (Exception ignored) {}
    }

    private String ltTypeCode(int pos) {
        String[] c = {"pred","ado","chant","exhort","annonce","temoignage"};
        return pos >= 0 && pos < c.length ? c[pos] : "pred";
    }
    private String ltStyleCode(int pos) {
        String[] c = {"standard","cinema","minimal","gradient"};
        return pos >= 0 && pos < c.length ? c[pos] : "standard";
    }

    // ── Logo animation ────────────────────────────────
    private void broadcastLogo() {
        SharedPreferences prefs = getSharedPreferences("wsc_prefs", MODE_PRIVATE);
        try {
            JSONObject j = new JSONObject();
            j.put("type",       "logo_anim");
            j.put("logoUrl",    prefs.getString("church_logo",  ""));
            j.put("churchName", prefs.getString("church_name",  "Wise Smart Church System"));
            j.put("tag",        prefs.getString("church_tag",   "Église — Culte de Louange"));
            j.put("duration",   prefs.getInt("logo_dur", 6));
            broadcast(j.toString());
            toast("✨ Animation logo diffusée");
        } catch (Exception e) { Log.e(TAG, "broadcastLogo", e); }
    }

    // ── Camera ────────────────────────────────────────
    private void toggleCamera() {
        camActive = !camActive;
        try {
            JSONObject j = new JSONObject();
            j.put("type", camActive ? "camera_on" : "camera_off");
            broadcast(j.toString());
        } catch (Exception ignored) {}
        if (btnCamera != null) {
            btnCamera.setText(camActive ? "📷 CAM ●" : "📷 Caméra");
            btnCamera.setTextColor(Color.parseColor(camActive ? "#22C55E" : "#64748B"));
        }
        toast(camActive ? "Caméra activée" : "Caméra désactivée");
    }

    // ── Background ────────────────────────────────────
    private void initBgButtons() {
        if (btnBgBlack    != null) btnBgBlack.setOnClickListener(v    -> setBgMode("black"));
        if (btnBgGradient != null) btnBgGradient.setOnClickListener(v -> setBgMode("gradient"));
        if (btnBgImage    != null) btnBgImage.setOnClickListener(v    -> setBgMode("image"));
    }
    private void setBgMode(String mode) {
        bgMode = mode;
        try {
            JSONObject j = new JSONObject(); j.put("type", "bg");
            JSONObject bg = new JSONObject(); bg.put("mode", mode); bg.put("url", bgUrl);
            j.put("bg", bg); broadcast(j.toString());
        } catch (Exception ignored) {}
        refreshBgButtons();
    }
    private void setBgUrl(String url) { bgUrl = url; bgMode = "image"; setBgMode("image"); }
    private void refreshBgButtons() {
        if (btnBgBlack    != null) btnBgBlack.setAlpha("black".equals(bgMode) ? 1f : 0.5f);
        if (btnBgGradient != null) btnBgGradient.setAlpha("gradient".equals(bgMode) ? 1f : 0.5f);
        if (btnBgImage    != null) btnBgImage.setAlpha("image".equals(bgMode) ? 1f : 0.5f);
    }

    // ── Colors ────────────────────────────────────────
    private void initColorButtons() {
        if (btnColorWhite != null) btnColorWhite.setOnClickListener(v -> textColor = "#FFFFFF");
        if (btnColorGold  != null) btnColorGold.setOnClickListener(v  -> textColor = "#FDE68A");
        if (btnColorGreen != null) btnColorGreen.setOnClickListener(v -> textColor = "#BBF7D0");
        if (btnColorBlue  != null) btnColorBlue.setOnClickListener(v  -> textColor = "#BFDBFE");
        if (btnColorRed   != null) btnColorRed.setOnClickListener(v   -> textColor = "#FCA5A5");
    }

    // ── Font sizes ────────────────────────────────────
    private void initFontButtons() {
        if (btnFsS  != null) btnFsS.setOnClickListener(v  -> { fontSize = "3.8vw"; highlightFs(btnFsS); });
        if (btnFsM  != null) btnFsM.setOnClickListener(v  -> { fontSize = "5.5vw"; highlightFs(btnFsM); });
        if (btnFsL  != null) btnFsL.setOnClickListener(v  -> { fontSize = "7vw";   highlightFs(btnFsL); });
        if (btnFsXL != null) btnFsXL.setOnClickListener(v -> { fontSize = "9vw";   highlightFs(btnFsXL); });
    }
    private void highlightFs(Button active) {
        Button[] btns = {btnFsS, btnFsM, btnFsL, btnFsXL};
        for (Button b : btns) {
            if (b == null) continue;
            b.setBackgroundResource(b == active ? R.drawable.bg_slot_active : R.drawable.bg_btn_icon);
        }
    }

    // ── Slots 1-4 ─────────────────────────────────────
    private void initSlotButtons() {
        Button[] slots = {btnSlot1, btnSlot2, btnSlot3, btnSlot4};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            final int slot = i + 1;
            slots[i].setOnClickListener(v -> selectSlot(slot));
        }
    }
    private void selectSlot(int slot) {
        activeSlot = slot;
        Button[] slots = {btnSlot1, btnSlot2, btnSlot3, btnSlot4};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            slots[i].setBackgroundResource(i+1 == slot ? R.drawable.bg_slot_active : R.drawable.bg_slot_btn);
            slots[i].setAlpha(i+1 == slot ? 1f : 0.5f);
        }
        if (currentPayload != null) currentPayload.overlaySlot = slot;
    }

    // ── Remote buttons ────────────────────────────────
    private void initRemoteButtons() { /* handled in bindViews */ }

    // ── Service ───────────────────────────────────────
    private void bindServerService() {
        Intent i = new Intent(this, WscServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        bindService(i, svcConn, BIND_AUTO_CREATE);
    }

    @Override public void onClientConnected(String id, String ip)    { ui.post(this::updateStatus); }
    @Override public void onClientDisconnected(String id)             { ui.post(this::updateStatus); }
    @Override public void onScreenCount(int count, List<String> ips) {
        ui.post(() -> { if (tvScreens != null) tvScreens.setText(count + " écran(s)"); });
    }

    private void updateStatus() {
        if (!bound || server == null) return;
        int cnt = server.getClientCount();
        String ip = server.getServerIp();
        if (tvIp     != null) tvIp.setText(ip + ":9000");
        if (tvScreens!= null) tvScreens.setText(cnt + " écran(s)");
        if (tvStatus != null) {
            tvStatus.setText(cnt > 0 ? "● EN LIGNE" : "○ En attente");
            tvStatus.setTextColor(Color.parseColor(cnt > 0 ? "#22C55E" : "#F59E0B"));
        }
    }

    // ── Prefs ─────────────────────────────────────────
    private void restorePrefs() {
        SharedPreferences prefs = getSharedPreferences("wsc_prefs", MODE_PRIVATE);
        String code = prefs.getString("main_bible", "");
        if (!code.isEmpty() && biblePanel != null) biblePanel.setBibleCode(code);
        applySettings(prefs);
    }
    private void applySettings(SharedPreferences prefs) {
        String name = prefs.getString("church_name", "Wise Smart Church System");
        TextView tvApp = findViewById(R.id.tv_app_name);
        if (tvApp != null) tvApp.setText(name);
    }

    // ── Keyboard shortcuts ────────────────────────────
    private void setupKeyboard() {}

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_SPACE:
                case KeyEvent.KEYCODE_ENTER:     projectCurrent(); return true;
                case KeyEvent.KEYCODE_ESCAPE:    projectClear();   return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_PAGE_DOWN: navigateNext();   return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_PAGE_UP:   navigatePrev();   return true;
                case KeyEvent.KEYCODE_L:         broadcastLogo();  return true;
                case KeyEvent.KEYCODE_1: selectSlot(1); return true;
                case KeyEvent.KEYCODE_2: selectSlot(2); return true;
                case KeyEvent.KEYCODE_3: selectSlot(3); return true;
                case KeyEvent.KEYCODE_4: selectSlot(4); return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ── Permissions ───────────────────────────────────
    private void requestPermissions() {
        List<String> need = new ArrayList<>();
        List<String> perms = new ArrayList<>(Arrays.asList(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            perms.add(Manifest.permission.READ_MEDIA_VIDEO);
            perms.add(Manifest.permission.READ_MEDIA_AUDIO);
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        for (String p : perms)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                need.add(p);
        if (!need.isEmpty())
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERM);
    }

    // ── Helpers ───────────────────────────────────────
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private void flash(View v) {
        if (v == null) return;
        v.setAlpha(0.5f); ui.postDelayed(() -> v.setAlpha(1f), 180);
    }
    private void copyIp() {
        if (!bound || server == null) return;
        String ip = server.getServerIp() + ":9000";
        ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
            .setPrimaryClip(android.content.ClipData.newPlainText("WSC IP", ip));
        toast("Copié: " + ip);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { if (bound && server != null) server.removeListener(this); } catch (Exception ignored) {}
        try { if (bound) unbindService(svcConn); } catch (Exception ignored) {}
    }
    @Override protected void onResume() { super.onResume(); if (bound) updateStatus(); }
}
