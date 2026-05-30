package com.wisesmartchurch.system.remote;

import android.os.*; import android.view.*; import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.wisesmartchurch.system.R;
import com.wisesmartchurch.system.server.WscWebSocketServer;
import okhttp3.*;
import okio.ByteString;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Mode Remote Prédicateur: se connecte à la régie via WS,
 * affiche le verset projeté en gros sur l'écran du prédicateur.
 */
public class RemotePreacherActivity extends AppCompatActivity {

    private TextView  tvVerse, tvRef, tvStatus, tvConnStatus;
    private EditText  etIp;
    private OkHttpClient httpClient;
    private WebSocket ws;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean connected = false;

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_remote_preacher);
        tvVerse     = findViewById(R.id.tv_remote_verse);
        tvRef       = findViewById(R.id.tv_remote_ref);
        tvStatus    = findViewById(R.id.tv_remote_status);
        tvConnStatus= findViewById(R.id.tv_remote_conn);
        etIp        = findViewById(R.id.et_remote_ip);
        View btnConnect = findViewById(R.id.btn_remote_connect);
        if(btnConnect!=null) btnConnect.setOnClickListener(v->connect());

        httpClient = new OkHttpClient.Builder()
            .connectTimeout(8,TimeUnit.SECONDS)
            .readTimeout(0,TimeUnit.MILLISECONDS)
            .pingInterval(25,TimeUnit.SECONDS).build();
    }

    private void connect(){
        String ip = etIp!=null ? etIp.getText().toString().trim() : "";
        if(ip.isEmpty()){Toast.makeText(this,"Entrez l'IP de la régie",Toast.LENGTH_SHORT).show();return;}
        if(ws!=null){ws.close(1000,"reconnect");}
        String url = "ws://"+ip+":9000";
        setStatus("Connexion…",0xFFF59E0B);
        ws = httpClient.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener(){
            @Override public void onOpen(WebSocket ws, Response r){
                connected=true;
                try{JSONObject j=new JSONObject();j.put("type","identify");j.put("screenId","remote_preacher");j.put("role","preacher");ws.send(j.toString());}catch(Exception ignored){}
                ui.post(()->setStatus("● Connecté à "+ip,0xFF22c55e));
            }
            @Override public void onMessage(WebSocket ws, String txt){
                ui.post(()->handleMessage(txt));
            }
            @Override public void onMessage(WebSocket ws, ByteString bytes){ ui.post(()->handleMessage(bytes.utf8())); }
            @Override public void onClosed(WebSocket ws,int code,String reason){ connected=false; ui.post(()->setStatus("○ Déconnecté",0xFFef4444)); scheduleReconnect(ip); }
            @Override public void onFailure(WebSocket ws,Throwable t,Response r){ connected=false; ui.post(()->setStatus("✕ Erreur: "+(t.getMessage()!=null?t.getMessage():""),0xFFef4444)); scheduleReconnect(ip); }
        });
    }

    private void handleMessage(String json){
        try{
            JSONObject p=new JSONObject(json);
            String type=p.optString("type","");
            if("project".equals(type)){
                String text=p.optString("text",""); String ref=p.optString("ref","");
                if(tvVerse!=null){tvVerse.setText(text);tvVerse.setVisibility(text.isEmpty()?View.INVISIBLE:View.VISIBLE);}
                if(tvRef!=null){tvRef.setText(ref);tvRef.setVisibility(ref.isEmpty()?View.GONE:View.VISIBLE);}
            } else if("clear".equals(type)){
                if(tvVerse!=null){tvVerse.setText("");tvVerse.setVisibility(View.INVISIBLE);}
                if(tvRef!=null){tvRef.setText("");tvRef.setVisibility(View.GONE);}
            }
        }catch(Exception ignored){}
    }

    private void scheduleReconnect(String ip){ if(!isFinishing()) ui.postDelayed(()->connect(),4000); }
    private void setStatus(String msg,int color){ if(tvConnStatus!=null){tvConnStatus.setText(msg);tvConnStatus.setTextColor(color);} }

    @Override protected void onDestroy(){super.onDestroy();if(ws!=null)ws.close(1000,"bye");httpClient.dispatcher().executorService().shutdown();}
}
