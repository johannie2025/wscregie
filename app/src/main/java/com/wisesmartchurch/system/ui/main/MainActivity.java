package com.wisesmartchurch.system.ui.main;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.tabs.TabLayout;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.bible.BibleEngine;
import com.wisesmartchurch.system.camera.CameraManager;
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

    private static final String TAG     = "WSC_Main";
    private static final int    REQ_PERM    = 100;
    private static final int    REQ_MEDIA   = 101;

    // ── Services ──
    private WscServerService server;
    private boolean          bound = false;
    private final ServiceConnection svcConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            server = ((WscServerService.LocalBinder) b).getService();
            server.addListener(MainActivity.this);
            bound = true; updateStatus();
        }
        @Override public void onServiceDisconnected(ComponentName n) { bound = false; server = null; }
    };

    // ── Core ──
    private BibleEngine      bibleEngine;
    private StreamingManager streamMgr;
    private CameraManager    cameraMgr;
    private AppDatabase      db;

    // ── Panels ──
    private BiblePanelController   biblePanel;
    private SongsPanelController   songsPanel;
    private PlaylistPanelController playlistPanel;
    private AnnouncePanelController announcePanel;
    private BiblesFragment         biblesFragment;
    private SettingsFragment       settingsFragment;

    // ── Projection state ──
    private ProjectionPayload currentPayload;
    private String textColor    = "#FFFFFF";
    private String fontSize     = "5.5vw";
    private String bgMode       = "black";
    private String bgUrl        = "";
    private int    activeSlot   = 1;
    private boolean camActive   = false;

    // ── Views ──
    private TextView  tvStatus, tvScreens, tvIp;
    private TextView  tvRef, tvText;
    private TabLayout tabs;
    private FrameLayout panelContainer;
    private View panelBible,panelSongs,panelAnnounce,panelPlaylist,panelBibles,panelSettings,panelScreens;
    private EditText etSearch, etLtName, etLtRole, etBgUrl;
    private Button   btnProject, btnClear, btnNavPrev, btnNavNext;
    private Button   btnLtShow, btnLtHide, btnLogoAnim, btnCamera;
    private Button   btnBgBlack, btnBgGradient, btnBgImage;
    private Button   btnFsS, btnFsM, btnFsL, btnFsXL;
    private Button   btnSlot1, btnSlot2, btnSlot3, btnSlot4;
    private Spinner  spLtType, spLtStyle;
    private TextView tvLtPreview;
    private View     btnColorWhite, btnColorGold, btnColorGreen, btnColorBlue, btnColorRed;

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        db = AppDatabase.getInstance(this);
        bibleEngine = new BibleEngine(this);
        streamMgr   = new StreamingManager(this);
        cameraMgr   = new CameraManager(this);

        requestPermissions();
        bindViews();
        initPanels();
        bindService();
        restorePrefs();
        setupKeyboard();
        initRemoteButtons();
        initBgButtons();
        initColorButtons();
        initFontButtons();
        initSlotButtons();
    }

    // ── View bindings ─────────────────────────────────────
    private void bindViews() {
        tvStatus   = findViewById(R.id.tv_server_status);
        tvScreens  = findViewById(R.id.tv_screen_count);
        tvIp       = findViewById(R.id.tv_server_ip);
        tvRef      = findViewById(R.id.tv_current_ref);
        tvText     = findViewById(R.id.tv_current_text);
        tabs       = findViewById(R.id.center_tabs);

        panelBible    = findViewById(R.id.panel_bible);
        panelSongs    = findViewById(R.id.panel_songs);
        panelAnnounce = findViewById(R.id.panel_announce);
        panelPlaylist = findViewById(R.id.panel_playlist);
        panelBibles   = findViewById(R.id.panel_bibles);
        panelSettings = findViewById(R.id.panel_settings);
        panelScreens  = findViewById(R.id.panel_screens);

        etSearch    = findViewById(R.id.et_bible_search);
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
        tvLtPreview = findViewById(R.id.tv_lt_preview);

        btnColorWhite = findViewById(R.id.btn_color_white);
        btnColorGold  = findViewById(R.id.btn_color_gold);
        btnColorGreen = findViewById(R.id.btn_color_green);
        btnColorBlue  = findViewById(R.id.btn_color_blue);
        btnColorRed   = findViewById(R.id.btn_color_red);

        btnProject.setOnClickListener(v -> projectCurrent());
        btnClear.setOnClickListener(v -> projectClear());
        btnNavPrev.setOnClickListener(v -> navigatePrev());
        btnNavNext.setOnClickListener(v -> navigateNext());
        btnLtShow.setOnClickListener(v -> showLt());
        btnLtHide.setOnClickListener(v -> hideLt());
        btnLogoAnim.setOnClickListener(v -> broadcastLogo());
        btnCamera.setOnClickListener(v -> toggleCamera());
        if (tvIp != null) tvIp.setOnClickListener(v -> copyIp());
        if (etBgUrl != null) etBgUrl.setOnEditorActionListener((v, a, e) -> { setBgUrl(v.getText().toString()); return false; });

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) { showPanel(t.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        View btnScreensBtn = findViewById(R.id.btn_screens);
        if (btnScreensBtn != null) btnScreensBtn.setOnClickListener(v -> startActivity(new Intent(this, ScreensManagerActivity.class)));
    }

    // ── Panels ───────────────────────────────────────────
    private void initPanels() {
        biblePanel    = new BiblePanelController(this, panelBible, bibleEngine);
        biblePanel.setOnVerseSelected((text, ref, bm, bu) -> {
            bgMode = bm != null ? bm : bgMode;
            bgUrl  = bu != null ? bu  : bgUrl;
            setContent(text, ref);
        });

        songsPanel    = new SongsPanelController(this, panelSongs, db);
        songsPanel.setOnStropheSelected((text, label, bm, bu) -> {
            bgMode = bm != null ? bm : bgMode;
            bgUrl  = bu != null ? bu  : bgUrl;
            setContent(text, label);
        });

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

        announcePanel = new AnnouncePanelController(this, panelAnnounce, db);
        announcePanel.setOnAnnounceSelected((text, title, bm, bu) -> {
            if (bm != null) bgMode = bm;
            if (bu != null) bgUrl  = bu;
            setContent(text, title);
        });

        // Bibles
        LinearLayout llBibles = panelBibles != null ? ((android.widget.ScrollView) panelBibles).findViewById(R.id.ll_bibles_content) : null;
        if (llBibles == null && panelBibles instanceof LinearLayout) {
            // fallback: find inside
            llBibles = panelBibles.findViewById(R.id.ll_bibles_content);
        }
        if (llBibles == null && panelBibles != null) {
            // panel_bibles is a LinearLayout → first ScrollView child
            if (panelBibles instanceof LinearLayout) {
                for (int i = 0; i < ((LinearLayout) panelBibles).getChildCount(); i++) {
                    View child = ((LinearLayout) panelBibles).getChildAt(i);
                    if (child instanceof android.widget.ScrollView) {
                        llBibles = (LinearLayout) ((android.widget.ScrollView) child).getChildAt(0);
                        break;
                    }
                }
            }
        }
        if (llBibles != null) {
            biblesFragment = new BiblesFragment(this, llBibles, bibleEngine);
            biblesFragment.setOnBibleChanged((active, main) -> {
                biblePanel.setBibleCode(main);
                toast("Bible active: " + main);
            });
        }

        // Settings
        LinearLayout llSettings = panelSettings != null ? panelSettings.findViewById(R.id.ll_settings_content) : null;
        if (llSettings == null && panelSettings instanceof android.widget.ScrollView) {
            llSettings = (LinearLayout) ((android.widget.ScrollView) panelSettings).getChildAt(0);
        }
        if (llSettings != null) {
            settingsFragment = new SettingsFragment(this, llSettings);
            settingsFragment.setOnSettingsChanged(prefs -> applySettingsFromPrefs(prefs));
        }

        showPanel(0);
    }

    private void showPanel(int idx) {
        View[] panels = {panelBible, panelSongs, panelAnnounce, panelPlaylist, panelBibles, panelSettings, panelScreens};
        for (int i = 0; i < panels.length; i++)
            if (panels[i] != null) panels[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
    }

    // ── Content ──────────────────────────────────────────
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

    // ── Projection ────────────────────────────────────────
    public void projectCurrent() {
        if (currentPayload == null) { toast("Aucun contenu sélectionné"); return; }
        broadcast(currentPayload.toJson().toString());
        flash(btnProject);
    }

    public void projectClear() {
        try { JSONObject j = new JSONObject(); j.put("type","clear"); broadcast(j.toString()); } catch (Exception ignored) {}
    }

    private void broadcast(String json) {
        if (bound && server != null) server.broadcast(json);
    }

    // ── Navigation ────────────────────────────────────────
    private void navigatePrev() {
        if (tabs.getSelectedTabPosition() == 0 && biblePanel != null) biblePanel.navigatePrev();
        else if (tabs.getSelectedTabPosition() == 3 && playlistPanel != null) playlistPanel.navigatePrev();
    }
    private void navigateNext() {
        if (tabs.getSelectedTabPosition() == 0 && biblePanel != null) biblePanel.navigateNext();
        else if (tabs.getSelectedTabPosition() == 3 && playlistPanel != null) playlistPanel.navigateNext();
    }

    // ── Lower Thirds ──────────────────────────────────────
    private void showLt() {
        try {
            JSONObject j = new JSONObject(); j.put("type","lt");
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
        } catch (Exception e) { Log.e(TAG,"lt",e); }
    }

    private void hideLt() {
        try {
            JSONObject j = new JSONObject(); j.put("type","lt");
            JSONObject lt = new JSONObject(); lt.put("show",false); j.put("lt",lt);
            broadcast(j.toString());
            if (tvLtPreview != null) tvLtPreview.setVisibility(View.GONE);
        } catch (Exception e) { Log.e(TAG,"lt",e); }
    }

    private String ltTypeCode(int pos) {
        String[] c = {"pred","ado","chant","exhort","annonce","temoignage"};
        return pos < c.length ? c[pos] : "pred";
    }
    private String ltStyleCode(int pos) {
        String[] c = {"standard","cinema","minimal","gradient"};
        return pos < c.length ? c[pos] : "standard";
    }

    // ── Logo Animation ────────────────────────────────────
    private void broadcastLogo() {
        SharedPreferences prefs = getSharedPreferences("wsc_prefs", MODE_PRIVATE);
        try {
            JSONObject j = new JSONObject();
            j.put("type",       "logo_anim");
            j.put("logoUrl",    prefs.getString("church_logo", ""));
            j.put("churchName", prefs.getString("church_name", "Wise Smart Church System"));
            j.put("tag",        prefs.getString("church_tag",  "Église — Culte de Louange"));
            j.put("duration",   prefs.getInt("logo_dur", 6));
            broadcast(j.toString());
            toast("✨ Animation logo diffusée");
        } catch (Exception e) { Log.e(TAG,"logo",e); }
    }

    // ── Camera ────────────────────────────────────────────
    private void toggleCamera() {
        camActive = !camActive;
        try {
            JSONObject j = new JSONObject();
            j.put("type", camActive ? "camera_on" : "camera_off");
            if (camActive) j.put("cameraStream", "ws://" + NetworkUtils.getLocalIp(this) + ":9001");
            broadcast(j.toString());
        } catch (Exception ignored) {}
        if (btnCamera != null) btnCamera.setText(camActive ? "📷 CAM ●" : "📷 Caméra");
        btnCamera.setTextColor(camActive ? Color.parseColor("#22C55E") : Color.parseColor("#64748B"));
        toast(camActive ? "Caméra activée sur les écrans" : "Caméra désactivée");
    }

    // ── Background ───────────────────────────────────────
    private void initBgButtons() {
        if (btnBgBlack != null)    btnBgBlack.setOnClickListener(v -> setBgMode("black"));
        if (btnBgGradient != null) btnBgGradient.setOnClickListener(v -> setBgMode("gradient"));
        if (btnBgImage != null)    btnBgImage.setOnClickListener(v -> setBgMode("image"));
    }
    private void setBgMode(String mode) {
        bgMode = mode;
        refreshBgButtons();
        try { JSONObject j = new JSONObject(); j.put("type","bg"); JSONObject bg = new JSONObject(); bg.put("mode",mode); bg.put("url",bgUrl); j.put("bg",bg); broadcast(j.toString()); } catch (Exception ignored) {}
    }
    private void setBgUrl(String url) { bgUrl = url; bgMode = "image"; refreshBgButtons(); setBgMode("image"); }
    private void refreshBgButtons() {
        int activeColor = Color.parseColor("#2563EB"), inactiveColor = Color.parseColor("#1E2535");
        if (btnBgBlack    != null) btnBgBlack.setBackgroundColor("black".equals(bgMode) ? activeColor : inactiveColor);
        if (btnBgGradient != null) btnBgGradient.setBackgroundColor("gradient".equals(bgMode) ? activeColor : inactiveColor);
        if (btnBgImage    != null) btnBgImage.setBackgroundColor("image".equals(bgMode) ? activeColor : inactiveColor);
    }

    // ── Colors ────────────────────────────────────────────
    private void initColorButtons() {
        if (btnColorWhite != null) btnColorWhite.setOnClickListener(v -> setColor("#FFFFFF"));
        if (btnColorGold  != null) btnColorGold.setOnClickListener(v  -> setColor("#FDE68A"));
        if (btnColorGreen != null) btnColorGreen.setOnClickListener(v -> setColor("#BBF7D0"));
        if (btnColorBlue  != null) btnColorBlue.setOnClickListener(v  -> setColor("#BFDBFE"));
        if (btnColorRed   != null) btnColorRed.setOnClickListener(v   -> setColor("#FCA5A5"));
    }
    private void setColor(String c) { textColor = c; }

    // ── Font sizes ────────────────────────────────────────
    private void initFontButtons() {
        if (btnFsS  != null) btnFsS.setOnClickListener(v  -> setFs("3.8vw", btnFsS));
        if (btnFsM  != null) btnFsM.setOnClickListener(v  -> setFs("5.5vw", btnFsM));
        if (btnFsL  != null) btnFsL.setOnClickListener(v  -> setFs("7vw",   btnFsL));
        if (btnFsXL != null) btnFsXL.setOnClickListener(v -> setFs("9vw",   btnFsXL));
    }
    private void setFs(String fs, View active) {
        fontSize = fs;
        View[] btns = {btnFsS, btnFsM, btnFsL, btnFsXL};
        for (View b : btns) { if (b != null) b.setBackgroundResource(b == active ? R.drawable.bg_slot_active : R.drawable.bg_btn_icon); }
    }

    // ── Slots ─────────────────────────────────────────────
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

    // ── Remote buttons ────────────────────────────────────
    private void initRemoteButtons() {
        View btnRPred = findViewById(R.id.btn_remote_preacher);
        View btnRMaes = findViewById(R.id.btn_remote_maestro);
        if (btnRPred != null) btnRPred.setOnClickListener(v -> startActivity(new Intent(this, com.wisesmartchurch.system.remote.RemotePreacherActivity.class)));
        if (btnRMaes != null) btnRMaes.setOnClickListener(v -> startActivity(new Intent(this, com.wisesmartchurch.system.remote.RemoteMaestroActivity.class)));
    }

    // ── Service ───────────────────────────────────────────
    private void bindService() {
        Intent i = new Intent(this, WscServerService.class);
        startService(i); bindService(i, svcConn, BIND_AUTO_CREATE);
    }

    @Override public void onClientConnected(String id, String ip) { ui.post(this::updateStatus); }
    @Override public void onClientDisconnected(String id) { ui.post(this::updateStatus); }
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

    // ── Preferences ───────────────────────────────────────
    private void restorePrefs() {
        SharedPreferences prefs = getSharedPreferences("wsc_prefs", MODE_PRIVATE);
        String code = prefs.getString("main_bible", "");
        if (!code.isEmpty() && biblePanel != null) biblePanel.setBibleCode(code);
        applySettingsFromPrefs(prefs);
    }

    private void applySettingsFromPrefs(SharedPreferences prefs) {
        String name = prefs.getString("church_name", "Wise Smart Church System");
        setTitle(name);
        // update topbar
        TextView tvApp = findViewById(R.id.tv_app_name);
        if (tvApp != null) tvApp.setText(name);
    }

    // ── Keyboard ──────────────────────────────────────────
    private void setupKeyboard() {
        if (etSearch != null) etSearch.setOnEditorActionListener((v, action, event) -> {
            // handled by BiblePanel
            return false;
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_SPACE:
                case KeyEvent.KEYCODE_ENTER:    projectCurrent(); return true;
                case KeyEvent.KEYCODE_ESCAPE:   projectClear();   return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_PAGE_DOWN: navigateNext();  return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_PAGE_UP:  navigatePrev();   return true;
                case KeyEvent.KEYCODE_L:        broadcastLogo();  return true;
                case KeyEvent.KEYCODE_1: selectSlot(1); return true;
                case KeyEvent.KEYCODE_2: selectSlot(2); return true;
                case KeyEvent.KEYCODE_3: selectSlot(3); return true;
                case KeyEvent.KEYCODE_4: selectSlot(4); return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ── Permissions ───────────────────────────────────────
    private void requestPermissions() {
        List<String> need = new ArrayList<>();
        String[] all = { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS };
        // Read media permissions (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            all = new String[]{ Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS };
        }
        for (String p : all)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (!need.isEmpty())
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERM);
    }

    // ── Helpers ───────────────────────────────────────────
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private void flash(View v) { if (v==null) return; v.setAlpha(.5f); ui.postDelayed(()->v.setAlpha(1f),180); }
    private void copyIp() {
        if (!bound || server == null) return;
        String ip = server.getServerIp() + ":9000";
        ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
            .setPrimaryClip(android.content.ClipData.newPlainText("WSC IP", ip));
        toast("Copié: " + ip);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (bound && server != null) server.removeListener(this);
        if (bound) unbindService(svcConn);
    }

    @Override protected void onResume() { super.onResume(); if (bound) updateStatus(); }
}
