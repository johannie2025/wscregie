package com.wisesmartchurch.system.ui.bible;

import android.content.Context;
import android.view.*;
import android.widget.*;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.bible.BibleEngine;
import com.wisesmartchurch.system.bible.BibleEngine.BibleSource;
import com.wisesmartchurch.system.model.BibleMeta;
import java.util.*;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

/** Panneau de téléchargement et gestion des bibles.
 *  Instancié par MainActivity et injecté dans panel_bibles. */
public class BiblesFragment {

    public interface OnBibleChangedListener { void onChanged(List<String> activeCodes, String mainCode); }

    private final Context ctx;
    private final LinearLayout container;
    private final BibleEngine engine;
    private OnBibleChangedListener listener;
    private String filterLang = "all";
    private final Handler ui = new Handler(Looper.getMainLooper());

    public BiblesFragment(Context ctx, LinearLayout container, BibleEngine engine) {
        this.ctx = ctx; this.container = container; this.engine = engine;
        render();
    }

    public void setOnBibleChanged(OnBibleChangedListener l) { this.listener = l; }

    public void render() {
        container.removeAllViews();
        // Lang filter row
        LinearLayout filterRow = new LinearLayout(ctx);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0,0,0,10);
        String[] langs = {"all","fr","es","en","pt"};
        String[] labels = {"Tout","🇫🇷 FR","🇪🇸 ES","🇺🇸 EN","🇧🇷 PT"};
        for (int i = 0; i < langs.length; i++) {
            final String l = langs[i];
            Button b = makeFilterBtn(labels[i], l.equals(filterLang));
            b.setOnClickListener(v -> { filterLang = l; render(); });
            filterRow.addView(b);
            if (i < langs.length-1) { View sp = new View(ctx); sp.setLayoutParams(new LinearLayout.LayoutParams(8,1)); filterRow.addView(sp); }
        }
        container.addView(filterRow);

