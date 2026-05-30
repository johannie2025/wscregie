package com.wisesmartchurch.system.model;
import org.json.JSONObject;
import org.json.JSONException;
public class ProjectionPayload {
    public String type; public String text; public String ref;
    public String color; public String fontSize; public BgInfo bg;
    public LtInfo lt; public String tagLabel; public int overlaySlot;
    public boolean showCamera; public String cameraStreamUrl; public String screenTargets;
    public static class BgInfo { public String mode,url,color; }
    public static class LtInfo { public boolean show; public String name,role,type,style; }
    public JSONObject toJson() {
        try {
            JSONObject j=new JSONObject();
            j.put("type",type!=null?type:"project");
            if(text!=null)j.put("text",text);if(ref!=null)j.put("ref",ref);
            if(color!=null)j.put("color",color);if(fontSize!=null)j.put("fontSize",fontSize);
            if(screenTargets!=null)j.put("targets",screenTargets);
            j.put("overlaySlot",overlaySlot);
            if(bg!=null){JSONObject jb=new JSONObject();jb.put("mode",bg.mode!=null?bg.mode:"black");jb.put("url",bg.url!=null?bg.url:"");jb.put("color",bg.color!=null?bg.color:"#000");j.put("bg",jb);}
            if(lt!=null){JSONObject jl=new JSONObject();jl.put("show",lt.show);jl.put("name",lt.name!=null?lt.name:"");jl.put("role",lt.role!=null?lt.role:"");jl.put("type",lt.type!=null?lt.type:"pred");jl.put("style",lt.style!=null?lt.style:"standard");j.put("lt",jl);j.put("type","lt");}
            if(tagLabel!=null){j.put("label",tagLabel);j.put("type","tag");}
            if(showCamera&&cameraStreamUrl!=null)j.put("cameraStream",cameraStreamUrl);
            return j;
        }catch(JSONException e){return new JSONObject();}
    }
    public static ProjectionPayload clear(){ProjectionPayload p=new ProjectionPayload();p.type="clear";return p;}
}
