package com.wisesmartchurch.system.ui.songs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.db.AppDatabase;
import com.wisesmartchurch.system.model.Song;
import java.util.*;
import java.util.concurrent.Executors;

public class SongsPanelController {

    public interface OnStropheSelectedListener {
        void onStropheSelected(String text, String label, String bgMode, String bgUrl);
    }

    private final Context      ctx;
    private final View         root;
    private final AppDatabase  db;
    private OnStropheSelectedListener listener;

    private List<Song>         songs     = new ArrayList<>();
    private Song               activeSong = null;
    private int                activeStropheIdx = -1;
    private RecyclerView       rvSongs, rvStrophes;
    private SongAdapter        songAdapter;
    private StropheAdapter     stropheAdapter;
    private EditText           etSongSearch;

    public SongsPanelController(Context ctx, View root, AppDatabase db) {
        this.ctx = ctx; this.root = root; this.db = db;
        initViews();
        loadSongs();
    }

    private void initViews() {
        if (root == null) return;
        rvSongs    = root.findViewById(R.id.rv_songs);
        rvStrophes = root.findViewById(R.id.rv_strophes);
        etSongSearch = root.findViewById(R.id.et_song_search);

        if (rvSongs != null) {
            rvSongs.setLayoutManager(new LinearLayoutManager(ctx));
            songAdapter = new SongAdapter();
            rvSongs.setAdapter(songAdapter);
        }
        if (rvStrophes != null) {
            rvStrophes.setLayoutManager(new LinearLayoutManager(ctx));
            stropheAdapter = new StropheAdapter();
            rvStrophes.setAdapter(stropheAdapter);
        }

        View btnAdd    = root.findViewById(R.id.btn_add_song);
        View btnImport = root.findViewById(R.id.btn_import_lyrics);
        if (btnAdd    != null) btnAdd.setOnClickListener(v -> showAddSongDialog());
        if (btnImport != null) btnImport.setOnClickListener(v -> showImportDialog());

        if (etSongSearch != null) {
            etSongSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence s,int a,int b,int c){filterSongs(s.toString());}
                @Override public void afterTextChanged(android.text.Editable s){}
            });
        }
    }

    private void loadSongs() {
        Executors.newSingleThreadExecutor().execute(() -> {
            songs = db.songDao().getAll();
            if (rvSongs != null) rvSongs.post(() -> songAdapter.setData(songs));
        });
    }

    private void filterSongs(String q) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Song> filtered = q.trim().isEmpty() ? db.songDao().getAll() : db.songDao().search(q);
            if (rvSongs != null) rvSongs.post(() -> songAdapter.setData(filtered));
        });
    }

    private void openSong(Song song) {
        activeSong = song;
        List<Song.Strophe> strophes = song.parseStrophes();
        if (stropheAdapter != null) stropheAdapter.setData(strophes, song);
        if (rvStrophes != null) rvStrophes.setVisibility(View.VISIBLE);
    }

    private void selectStrophe(Song.Strophe s, Song song) {
        String bgMode = song.bgMode != null ? song.bgMode : "black";
        String bgUrl  = song.bgUrl  != null ? song.bgUrl  : "";
        if (listener != null) listener.onStropheSelected(s.text, s.label, bgMode, bgUrl);
    }

    private void showAddSongDialog() {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_song, null);
        EditText etTitle  = v.findViewById(R.id.et_song_title);
        EditText etAuthor = v.findViewById(R.id.et_song_author);
        EditText etLyrics = v.findViewById(R.id.et_song_lyrics);
        EditText etBgUrl  = v.findViewById(R.id.et_song_bg);
        Spinner  spLang   = v.findViewById(R.id.sp_song_lang);

        new AlertDialog.Builder(ctx).setTitle("🎵 Ajouter un chant").setView(v)
            .setPositiveButton("Enregistrer", (d, w) -> {
                String title = etTitle != null ? etTitle.getText().toString().trim() : "";
                if (title.isEmpty()) { Toast.makeText(ctx, "Titre requis", Toast.LENGTH_SHORT).show(); return; }
                Song song = new Song();
                song.title  = title;
                song.author = etAuthor != null ? etAuthor.getText().toString().trim() : "";
                song.lyrics = etLyrics != null ? etLyrics.getText().toString().trim() : "";
                song.bgUrl  = etBgUrl  != null ? etBgUrl.getText().toString().trim()  : "";
                song.bgMode = song.bgUrl.isEmpty() ? "black" : "image";
                song.language = spLang != null ? (String) spLang.getSelectedItem() : "fr";
                song.createdAt = System.currentTimeMillis();
                song.updatedAt = song.createdAt;
                Executors.newSingleThreadExecutor().execute(() -> { db.songDao().insert(song); loadSongs(); });
            })
            .setNegativeButton("Annuler", null).show();
    }

    private void showImportDialog() {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_import_lyrics, null);
        EditText etTitle  = v.findViewById(R.id.et_import_title);
        EditText etLyrics = v.findViewById(R.id.et_import_lyrics);
        new AlertDialog.Builder(ctx).setTitle("📋 Importer paroles").setView(v)
            .setPositiveButton("Importer", (d, w) -> {
                String title = etTitle != null ? etTitle.getText().toString().trim() : "Chant";
                String raw   = etLyrics != null ? etLyrics.getText().toString().trim() : "";
                if (raw.isEmpty()) return;
                Song song = new Song();
                song.title = title; song.lyrics = raw; song.bgMode = "black";
                song.language = "fr"; song.createdAt = System.currentTimeMillis();
                song.updatedAt = song.createdAt;
                Executors.newSingleThreadExecutor().execute(() -> { db.songDao().insert(song); loadSongs(); });
                Toast.makeText(ctx, "✅ Importé: " + song.parseStrophes().size() + " strophes", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Annuler", null).show();
    }

    public void setOnStropheSelected(OnStropheSelectedListener l) { this.listener = l; }

    // ── SongAdapter ──────────────────────────────────
    class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {
        private List<Song> data = new ArrayList<>();
        void setData(List<Song> d) { data = d; notifyDataSetChanged(); }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_song, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            Song s = data.get(pos);
            h.tvTitle.setText(s.title);
            h.tvMeta.setText((s.author != null ? s.author : "") + " · " + s.parseStrophes().size() + " strophes");
            h.itemView.setOnClickListener(x -> openSong(s));
            h.btnAdd.setOnClickListener(x -> addSongToPlaylist(s));
            h.btnDelete.setOnClickListener(x -> deleteSong(s));
        }
        @Override public int getItemCount() { return data.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvMeta; View btnAdd, btnDelete;
            VH(View v) { super(v);
                tvTitle   = v.findViewById(R.id.tv_song_title);
                tvMeta    = v.findViewById(R.id.tv_song_meta);
                btnAdd    = v.findViewById(R.id.btn_song_add_pl);
                btnDelete = v.findViewById(R.id.btn_song_delete);
            }
        }
    }

    // ── StropheAdapter ────────────────────────────────
    class StropheAdapter extends RecyclerView.Adapter<StropheAdapter.VH> {
        private List<Song.Strophe> data = new ArrayList<>();
        private Song song;
        void setData(List<Song.Strophe> d, Song s) { data = d; song = s; notifyDataSetChanged(); }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_strophe, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            Song.Strophe s = data.get(pos);
            h.tvLabel.setText(s.label);
            h.tvText.setText(s.text);
            h.itemView.setOnClickListener(x -> selectStrophe(s, song));
        }
        @Override public int getItemCount() { return data.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvLabel, tvText;
            VH(View v) { super(v); tvLabel = v.findViewById(R.id.tv_strophe_label); tvText = v.findViewById(R.id.tv_strophe_text); }
        }
    }

    private void addSongToPlaylist(Song s) { /* delegate to MainActivity */ }
    private void deleteSong(Song s) {
        new AlertDialog.Builder(ctx).setTitle("Supprimer " + s.title + " ?")
            .setPositiveButton("Supprimer", (d, w) ->
                Executors.newSingleThreadExecutor().execute(() -> { db.songDao().delete(s); loadSongs(); }))
            .setNegativeButton("Annuler", null).show();
    }
}
