package com.wisesmartchurch.system.ui.screens;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.db.AppDatabase;
import com.wisesmartchurch.system.model.ScreenDevice;
import com.wisesmartchurch.system.server.WscServerService;
import java.util.*;
import java.util.concurrent.Executors;

/** Gérer les écrans connectés: layout, rôle, ce qui est affiché */
public class ScreensManagerActivity extends AppCompatActivity {

    private AppDatabase db;
    private RecyclerView rvScreens;
    private ScreenAdapter adapter;
    private List<ScreenDevice> screens = new ArrayList<>();
    private WscServerService server;
    private boolean bound = false;

    private final ServiceConnection conn = new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){
            server=((WscServerService.LocalBinder)b).getService(); bound=true;
            loadScreens();
        }
        @Override public void onServiceDisconnected(ComponentName n){bound=false;}
    };

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        setContentView(R.layout.activity_screens);
        db=AppDatabase.getInstance(this);
        rvScreens=findViewById(R.id.rv_screens);
        if(rvScreens!=null){
            rvScreens.setLayoutManager(new LinearLayoutManager(this));
            adapter=new ScreenAdapter();
            rvScreens.setAdapter(adapter);
        }
        bindService(new Intent(this,WscServerService.class),conn,BIND_AUTO_CREATE);
    }

    private void loadScreens(){
        Executors.newSingleThreadExecutor().execute(()->{
            screens = db.screenDao().getAll();
            // Mark online screens
            if(bound){
                List<String> onlineIps = server.getConnectedIps();
                for(ScreenDevice sd:screens) sd.isOnline=onlineIps.contains(sd.ip);
            }
            runOnUiThread(()->adapter.notifyDataSetChanged());
        });
    }

    private void showConfigDialog(ScreenDevice sd){
        View v=LayoutInflater.from(this).inflate(R.layout.dialog_screen_config,null);
        EditText etName   = v.findViewById(R.id.et_screen_name);
        Spinner  spRole   = v.findViewById(R.id.sp_screen_role);
        Spinner  spLayout = v.findViewById(R.id.sp_screen_layout);
        CheckBox cbVerse  = v.findViewById(R.id.cb_screen_verse);
        CheckBox cbLt     = v.findViewById(R.id.cb_screen_lt);
        CheckBox cbClock  = v.findViewById(R.id.cb_screen_clock);
        CheckBox cbPlan   = v.findViewById(R.id.cb_screen_plan);
        if(etName!=null)  etName.setText(sd.name!=null?sd.name:"");
        if(cbVerse!=null) cbVerse.setChecked(sd.showVerse);
        if(cbLt!=null)    cbLt.setChecked(sd.showLt);
        if(cbClock!=null) cbClock.setChecked(sd.showClock);
        if(cbPlan!=null)  cbPlan.setChecked(sd.showPlan);
        new android.app.AlertDialog.Builder(this).setTitle("⚙ Configurer: "+sd.name).setView(v)
            .setPositiveButton("Sauvegarder",(d,w)->{
                if(etName!=null)  sd.name      = etName.getText().toString().trim();
                if(spRole!=null)  sd.role      = spRole.getSelectedItem().toString().toLowerCase();
                if(spLayout!=null)sd.layout    = spLayout.getSelectedItem().toString().toLowerCase().replace(" ","_");
                if(cbVerse!=null) sd.showVerse = cbVerse.isChecked();
                if(cbLt!=null)    sd.showLt    = cbLt.isChecked();
                if(cbClock!=null) sd.showClock = cbClock.isChecked();
                if(cbPlan!=null)  sd.showPlan  = cbPlan.isChecked();
                Executors.newSingleThreadExecutor().execute(()->{db.screenDao().update(sd);loadScreens();});
                // Send config to that screen
                if(bound) sendScreenConfig(sd);
            })
            .setNegativeButton("Annuler",null).show();
    }

    private void sendScreenConfig(ScreenDevice sd){
        try{
            org.json.JSONObject j=new org.json.JSONObject();
            j.put("type","screen_config");
            j.put("showVerse",sd.showVerse); j.put("showLt",sd.showLt);
            j.put("showClock",sd.showClock); j.put("showPlan",sd.showPlan);
            j.put("layout",sd.layout!=null?sd.layout:"verse_only");
            server.broadcastTo(sd.ip,j.toString());
        }catch(Exception ignored){}
    }

    class ScreenAdapter extends RecyclerView.Adapter<ScreenAdapter.VH>{
        @Override public VH onCreateViewHolder(ViewGroup p,int t){
            return new VH(LayoutInflater.from(ScreensManagerActivity.this).inflate(R.layout.item_screen,p,false));
        }
        @Override public void onBindViewHolder(VH h,int pos){
            ScreenDevice sd=screens.get(pos);
            h.tvName.setText(sd.name!=null?sd.name:sd.ip);
            h.tvIp.setText(sd.ip+" ("+( sd.role!=null?sd.role:"main")+")");
            h.tvStatus.setText(sd.isOnline?"● EN LIGNE":"○ Hors ligne");
            h.tvStatus.setTextColor(sd.isOnline?0xFF22c55e:0xFFef4444);
            h.btnConfig.setOnClickListener(x->showConfigDialog(sd));
        }
        @Override public int getItemCount(){return screens.size();}
        class VH extends RecyclerView.ViewHolder{
            TextView tvName,tvIp,tvStatus; View btnConfig;
            VH(View v){super(v);tvName=v.findViewById(R.id.tv_screen_name);tvIp=v.findViewById(R.id.tv_screen_ip);tvStatus=v.findViewById(R.id.tv_screen_status);btnConfig=v.findViewById(R.id.btn_screen_config);}
        }
    }

    @Override protected void onDestroy(){super.onDestroy();if(bound)unbindService(conn);}
}
