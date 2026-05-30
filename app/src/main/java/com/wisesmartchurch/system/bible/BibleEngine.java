package com.wisesmartchurch.system.bible;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.wisesmartchurch.system.db.AppDatabase;
import com.wisesmartchurch.system.db.BibleDao;
import com.wisesmartchurch.system.model.BibleVerse;
import com.wisesmartchurch.system.model.BibleMeta;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Télécharge, stocke et recherche dans les bibles */
public class BibleEngine {

    private static final String TAG = "BibleEngine";
    private final Context       ctx;
    private final BibleDao      dao;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler        ui   = new Handler(Looper.getMainLooper());

    public interface ProgressCallback {
        void onProgress(int pct, String msg);
        void onSuccess(String bibleCode);
        void onError(String error);
    }

    public interface SearchCallback {
        void onResults(List<BibleVerse> verses);
    }

    public static final BibleSource[] SOURCES = {
        new BibleSource("LSG",    "Louis Segond 1910",           "fr", "https://cdn.jsdelivr.net/gh/thiagobodruk/bible@master/json/fr_apee.json"),
        new BibleSource("NEG",    "Nouvelle Éd. de Genève",      "fr", "https://cdn.jsdelivr.net/gh/thiagobodruk/bible@master/json/fr_neg.json"),
        new BibleSource("RVR60",  "Reina Valera 1960",           "es", "https://cdn.jsdelivr.net/gh/thiagobodruk/bible@master/json/es_rvr.json"),
        new BibleSource("NVI_ES", "Nueva Versión Internacional", "es", "https://cdn.jsdelivr.net/gh/thiagobodruk/bible@master/json/es_nvi.json"),
        new BibleSource("KJV",    "King James Version",          "en", "https://cdn.jsdelivr.net/gh/thiagobodruk/bible@master/json/en_kjv.json"),
        new BibleSource("NIV",    "New Intl Version",            "en", "https://cdn.jsdelivr.net/gh/thiagobodruk/bible@master/json/en_niv.json"),
        new BibleSource("ARA_PT", "Almeida Rev. Atualizada",     "pt", "https://cdn.jsdelivr.net/gh/thiagobodruk/bible@master/json/pt_aa.json"),
    };

    // Book names FR / ES / EN index 0-65
    public static final String[] BOOKS_FR = {
        "Genèse","Exode","Lévitique","Nombres","Deutéronome","Josué","Juges","Ruth",
        "1 Samuel","2 Samuel","1 Rois","2 Rois","1 Chroniques","2 Chroniques","Esdras","Néhémie",
        "Esther","Job","Psaumes","Proverbes","Ecclésiaste","Cantique","Ésaïe","Jérémie",
        "Lamentations","Ézéchiel","Daniel","Osée","Joël","Amos","Abdias","Jonas","Michée",
        "Nahum","Habacuc","Sophonie","Aggée","Zacharie","Malachie",
        "Matthieu","Marc","Luc","Jean","Actes","Romains","1 Corinthiens","2 Corinthiens",
        "Galates","Éphésiens","Philippiens","Colossiens","1 Thessaloniciens","2 Thessaloniciens",
        "1 Timothée","2 Timothée","Tite","Philémon","Hébreux","Jacques",
        "1 Pierre","2 Pierre","1 Jean","2 Jean","3 Jean","Jude","Apocalypse"
    };
    public static final String[] BOOKS_ES = {
        "Génesis","Éxodo","Levítico","Números","Deuteronomio","Josué","Jueces","Rut",
        "1 Samuel","2 Samuel","1 Reyes","2 Reyes","1 Crónicas","2 Crónicas","Esdras","Nehemías",
        "Ester","Job","Salmos","Proverbios","Eclesiastés","Cantares","Isaías","Jeremías",
        "Lamentaciones","Ezequiel","Daniel","Oseas","Joel","Amós","Abdías","Jonás","Miqueas",
        "Nahúm","Habacuc","Sofonías","Hageo","Zacarías","Malaquías",
        "Mateo","Marcos","Lucas","Juan","Hechos","Romanos","1 Corintios","2 Corintios",
        "Gálatas","Efesios","Filipenses","Colosenses","1 Tesalonicenses","2 Tesalonicenses",
        "1 Timoteo","2 Timoteo","Tito","Filemón","Hebreos","Santiago",
        "1 Pedro","2 Pedro","1 Juan","2 Juan","3 Juan","Judas","Apocalipsis"
    };
    public static final String[] BOOKS_EN = {
        "Genesis","Exodus","Leviticus","Numbers","Deuteronomy","Joshua","Judges","Ruth",
        "1 Samuel","2 Samuel","1 Kings","2 Kings","1 Chronicles","2 Chronicles","Ezra","Nehemiah",
        "Esther","Job","Psalms","Proverbs","Ecclesiastes","Song of Solomon","Isaiah","Jeremiah",
        "Lamentations","Ezekiel","Daniel","Hosea","Joel","Amos","Obadiah","Jonah","Micah",
        "Nahum","Habakkuk","Zephaniah","Haggai","Zechariah","Malachi",
        "Matthew","Mark","Luke","John","Acts","Romans","1 Corinthians","2 Corinthians",
        "Galatians","Ephesians","Philippians","Colossians","1 Thessalonians","2 Thessalonians",
        "1 Timothy","2 Timothy","Titus","Philemon","Hebrews","James",
        "1 Peter","2 Peter","1 John","2 John","3 John","Jude","Revelation"
    };
    public static final int NT_START = 39;

