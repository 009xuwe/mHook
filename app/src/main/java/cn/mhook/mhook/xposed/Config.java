package cn.mhook.mhook.xposed;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.tamsiree.rxkit.RxFileTool;
import java.io.File;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

import static cn.mhook.mData.jsonDir;
import static cn.mhook.mhook.xposed.utils.H.context;
import static cn.mhook.mhook.xposed.utils.H.pkg;
import static cn.mhook.mhook.xposed.utils.mHookCfg.SettingDir;

public class Config {

    public static JSONArray getJsonCfg(){
       /* if (context!=null){
            JSONArray ret = cn.mhook.mhook.contentprovider.jsonCfg.getAllCfgByPkg(pkg);
            return ret.isEmpty()?getJsonCfgByPatch():ret;
        }else {
            return getJsonCfgByPatch();
        }*/
        return getJsonCfgByPatch();
    }

    static JSONArray getJsonCfgByPatch(){
        JSONArray ret = new JSONArray();
        String patch = jsonDir + pkg+"/jsonCfg/";
        XposedBridge.log("test---"+patch);
        if (RxFileTool.fileExists(patch)){
            XposedBridge.log("test---xxxxx-"+patch);
            List<File> files = RxFileTool.listFilesInDirWithFilter(patch,".json");
            for (File file:files){
                String cfg = RxFileTool.readFile2String(file,"utf-8");
                ret.add(JSONObject.parseObject(cfg));
            }
        }
        return ret;
    }

    public static JSONObject getAppCfg(){
        if (RxFileTool.isFileExists(SettingDir)) {
           String cfg =  RxFileTool.readFile2String(SettingDir,"utf-8");
           try {
               JSONObject config = JSONObject.parseObject(cfg);
               return config;
           }catch (Throwable e){

           }
        }
        return null;
    }

    public static Boolean getEnable(String key){
        if (getAppCfg()!=null&&getAppCfg().containsKey(key)&&getAppCfg().getBoolean(key)){
            return true;
        }
        return false;
    }

    public static String getString(String key, String def){
        JSONObject cfg = getAppCfg();
        if (cfg!=null && cfg.containsKey(key)){
            String v = cfg.getString(key);
            if (v!=null && !v.isEmpty()) return v;
        }
        return def;
    }

    public static int getInt(String key, int def){
        JSONObject cfg = getAppCfg();
        if (cfg!=null && cfg.containsKey(key)){
            try {
                return cfg.getIntValue(key);
            }catch (Throwable ignored){
            }
        }
        return def;
    }
}
