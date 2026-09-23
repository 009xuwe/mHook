package cn.mhook.mhook.xposed.utils;

import android.content.Context;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import cn.mhook.mhook.contentprovider.PrintData;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public  class H {
    public static String pkg;
    public static XC_LoadPackage.LoadPackageParam loadPackageParam;
    public static JSONArray waitSend;
    public static Context context;
    public static Context aContext;
    public static Context systemContext;
    public static IXposedHookZygoteInit.StartupParam startupparam;

    /** 重入保护：日志链路自身（ContentProvider→Binder/SQLite→new File 等）会再次触发已注册的
     *  hook（尤其 File 构造），造成无限递归与内存爆涨，用线程本地标记截断。 */
    private static final ThreadLocal<Boolean> sInLog = new ThreadLocal<Boolean>();
    /** 上下文就绪前最多缓存的日志条数，避免长期无 context 时 waitSend 无限堆积。 */
    private static final int WAIT_SEND_MAX = 500;

    public static void p(String msg){
        if (Boolean.TRUE.equals(sInLog.get())) return;
        sInLog.set(Boolean.TRUE);
        try {
            JSONObject j = JSON.parseObject(msg);
            XposedBridge.log("test---"+H.pkg+JSONObject.toJSONString(j,true));
            if (waitSend==null){
                waitSend = new JSONArray();
            }
            if (context==null&&aContext==null){
                if (waitSend.size() < WAIT_SEND_MAX) {
                    waitSend.add(msg);
                }
            }else if (context!=null){
                if (waitSend.size()>0){
                    for (Object o:waitSend){
                        PrintData.putData(context,o.toString());
                    }
                    waitSend.clear();
                    PrintData.putData(context,msg);
                }else {
                    PrintData.putData(context,msg);
                }
            }else if (aContext!=null){
                if (waitSend.size()>0){
                    for (Object o:waitSend){
                        PrintData.putData(aContext,o.toString());
                    }
                    waitSend.clear();
                    PrintData.putData(aContext,msg);
                }else {
                    PrintData.putData(aContext,msg);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            sInLog.remove();
        }
    }

    /** 上下文就绪后冲刷之前缓存的记录，避免早期事件因后续无事件而丢失。 */
    public static void flush(){
        try {
            if (waitSend == null || waitSend.size() == 0) return;
            Context c = context != null ? context : aContext;
            if (c == null) return;
            for (Object o : waitSend) {
                PrintData.putData(c, o.toString());
            }
            waitSend.clear();
        } catch (Throwable ignored) {
        }
    }

    public static String msg(String type,Object msg,Object other){
        JSONObject ret = new JSONObject(true);
        ret.put("type",type);
        ret.put("msg",msg);
        ret.put("other",other);
        return ret.toJSONString();
    }


    public static JSONArray getStackTrace(){
        JSONArray s = new JSONArray();
        Throwable ex = new Throwable();
        StackTraceElement[] stackElements = ex.getStackTrace();
        if (stackElements != null) {
            for (int i = 0; i < stackElements.length; i++) {
                String ClassName = stackElements[i].getClassName();
                String MethodName = stackElements[i].getMethodName();
                if (!ClassName.startsWith("cn.mhook.mhook")&&!ClassName.startsWith("de.robv.android.xposed")){
                    s.add("类："+ClassName+"--方法："+MethodName);
                }
            }
        }
        return s;
    }


    public static JSONObject putDetail(XC_MethodHook.MethodHookParam param,JSONArray jsonArray){
        JSONObject j = new JSONObject(true);
        if (param!=null&&param.args!=null&&param.args.length>0){
            j.put("详情",JSONObject.toJSONString(param.args));
        }
        j.put("调用",jsonArray);
        return j;
    }

}