    public BibleEngine(Context ctx) {
        this.ctx = ctx;
        this.dao = AppDatabase.getInstance(ctx).bibleDao();
    }

    /** Télécharge et importe une bible */
    public void download(String code, ProgressCallback cb) {
        exec.execute(() -> {
            BibleSource src = findSource(code);
            if (src == null) { ui.post(() -> cb.onError("Source introuvable: " + code)); return; }
            ui.post(() -> cb.onProgress(5, "Connexion…"));
            try {
                String json = fetchJson(src.url, cb);
                ui.post(() -> cb.onProgress(50, "Import en base…"));
                importJson(src, json, cb);
                ui.post(() -> { cb.onProgress(100, "Terminé !"); cb.onSuccess(code); });
            } catch (Exception e) {
                Log.e(TAG, "download error", e);
                ui.post(() -> cb.onError(e.getMessage()));
            }
        });
    }

    private String fetchJson(String url, ProgressCallback cb) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000); conn.setReadTimeout(60000);
        int total = conn.getContentLength();
        StringBuilder sb = new StringBuilder();
        try (InputStream is = conn.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            char[] buf = new char[8192]; int read; long got = 0;
            while ((read = br.read(buf)) != -1) {
                sb.append(buf, 0, read); got += read;
                if (total > 0) {
                    final int pct = (int)(10 + (got * 35 / total));
                    ui.post(() -> cb.onProgress(pct, "Téléchargement…"));
                }
            }
        }
        return sb.toString();
    }

    private void importJson(BibleSource src, String raw, ProgressCallback cb) throws Exception {
        JSONArray books = new JSONArray(raw);
        // Clean existing
        dao.deleteVerses(src.code); dao.deleteMeta(src.code);
        BibleMeta meta = new BibleMeta();
        meta.code = src.code; meta.name = src.name; meta.lang = src.lang;
        meta.isActive = true; meta.isMain = false; meta.downloadedAt = System.currentTimeMillis();
        dao.insertMeta(meta);
        List<BibleVerse> batch = new ArrayList<>();
        int totalVerses = 0;
        for (int bi = 0; bi < books.length(); bi++) {
            JSONObject book = books.getJSONObject(bi);
            JSONArray chapters = book.getJSONArray("chapters");
            for (int ci = 0; ci < chapters.length(); ci++) {
                JSONArray verses = chapters.getJSONArray(ci);
                for (int vi = 0; vi < verses.length(); vi++) {
                    BibleVerse v = new BibleVerse();
                    v.bibleCode = src.code; v.bookIndex = bi; v.chapterIndex = ci; v.verseIndex = vi;
                    v.text = verses.getString(vi);
                    batch.add(v); totalVerses++;
                    if (batch.size() >= 500) {
                        dao.insertVerses(new ArrayList<>(batch)); batch.clear();
                        final int pct = 50 + (int)(bi * 45f / books.length());
                        final String msg = "Import " + getBookName(bi, src.lang) + "…";
                        ui.post(() -> cb.onProgress(pct, msg));
                    }
                }
            }
        }
        if (!batch.isEmpty()) dao.insertVerses(batch);
        meta.verseCount = totalVerses; dao.insertMeta(meta);
        Log.i(TAG, "✅ " + src.code + " importé: " + totalVerses + " versets");
    }

    /** Recherche synchrone (appeler depuis un thread) */
    public List<BibleVerse> searchSync(String bibleCode, String keyword) {
        return dao.searchText(bibleCode, keyword);
    }

    /** Recherche asynchrone */
    public void search(String bibleCode, String keyword, SearchCallback cb) {
        exec.execute(() -> {
            List<BibleVerse> results = dao.searchText(bibleCode, keyword.toLowerCase());
            ui.post(() -> cb.onResults(results));
        });
    }

    /** Recherche par référence */
    public void getChapter(String code, int book, int ch, SearchCallback cb) {
        exec.execute(() -> {
            List<BibleVerse> v = dao.getChapter(code, book, ch);
            ui.post(() -> cb.onResults(v));
        });
    }

    public List<BibleMeta> getAllMeta() { return dao.getAllMeta(); }

    public int getChapterCount(String code, int book) {
        return dao.bookChapterCount(code, book);
    }

    public void activateBible(String code, boolean active) {
        exec.execute(() -> {
            List<BibleMeta> metas = dao.getAllMeta();
            for (BibleMeta m : metas) {
                if (m.code.equals(code)) { m.isActive = active; dao.updateMeta(m); }
            }
        });
    }

    public static String getBookName(int idx, String lang) {
        if (idx < 0 || idx >= 66) return "?";
        switch (lang) {
            case "es": return BOOKS_ES[idx];
            case "en": return BOOKS_EN[idx];
            default:   return BOOKS_FR[idx];
        }
    }

    /** Trouver l'index d'un livre à partir d'un nom partiel (tri-langue) */
    public static int findBookIndex(String input) {
        String n = normalize(input);
        for (String[] names : new String[][]{BOOKS_FR, BOOKS_ES, BOOKS_EN}) {
            for (int i = 0; i < names.length; i++) {
                if (normalize(names[i]).startsWith(n)) return i;
            }
        }
        // Aliases
        Map<String, Integer> aliases = new HashMap<>();
        aliases.put("jn", 39); aliases.put("john", 39); aliases.put("jean", 39); aliases.put("juan", 39);
        aliases.put("gn", 0);  aliases.put("genesis", 0); aliases.put("genese", 0);
        aliases.put("ps", 18); aliases.put("psalms", 18); aliases.put("salmos", 18);
        aliases.put("ap", 65); aliases.put("rev", 65); aliases.put("revelation", 65); aliases.put("apocalipsis", 65);
        aliases.put("mt", 39); aliases.put("matthew", 39); aliases.put("mateo", 39); aliases.put("matthieu", 39);
        aliases.put("mc", 40); aliases.put("mark", 40); aliases.put("marcos", 40);
        aliases.put("lc", 41); aliases.put("luke", 41); aliases.put("lucas", 41);
        aliases.put("ac", 43); aliases.put("acts", 43); aliases.put("hechos", 43);
        Integer idx = aliases.get(n.replaceAll("\\s",""));
        return idx != null ? idx : -1;
    }

    private static String normalize(String s) {
        return s.toLowerCase()
                .replaceAll("[àáâãäå]","a").replaceAll("[éèêë]","e")
                .replaceAll("[íìîï]","i").replaceAll("[óòôõö]","o")
                .replaceAll("[úùûü]","u").replaceAll("[ñ]","n").trim();
    }

    private BibleSource findSource(String code) {
        for (BibleSource s : SOURCES) if (s.code.equals(code)) return s;
        return null;
    }

    public static class BibleSource {
        public final String code, name, lang, url;
        public BibleSource(String c, String n, String l, String u) { code=c; name=n; lang=l; url=u; }
    }
}
