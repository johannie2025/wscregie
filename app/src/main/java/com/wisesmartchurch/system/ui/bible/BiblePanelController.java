package com.wisesmartchurch.system.ui.bible;

import android.content.Context;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.bible.BibleEngine;
import com.wisesmartchurch.system.model.*;
import java.util.*;

/** Contrôle le panneau Bible: sélecteur AT/NT, chapitres, versets, recherche */
public class BiblePanelController {

    public interface OnVerseSelectedListener {
        void onVerseSelected(String text, String ref, String bgMode, String bgUrl);
    }

    private final Context     ctx;
    private final View        root;
    private final BibleEngine engine;
    private OnVerseSelectedListener listener;

    // Bible state
    private String         bibleCode = "LSG";
    private List<String>   activeBibles = new ArrayList<>();
    private int            currentBook  = 0;
    private int            currentChapter = 0;
    private int            currentVerse   = -1;
    private List<BibleVerse> currentVerses = new ArrayList<>();
    private String         displayLang = "fr";

    // Views
    private TextView        tvBookName, tvChapterNum;
    private LinearLayout    llBooks;
    private GridView        gvChapters;
    private RecyclerView    rvVerses;
    private EditText        etSearch;
    private ListView        lvSearchResults;
    private Spinner         spBibleVersion;
    private TabHost         tabAtNt;
    private TextView        tvBgMode;
    private String          overrideBgMode = null, overrideBgUrl = null;

    private VerseAdapter    verseAdapter;

    public BiblePanelController(Context ctx, View root, BibleEngine engine) {
        this.ctx    = ctx;
        this.root   = root;
        this.engine = engine;
        initViews();
        loadBibleMeta();
    }

