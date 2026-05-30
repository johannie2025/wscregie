package com.wisesmartchurch.system.ui.playlist;

import android.app.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.db.AppDatabase;
import com.wisesmartchurch.system.model.*;
import java.util.*;
import java.util.concurrent.*;

public class PlaylistPanelController {

    public interface OnItemSelectedListener { void onItemSelected(PlaylistItem item); }
    public interface OnProjectRequestedListener { void onProjectRequested(PlaylistItem item); }

    private final Context     ctx;
    private final View        root;
    private final AppDatabase db;
    private OnItemSelectedListener   selectListener;
    private OnProjectRequestedListener projectListener;

    private List<PlaylistItem> items = new ArrayList<>();
    private int activeIdx = -1;
    private Playlist currentPlaylist;
    private RecyclerView rvPlaylist;
    private PlaylistAdapter adapter;
    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private static final long AUTO_SAVE_INTERVAL = 3 * 60 * 1000; // 3 min

    public PlaylistPanelController(Context ctx, View root, AppDatabase db) {
        this.ctx = ctx; this.root = root; this.db = db;
        initViews();
        startAutoSave();
    }

    private void initViews() {
        if (root == null) return;
        rvPlaylist = root.findViewById(R.id.rv_playlist);
        if (rvPlaylist != null) {
            rvPlaylist.setLayoutManager(new LinearLayoutManager(ctx));
            adapter = new PlaylistAdapter();
            rvPlaylist.setAdapter(adapter);
            // Drag & drop
            ItemTouchHelper ith = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.RIGHT) {
                @Override public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder a, RecyclerView.ViewHolder b) {
                    int from = a.getAdapterPosition(), to = b.getAdapterPosition();
                    Collections.swap(items, from, to);
                    adapter.notifyItemMoved(from, to);
                    return true;
                }
                @Override public void onSwiped(RecyclerView.ViewHolder vh, int dir) {
                    int pos = vh.getAdapterPosition();
                    items.remove(pos); adapter.notifyItemRemoved(pos);
                }
            });
            ith.attachToRecyclerView(rvPlaylist);
        }

        View btnSave   = root.findViewById(R.id.btn_pl_save);
        View btnLoad   = root.findViewById(R.id.btn_pl_load);
        View btnClear  = root.findViewById(R.id.btn_pl_clear);
        View btnNew    = root.findViewById(R.id.btn_pl_new);
        if (btnSave  != null) btnSave.setOnClickListener(v -> showSaveDialog());
        if (btnLoad  != null) btnLoad.setOnClickListener(v -> showLoadDialog());
        if (btnClear != null) btnClear.setOnClickListener(v -> confirmClear());
        if (btnNew   != null) btnNew.setOnClickListener(v -> newPlaylist());
    }

    public void addItem(PlaylistItem item) {
        item.position = items.size();
        items.add(item);
        if (adapter != null) adapter.notifyItemInserted(items.size() - 1);
        scheduleAutoSave();
    }

    public void addItems(List<PlaylistItem> newItems) {
        for (PlaylistItem i : newItems) addItem(i);
    }

    private void selectItem(int idx) {
        activeIdx = idx;
        for (int i = 0; i < items.size(); i++) items.get(i).isActive = (i == idx);
        if (adapter != null) adapter.notifyDataSetChanged();
        if (selectListener != null && idx < items.size()) selectListener.onItemSelected(items.get(idx));
    }

    public void navigatePrev() { if (activeIdx > 0) selectItem(activeIdx - 1); }
    public void navigateNext() { if (activeIdx < items.size() - 1) selectItem(activeIdx + 1); }

    // ── Auto-save ──────────────────────────────────
    private void startAutoSave() {
        autoSaveHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!items.isEmpty() && currentPlaylist != null) saveCurrentPlaylist();
                autoSaveHandler.postDelayed(this, AUTO_SAVE_INTERVAL);
            }
        }, AUTO_SAVE_INTERVAL);
    }

    private void scheduleAutoSave() {
        autoSaveHandler.removeCallbacksAndMessages(null);
        autoSaveHandler.postDelayed(() -> {
            if (!items.isEmpty()) autoSaveAnonymous();
        }, 30000); // 30s après dernière modification
    }

    private void autoSaveAnonymous() {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (currentPlaylist == null) {
                currentPlaylist = new Playlist();
                currentPlaylist.uuid = UUID.randomUUID().toString();
                currentPlaylist.name = "Auto-sauvegarde " + new java.util.Date().toString().substring(0, 16);
                currentPlaylist.serviceType = "culte";
                currentPlaylist.autoSave = true;
                currentPlaylist.createdAt = System.currentTimeMillis();
            }
            currentPlaylist.updatedAt = System.currentTimeMillis();
            if (currentPlaylist.id == 0) db.playlistDao().insertPlaylist(currentPlaylist);
            else db.playlistDao().updatePlaylist(currentPlaylist);
            saveItemsToDb(currentPlaylist.uuid);
        });
    }

    private void saveCurrentPlaylist() {
        if (currentPlaylist == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            currentPlaylist.updatedAt = System.currentTimeMillis();
            db.playlistDao().updatePlaylist(currentPlaylist);
            saveItemsToDb(currentPlaylist.uuid);
        });
    }

    private void saveItemsToDb(String uuid) {
        db.playlistDao().clearItems(uuid);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).playlistId = uuid;
            items.get(i).position   = i;
        }
        db.playlistDao().insertItems(items);
    }

    private void showSaveDialog() {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_save_playlist, null);
        EditText etName = v.findViewById(R.id.et_pl_name);
        Spinner  spType = v.findViewById(R.id.sp_pl_type);
        if (etName != null && currentPlaylist != null) etName.setText(currentPlaylist.name);
        new AlertDialog.Builder(ctx).setTitle("💾 Sauvegarder la playlist").setView(v)
            .setPositiveButton("Sauvegarder", (d, w) -> {
                String name = etName != null ? etName.getText().toString().trim() : "Culte";
                String type = spType != null ? spType.getSelectedItem().toString() : "culte";
                Executors.newSingleThreadExecutor().execute(() -> {
                    if (currentPlaylist == null) {
                        currentPlaylist = new Playlist();
                        currentPlaylist.uuid = UUID.randomUUID().toString();
                        currentPlaylist.createdAt = System.currentTimeMillis();
                    }
                    currentPlaylist.name = name; currentPlaylist.serviceType = type;
                    currentPlaylist.updatedAt = System.currentTimeMillis();
                    if (currentPlaylist.id == 0) db.playlistDao().insertPlaylist(currentPlaylist);
                    else db.playlistDao().updatePlaylist(currentPlaylist);
                    saveItemsToDb(currentPlaylist.uuid);
                    rvPlaylist.post(() -> Toast.makeText(ctx, "💾 Playlist sauvegardée", Toast.LENGTH_SHORT).show());
                });
            })
            .setNegativeButton("Annuler", null).show();
    }

    private void showLoadDialog() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Playlist> pls = db.playlistDao().getAllPlaylists();
            rvPlaylist.post(() -> {
                if (pls.isEmpty()) { Toast.makeText(ctx, "Aucune playlist sauvegardée", Toast.LENGTH_SHORT).show(); return; }
                String[] names = new String[pls.size()];
                for (int i = 0; i < pls.size(); i++)
                    names[i] = pls.get(i).name + " (" + pls.get(i).serviceType + ")";
                new AlertDialog.Builder(ctx).setTitle("📂 Charger une playlist")
                    .setItems(names, (d, which) -> loadPlaylist(pls.get(which)))
                    .setNegativeButton("Annuler", null).show();
            });
        });
    }

    private void loadPlaylist(Playlist pl) {
        currentPlaylist = pl;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<PlaylistItem> loaded = db.playlistDao().getItems(pl.uuid);
            rvPlaylist.post(() -> {
                items.clear(); items.addAll(loaded);
                if (adapter != null) adapter.notifyDataSetChanged();
                Toast.makeText(ctx, "📂 Chargé: " + pl.name, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmClear() {
        new AlertDialog.Builder(ctx).setTitle("Vider la playlist ?")
            .setPositiveButton("Vider", (d, w) -> { items.clear(); if (adapter != null) adapter.notifyDataSetChanged(); })
            .setNegativeButton("Annuler", null).show();
    }

    private void newPlaylist() { currentPlaylist = null; items.clear(); if (adapter != null) adapter.notifyDataSetChanged(); }

    public void setOnItemSelected(OnItemSelectedListener l) { selectListener = l; }
    public void setOnProjectRequested(OnProjectRequestedListener l) { projectListener = l; }
    public List<PlaylistItem> getItems() { return items; }

    // ── Adapter ──────────────────────────────────────
    class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_playlist, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            PlaylistItem item = items.get(pos);
            h.tvLabel.setText(item.label);
            h.tvType.setText(getTypeEmoji(item.type));
            boolean active = item.isActive;
            h.itemView.setBackgroundColor(active ? 0x22f0c040 : android.graphics.Color.TRANSPARENT);
            h.itemView.setOnClickListener(x -> selectItem(pos));
            h.itemView.setOnLongClickListener(x -> { showItemMenu(pos); return true; });
            h.btnProject.setOnClickListener(x -> { selectItem(pos); if (projectListener != null) projectListener.onProjectRequested(items.get(pos)); });
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvLabel, tvType; View btnProject;
            VH(View v) { super(v); tvLabel = v.findViewById(R.id.tv_pl_label); tvType = v.findViewById(R.id.tv_pl_type); btnProject = v.findViewById(R.id.btn_pl_item_project); }
        }
    }

    private String getTypeEmoji(String type) {
        if (type == null) return "?";
        switch (type) {
            case "verse":   return "📖";
            case "lyric":   return "🎵";
            case "announce":return "📢";
            case "image":   return "🖼";
            case "video":   return "🎬";
            case "lower_third":return "⬇";
            default:        return "•";
        }
    }

    private void showItemMenu(int pos) {
        PlaylistItem item = items.get(pos);
        new AlertDialog.Builder(ctx)
            .setTitle(item.label)
            .setItems(new String[]{"▶ Projeter", "⬆ Monter", "⬇ Descendre", "🗑 Supprimer"},
                (d, which) -> {
                    switch (which) {
                        case 0: selectItem(pos); if (projectListener != null) projectListener.onProjectRequested(item); break;
                        case 1: if (pos > 0) { Collections.swap(items, pos, pos-1); adapter.notifyDataSetChanged(); } break;
                        case 2: if (pos < items.size()-1) { Collections.swap(items, pos, pos+1); adapter.notifyDataSetChanged(); } break;
                        case 3: items.remove(pos); adapter.notifyItemRemoved(pos); break;
                    }
                }).show();
    }
}