        // Progress bar
        ProgressBar pb = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        pb.setId(R.id.dl_progress_bar);
        pb.setMax(100); pb.setProgress(0);
        pb.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8);
        pbp.setMargins(0,0,0,8);
        container.addView(pb, pbp);

        TextView tvStatus = new TextView(ctx);
        tvStatus.setId(R.id.tv_dl_status);
        tvStatus.setTextSize(10f); tvStatus.setTextColor(0xFF64748B);
        container.addView(tvStatus);

        // Bible list
        Executors.newSingleThreadExecutor().execute(() -> {
            List<BibleMeta> installed = engine.getAllMeta();
            Map<String, BibleMeta> instMap = new HashMap<>();
            for (BibleMeta m : installed) instMap.put(m.code, m);

            ui.post(() -> {
                for (BibleSource src : BibleEngine.SOURCES) {
                    if (!"all".equals(filterLang) && !src.lang.equals(filterLang)) continue;
                    BibleMeta meta = instMap.get(src.code);
                    boolean downloaded = meta != null;
                    boolean active = downloaded && meta.isActive;

                    // Card
                    LinearLayout card = new LinearLayout(ctx);
                    card.setOrientation(LinearLayout.HORIZONTAL);
                    card.setBackgroundResource(R.drawable.bg_card);
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    clp.setMargins(0,0,0,8);
                    card.setLayoutParams(clp);
                    card.setPadding(10,10,10,10);
                    card.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    // Info
                    LinearLayout info = new LinearLayout(ctx);
                    info.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    info.setLayoutParams(ilp);

                    TextView tvName = new TextView(ctx);
                    tvName.setText(src.name);
                    tvName.setTextSize(12f);
                    tvName.setTextColor(active ? 0xFFF0C040 : 0xFFE2E8F0);
                    tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                    info.addView(tvName);

                    TextView tvMeta = new TextView(ctx);
                    tvMeta.setText(src.lang.toUpperCase() + " · " + src.code + (downloaded ? (meta.verseCount>0?" · "+meta.verseCount+" v":"")+" · ✓ Hors-ligne":""));
                    tvMeta.setTextSize(10f);
                    tvMeta.setTextColor(downloaded ? 0xFF22C55E : 0xFF64748B);
                    info.addView(tvMeta);
                    card.addView(info);

                    if (downloaded) {
                        // Activate toggle
                        Button btnAct = makeSmallBtn(active ? "✓ Active" : "Activer", active ? 0xFFF0C040 : 0xFF64748B);
                        btnAct.setOnClickListener(v -> toggleActive(src.code, !active, pb, tvStatus));
                        card.addView(btnAct);
                        View sp = new View(ctx); sp.setLayoutParams(new LinearLayout.LayoutParams(4,1)); card.addView(sp);
                        // Set main
                        Button btnMain = makeSmallBtn("Principal", 0xFF2563EB);
                        btnMain.setOnClickListener(v -> setMain(src.code));
                        card.addView(btnMain);
                        View sp2 = new View(ctx); sp2.setLayoutParams(new LinearLayout.LayoutParams(4,1)); card.addView(sp2);
                        // Export
                        Button btnExp = makeSmallBtn("⬆", 0xFF64748B);
                        btnExp.setOnClickListener(v -> exportBible(src.code));
                        card.addView(btnExp);
                        View sp3 = new View(ctx); sp3.setLayoutParams(new LinearLayout.LayoutParams(4,1)); card.addView(sp3);
                        // Delete
                        Button btnDel = makeSmallBtn("🗑", 0xFFEF4444);
                        btnDel.setOnClickListener(v -> deleteBible(src.code, pb, tvStatus));
                        card.addView(btnDel);
                    } else {
                        Button btnDl = makeSmallBtn("⬇ Télécharger", 0xFF2563EB);
                        btnDl.setOnClickListener(v -> downloadBible(src, pb, tvStatus));
                        card.addView(btnDl);
                    }
                    container.addView(card);
                }

                // Import local JSON
                View divider = new View(ctx);
                divider.setBackgroundColor(0x14FFFFFF);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
                dp.setMargins(0,12,0,12);
                container.addView(divider, dp);

                TextView tvImport = new TextView(ctx);
                tvImport.setText("IMPORTER UN FICHIER JSON");
                tvImport.setTextSize(10f); tvImport.setTextColor(0xFF64748B);
                tvImport.setAllCaps(true); tvImport.setLetterSpacing(0.1f);
                container.addView(tvImport);

                TextView tvHint = new TextView(ctx);
                tvHint.setText("Exportez d'abord une bible depuis la régie principale,\npuis importez le fichier JSON sur cet appareil.");
                tvHint.setTextSize(10f); tvHint.setTextColor(0xFF64748B); tvHint.setPadding(0,6,0,8);
                container.addView(tvHint);
            });
        });
    }

    private void downloadBible(BibleSource src, ProgressBar pb, TextView tvStatus) {
        pb.setVisibility(View.VISIBLE); pb.setProgress(5);
        engine.download(src.code, new BibleEngine.ProgressCallback() {
            @Override public void onProgress(int pct, String msg) {
                pb.setProgress(pct); tvStatus.setText(msg);
            }
            @Override public void onSuccess(String code) {
                pb.setProgress(100); tvStatus.setText("✅ " + src.name + " installée !");
                ui.postDelayed(() -> { pb.setVisibility(View.GONE); tvStatus.setText(""); render(); notifyChanged(); }, 1500);
            }
            @Override public void onError(String err) {
                pb.setVisibility(View.GONE); tvStatus.setText("❌ " + err);
                Toast.makeText(ctx, "Erreur: " + err, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toggleActive(String code, boolean active, ProgressBar pb, TextView tvStatus) {
        engine.activateBible(code, active);
        ui.postDelayed(() -> { render(); notifyChanged(); }, 400);
    }

    private void setMain(String code) {
        engine.activateBible(code, true);
        // Store as main in prefs
        android.content.SharedPreferences prefs = ctx.getSharedPreferences("wsc_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("main_bible", code).apply();
        ui.postDelayed(() -> { render(); notifyChanged(); }, 300);
        Toast.makeText(ctx, "Bible principale: " + code, Toast.LENGTH_SHORT).show();
    }

    private void exportBible(String code) {
        Toast.makeText(ctx, "Export " + code + " en cours…", Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Build JSON array in thiagobodruk format
                com.wisesmartchurch.system.db.AppDatabase db = com.wisesmartchurch.system.db.AppDatabase.getInstance(ctx);
                List<com.wisesmartchurch.system.model.BibleVerse> allVerses = db.bibleDao().searchText(code, "");
                // group by book → chapter → verse
                org.json.JSONArray books = new org.json.JSONArray();
                // Find max book
                int maxBook = 0;
                for (com.wisesmartchurch.system.model.BibleVerse v : allVerses) if (v.bookIndex > maxBook) maxBook = v.bookIndex;
                for (int bi = 0; bi <= maxBook; bi++) {
                    org.json.JSONObject book = new org.json.JSONObject();
                    book.put("name", BibleEngine.getBookName(bi, "fr"));
                    book.put("abbrev", bi < BibleEngine.BOOKS_FR.length ? BibleEngine.BOOKS_FR[bi] : "?");
                    org.json.JSONArray chapters = new org.json.JSONArray();
                    final int biF = bi;
                    List<com.wisesmartchurch.system.model.BibleVerse> bkV = new ArrayList<>();
                    for (com.wisesmartchurch.system.model.BibleVerse v : allVerses) if (v.bookIndex == biF) bkV.add(v);
                    int maxCh = 0; for (com.wisesmartchurch.system.model.BibleVerse v : bkV) if (v.chapterIndex > maxCh) maxCh = v.chapterIndex;
                    for (int ci = 0; ci <= maxCh; ci++) {
                        final int ciF = ci; org.json.JSONArray chArr = new org.json.JSONArray();
                        List<com.wisesmartchurch.system.model.BibleVerse> chV = new ArrayList<>();
                        for (com.wisesmartchurch.system.model.BibleVerse v : bkV) if (v.chapterIndex == ciF) chV.add(v);
                        chV.sort(Comparator.comparingInt(v -> v.verseIndex));
                        for (com.wisesmartchurch.system.model.BibleVerse v : chV) chArr.put(v.text);
                        chapters.put(chArr);
                    }
                    book.put("chapters", chapters); books.put(book);
                }
                String json = books.toString();
                java.io.File dir = new java.io.File(ctx.getExternalFilesDir(null), "BibleExport");
                dir.mkdirs();
                java.io.File f = new java.io.File(dir, "bible_" + code + ".json");
                try (java.io.FileWriter fw = new java.io.FileWriter(f)) { fw.write(json); }
                ui.post(() -> Toast.makeText(ctx, "✅ Exporté: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(ctx, "❌ Export échoué: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void deleteBible(String code, ProgressBar pb, TextView tvStatus) {
        new android.app.AlertDialog.Builder(ctx)
            .setTitle("Supprimer la bible " + code + " ?")
            .setMessage("Tous les versets seront supprimés de l'appareil.")
            .setPositiveButton("Supprimer", (d, w) -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    com.wisesmartchurch.system.db.AppDatabase.getInstance(ctx).bibleDao().deleteVerses(code);
                    com.wisesmartchurch.system.db.AppDatabase.getInstance(ctx).bibleDao().deleteMeta(code);
                    ui.post(() -> { render(); notifyChanged(); Toast.makeText(ctx, "🗑 " + code + " supprimée", Toast.LENGTH_SHORT).show(); });
                });
            })
            .setNegativeButton("Annuler", null).show();
    }

    private void notifyChanged() {
        if (listener == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<BibleMeta> metas = engine.getAllMeta();
            List<String> active = new ArrayList<>();
            String main = ctx.getSharedPreferences("wsc_prefs", Context.MODE_PRIVATE).getString("main_bible", "");
            for (BibleMeta m : metas) if (m.isActive) active.add(m.code);
            if (main.isEmpty() && !active.isEmpty()) main = active.get(0);
            final String mainF = main;
            ui.post(() -> listener.onChanged(active, mainF));
        });
    }

    private Button makeFilterBtn(String label, boolean active) {
        Button b = new Button(ctx);
        b.setText(label); b.setTextSize(10f); b.setPadding(12, 4, 12, 4);
        b.setBackgroundResource(active ? R.drawable.bg_slot_active : R.drawable.bg_btn_icon);
        b.setTextColor(active ? 0xFFFFFFFF : 0xFF64748B);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 30);
        b.setLayoutParams(lp);
        return b;
    }

    private Button makeSmallBtn(String label, int color) {
        Button b = new Button(ctx);
        b.setText(label); b.setTextSize(9f); b.setTextColor(color);
        b.setBackgroundResource(R.drawable.bg_btn_icon);
        b.setPadding(8, 4, 8, 4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 32);
        b.setLayoutParams(lp);
        return b;
    }
}