    private void initViews() {
        if (root == null) return;

        // AT/NT toggle
        View btnAT = root.findViewById(R.id.btn_at);
        View btnNT = root.findViewById(R.id.btn_nt);
        if (btnAT != null) btnAT.setOnClickListener(v -> showTestament(false));
        if (btnNT != null) btnNT.setOnClickListener(v -> showTestament(true));

        llBooks = root.findViewById(R.id.ll_books);
        gvChapters = root.findViewById(R.id.gv_chapters);
        rvVerses   = root.findViewById(R.id.rv_verses);
        etSearch   = root.findViewById(R.id.et_bible_search);
        lvSearchResults = root.findViewById(R.id.lv_search_results);
        tvBookName  = root.findViewById(R.id.tv_book_name);
        tvChapterNum = root.findViewById(R.id.tv_chapter_num);
        spBibleVersion = root.findViewById(R.id.sp_bible_version);

        if (rvVerses != null) {
            rvVerses.setLayoutManager(new LinearLayoutManager(ctx));
            verseAdapter = new VerseAdapter();
            rvVerses.setAdapter(verseAdapter);
        }

        // Search
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence s,int a,int b,int c){performSearch(s.toString());}
                @Override public void afterTextChanged(Editable s){}
            });
        }

        showTestament(false); // Default AT
    }

    private void loadBibleMeta() {
        new Thread(() -> {
            List<BibleMeta> metas = engine.getAllMeta();
            List<String> names = new ArrayList<>();
            activeBibles.clear();
            for (BibleMeta m : metas) {
                if (m.isActive) { activeBibles.add(m.code); names.add(m.name + " (" + m.lang.toUpperCase() + ")"); }
            }
            if (!activeBibles.isEmpty() && (bibleCode == null || !activeBibles.contains(bibleCode)))
                bibleCode = activeBibles.get(0);
            // Update spinner on UI thread
            if (spBibleVersion != null) {
                spBibleVersion.post(() -> {
                    ArrayAdapter<String> a = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, names);
                    a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spBibleVersion.setAdapter(a);
                    spBibleVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                            if (pos < activeBibles.size()) { bibleCode = activeBibles.get(pos); loadChapter(); }
                        }
                        @Override public void onNothingSelected(AdapterView<?> p) {}
                    });
                });
            }
            if (spBibleVersion != null) spBibleVersion.post(() -> showTestament(false));
        }).start();
    }

    private void showTestament(boolean nt) {
        if (llBooks == null) return;
        llBooks.removeAllViews();
        int start = nt ? BibleEngine.NT_START : 0;
        int end   = nt ? 65 : BibleEngine.NT_START - 1;
        String[] names = BibleEngine.BOOKS_FR; // TODO: use lang
        for (int i = start; i <= end; i++) {
            final int bookIdx = i;
            Button b = new Button(ctx);
            b.setText(names[i]);
            b.setTextSize(11f);
            b.setOnClickListener(v -> selectBook(bookIdx));
            b.setBackgroundColor(currentBook == i ? 0xFF1e3a5f : android.graphics.Color.TRANSPARENT);
            b.setTextColor(currentBook == i ? 0xFFf0c040 : 0xFFe2e8f0);
            b.setPadding(8, 6, 8, 6);
            llBooks.addView(b);
        }
    }

    private void selectBook(int idx) {
        currentBook = idx; currentChapter = 0; currentVerse = -1;
        if (tvBookName != null) tvBookName.setText(BibleEngine.getBookName(idx, displayLang));
        loadChapterGrid();
    }

    private void loadChapterGrid() {
        if (gvChapters == null) return;
        new Thread(() -> {
            int cnt = engine.getChapterCount(bibleCode, currentBook);
            gvChapters.post(() -> {
                List<String> nums = new ArrayList<>();
                for (int i = 0; i < cnt; i++) nums.add(String.valueOf(i+1));
                ArrayAdapter<String> a = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, nums);
                gvChapters.setAdapter(a);
                gvChapters.setOnItemClickListener((p, v, pos, id) -> { currentChapter = pos; loadChapter(); });
            });
        }).start();
    }

    private void loadChapter() {
        if (bibleCode == null) return;
        engine.getChapter(bibleCode, currentBook, currentChapter, verses -> {
            currentVerses = verses;
            if (tvChapterNum != null) tvChapterNum.setText(
                BibleEngine.getBookName(currentBook, displayLang) + " " + (currentChapter+1));
            if (verseAdapter != null) { verseAdapter.setData(verses); }
        });
    }

    private void selectVerse(int idx) {
        if (idx < 0 || idx >= currentVerses.size()) return;
        currentVerse = idx;
        BibleVerse v = currentVerses.get(idx);
        String ref = BibleEngine.getBookName(v.bookIndex, displayLang)
                   + " " + (v.chapterIndex+1) + ":" + (v.verseIndex+1)
                   + " [" + bibleCode + "]";
        if (listener != null) listener.onVerseSelected(v.text, ref, overrideBgMode, overrideBgUrl);
        if (verseAdapter != null) { verseAdapter.setSelected(idx); }
    }

    private void performSearch(String q) {
        if (q.trim().isEmpty()) { hideSearch(); return; }
        // Try reference
        String[] parts = q.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                int bookIdx = BibleEngine.findBookIndex(parts[0]);
                if (bookIdx >= 0) {
                    String[] ref = parts[1].split(":");
                    int ch = Integer.parseInt(ref[0]) - 1;
                    if (ref.length > 1) {
                        int vIdx = Integer.parseInt(ref[1]) - 1;
                        engine.getChapter(bibleCode, bookIdx, ch, verses -> {
                            for (BibleVerse v : verses) {
                                if (v.verseIndex == vIdx) {
                                    String refStr = BibleEngine.getBookName(bookIdx, displayLang) + " " + (ch+1) + ":" + (vIdx+1);
                                    if (listener != null) listener.onVerseSelected(v.text, refStr, null, null);
                                    break;
                                }
                            }
                        });
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }
        // Full-text
        engine.search(bibleCode, q, results -> showSearchResults(results, q));
    }

    private void showSearchResults(List<BibleVerse> results, String query) {
        if (lvSearchResults == null) return;
        lvSearchResults.setVisibility(results.isEmpty() ? View.GONE : View.VISIBLE);
        List<String> display = new ArrayList<>();
        for (BibleVerse v : results) {
            display.add(BibleEngine.getBookName(v.bookIndex, displayLang)
                + " " + (v.chapterIndex+1) + ":" + (v.verseIndex+1) + "\n" + v.text);
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_2, android.R.id.text1, display);
        lvSearchResults.setAdapter(a);
        lvSearchResults.setOnItemClickListener((p, v, pos, id) -> {
            BibleVerse bv = results.get(pos);
            String ref = BibleEngine.getBookName(bv.bookIndex, displayLang)
                + " " + (bv.chapterIndex+1) + ":" + (bv.verseIndex+1);
            if (listener != null) listener.onVerseSelected(bv.text, ref, null, null);
            hideSearch();
        });
    }
    private void hideSearch() { if (lvSearchResults != null) lvSearchResults.setVisibility(View.GONE); }

    public void navigatePrev() {
        if (currentVerse > 0) selectVerse(currentVerse - 1);
        else if (currentChapter > 0) { currentChapter--; loadChapter(); }
    }
    public void navigateNext() {
        if (currentVerse < currentVerses.size() - 1) selectVerse(currentVerse + 1);
        else { currentChapter++; loadChapter(); selectVerse(0); }
    }

    public void setBibleCode(String code) { this.bibleCode = code; }
    public void setOnVerseSelected(OnVerseSelectedListener l) { this.listener = l; }

    // ── VerseAdapter ──────────────────────────────────
    class VerseAdapter extends RecyclerView.Adapter<VerseAdapter.VH> {
        private List<BibleVerse> data = new ArrayList<>();
        private int selectedIdx = -1;

        void setData(List<BibleVerse> d) {
            data = d; selectedIdx = -1; notifyDataSetChanged();
        }
        void setSelected(int idx) { selectedIdx = idx; notifyDataSetChanged(); }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_verse, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            BibleVerse v = data.get(pos);
            h.tvNum.setText(String.valueOf(v.verseIndex + 1));
            h.tvText.setText(v.text);
            boolean sel = pos == selectedIdx;
            h.itemView.setBackgroundColor(sel ? 0x22f0c040 : android.graphics.Color.TRANSPARENT);
            h.itemView.setOnClickListener(x -> selectVerse(pos));
            h.itemView.setOnLongClickListener(x -> { showVerseBgDialog(pos, v); return true; });
        }
        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvNum, tvText;
            VH(View v) { super(v); tvNum = v.findViewById(R.id.tv_verse_num); tvText = v.findViewById(R.id.tv_verse_text); }
        }
    }

    private void showVerseBgDialog(int pos, BibleVerse v) {
        // Show dialog to set per-verse background
        new android.app.AlertDialog.Builder(ctx)
            .setTitle("🖼 Fond pour " + BibleEngine.getBookName(v.bookIndex, displayLang)
                + " " + (v.chapterIndex+1) + ":" + (v.verseIndex+1))
            .setItems(new String[]{"⬛ Noir", "🌅 Dégradé", "Saisir URL image", "Annuler"},
                (d, which) -> {
                    switch (which) {
                        case 0: overrideBgMode = "black";    overrideBgUrl = ""; break;
                        case 1: overrideBgMode = "gradient"; overrideBgUrl = ""; break;
                        case 2: showBgUrlDialog(pos, v); return;
                        case 3: return;
                    }
                    selectVerse(pos);
                }).show();
    }

    private void showBgUrlDialog(int pos, BibleVerse v) {
        EditText et = new EditText(ctx);
        et.setHint("https://…");
        et.setText(overrideBgUrl != null ? overrideBgUrl : "");
        new android.app.AlertDialog.Builder(ctx)
            .setTitle("URL de l'image de fond").setView(et)
            .setPositiveButton("Appliquer", (d, w) -> {
                overrideBgMode = "image"; overrideBgUrl = et.getText().toString().trim();
                selectVerse(pos);
            })
            .setNegativeButton("Annuler", null).show();
    }
}
