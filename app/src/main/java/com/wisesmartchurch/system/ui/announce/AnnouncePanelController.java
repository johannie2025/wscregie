package com.wisesmartchurch.system.ui.announce;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.db.AppDatabase;
import com.wisesmartchurch.system.model.Announce;
import java.util.*;
import java.util.concurrent.Executors;

public class AnnouncePanelController {

    public interface OnAnnounceSelectedListener {
        void onAnnounceSelected(String text, String title, String bgMode, String bgUrl);
    }

    private final Context ctx; private final View root; private final AppDatabase db;
    private OnAnnounceSelectedListener listener;
    private List<Announce> announces = new ArrayList<>();
    private RecyclerView   rvAnnounces;
    private AnnounceAdapter adapter;

    public AnnouncePanelController(Context ctx, View root, AppDatabase db) {
        this.ctx=ctx; this.root=root; this.db=db;
        initViews(); loadAnnounces();
    }

    private void initViews() {
        if (root==null) return;
        rvAnnounces = root.findViewById(R.id.rv_announces);
        if (rvAnnounces!=null) {
            rvAnnounces.setLayoutManager(new LinearLayoutManager(ctx));
            adapter = new AnnounceAdapter();
            rvAnnounces.setAdapter(adapter);
        }
        View btnAdd = root.findViewById(R.id.btn_add_announce);
        if (btnAdd!=null) btnAdd.setOnClickListener(v->showAddDialog());
    }

    private void loadAnnounces() {
        Executors.newSingleThreadExecutor().execute(()->{
            announces = db.announceDao().getAll();
            if (rvAnnounces!=null) rvAnnounces.post(()->adapter.notifyDataSetChanged());
        });
    }

    private void showAddDialog() {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_announce, null);
        EditText etTitle    = v.findViewById(R.id.et_ann_title);
        EditText etBody     = v.findViewById(R.id.et_ann_body);
        EditText etSubtitle = v.findViewById(R.id.et_ann_subtitle);
        EditText etBgUrl    = v.findViewById(R.id.et_ann_bg);
        Spinner  spCategory = v.findViewById(R.id.sp_ann_category);
        new AlertDialog.Builder(ctx).setTitle("📢 Ajouter une annonce").setView(v)
            .setPositiveButton("Enregistrer",(d,w)->{
                Announce a = new Announce();
                a.title    = etTitle    !=null ? etTitle.getText().toString().trim()    : "";
                a.body     = etBody     !=null ? etBody.getText().toString().trim()     : "";
                a.subtitle = etSubtitle !=null ? etSubtitle.getText().toString().trim() : "";
                a.bgUrl    = etBgUrl    !=null ? etBgUrl.getText().toString().trim()    : "";
                a.bgMode   = a.bgUrl.isEmpty() ? "black" : "image";
                a.category = spCategory!=null  ? spCategory.getSelectedItem().toString().toLowerCase() : "annonce";
                a.createdAt = System.currentTimeMillis();
                Executors.newSingleThreadExecutor().execute(()->{db.announceDao().insert(a);loadAnnounces();});
            })
            .setNegativeButton("Annuler",null).show();
    }

    private void select(Announce a) {
        String text = (a.title!=null&&!a.title.isEmpty() ? a.title+"\n\n":"") + (a.body!=null?a.body:"");
        if (listener!=null) listener.onAnnounceSelected(text, a.title, a.bgMode, a.bgUrl);
    }

    public void setOnAnnounceSelected(OnAnnounceSelectedListener l){this.listener=l;}

    class AnnounceAdapter extends RecyclerView.Adapter<AnnounceAdapter.VH> {
        @Override public VH onCreateViewHolder(ViewGroup p,int t){
            return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_announce,p,false));
        }
        @Override public void onBindViewHolder(VH h,int pos){
            Announce a=announces.get(pos);
            h.tvTitle.setText(a.title!=null?a.title:"");
            h.tvBody.setText(a.body!=null?a.body.substring(0,Math.min(a.body.length(),80)):"");
            h.tvCat.setText(a.category!=null?a.category:"");
            h.itemView.setOnClickListener(x->select(a));
            h.btnDelete.setOnClickListener(x->new AlertDialog.Builder(ctx).setTitle("Supprimer ?")
                .setPositiveButton("Oui",(d,w)->Executors.newSingleThreadExecutor().execute(()->{db.announceDao().delete(a);loadAnnounces();}))
                .setNegativeButton("Non",null).show());
        }
        @Override public int getItemCount(){return announces.size();}
        class VH extends RecyclerView.ViewHolder{
            TextView tvTitle,tvBody,tvCat; View btnDelete;
            VH(View v){super(v);tvTitle=v.findViewById(R.id.tv_ann_title);tvBody=v.findViewById(R.id.tv_ann_body);tvCat=v.findViewById(R.id.tv_ann_cat);btnDelete=v.findViewById(R.id.btn_ann_delete);}
        }
    }
}
