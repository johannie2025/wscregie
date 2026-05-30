package com.wisesmartchurch.system.remote;

import android.os.*; import android.view.*; import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.wisesmartchurch.system.R;
import okhttp3.*;
import okio.ByteString;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

/**
 * Mode Remote Maestro: affiche les lyrics de chants (strophes)
 * sur l'écran du chef de chorale/musicien. Interface sombre, gros texte.
 */
public class RemoteMaestroActivity extends AppCompatActivity {

    private TextView tvLyric, tvLabel, tvConnStatus;
    private EditText etIp;
    private OkHttpClient httpClient;
    private WebSocket ws;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_remote_maestro);
        tvLyric    = findViewById(R.id.tv_maestro_lyric);
        tvLabel    = findViewById(R.id.tv_maestro_label);
        tvConnStatus=findViewById(R.id.tv_maestro_conn);
        etIp       = findViewById(R.id.et_maestro_ip);
        View btnConn = findViewById(R.id.btn_maestro_connect);
        if(btnConn!=null) btnConn.setOnClickListener(v->connect());
        httpClient = new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).pingInterval(25,TimeUnit.SECONDS).build();
    }

    private void connect(){
        String ip=etIp!=null?etIp.getText().toString().trim():"";
        if(ip.isEmpty()){Toast.makeText(this,"Entrez l'IP",Toast.LENGTH_SHORT).show();return;}
        if(ws!=null)ws.close(1000,"reconnect");
        ws=httpClient.newWebSocket(new Request.Builder().url("ws://"+ip+":9000").build(),new WebSocketListener(){
            @Override public void onOpen(WebSocket w,Response r){
                try{JSONObject j=new JSONObject();j.put("type","identify");j.put("screenId","remote_maestro");j.put("role","maestro");w.send(j.toString());}catch(Exception ignored){}
                ui.post(()->setStatus("● "+ip,0xFF22c55e));
            }
            @Override public void onMessage(WebSocket w,String txt){ui.post(()->handle(txt));}
            @Override public void onMessage(WebSocket w,ByteString b){ui.post(()->handle(b.utf8()));}
            @Override public void onClosed(WebSocket w,int c,String r){ui.post(()->setStatus("○ Déconnecté",0xFFef4444));ui.postDelayed(()->connect(),4000);}
            @Override public void onFailure(WebSocket w,Throwable t,Response r){ui.post(()->setStatus("✕ "+t.getMessage(),0xFFef4444));ui.postDelayed(()->connect(),4000);}
        });
    }

    private void handle(String json){
        try{
            JSONObject p=new JSONObject(json);
            if("project".equals(p.optString("type"))){
                if(tvLyric!=null)tvLyric.setText(p.optString("text",""));
                if(tvLabel!=null)tvLabel.setText(p.optString("ref",""));
            }else if("clear".equals(p.optString("type"))){
                if(tvLyric!=null)tvLyric.setText("");
                if(tvLabel!=null)tvLabel.setText("");
            }
        }catch(Exception ignored){}
    }

    private void setStatus(String m,int c){if(tvConnStatus!=null){tvConnStatus.setText(m);tvConnStatus.setTextColor(c);}}
    @Override protected void onDestroy(){super.onDestroy();if(ws!=null)ws.close(1000,"bye");}
}
