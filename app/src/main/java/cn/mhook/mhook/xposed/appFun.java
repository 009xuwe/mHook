package cn.mhook.mhook.xposed;

import android.net.Proxy;
import android.net.VpnService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import cn.mhook.mhook.xposed.utils.H;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import static cn.mhook.mhook.xposed.Config.getEnable;
import static cn.mhook.mhook.xposed.utils.H.getStackTrace;

public class appFun {

    ClassLoader c;

    public appFun(ClassLoader c){
        this.c = c;
        if (getEnable("cProperty")) FProxy();
        if (getEnable("putJson")) putJson();
        if (getEnable("signBypass")) SignBypass.init();
    }

    private void getJson(){

        XposedBridge.hookAllMethods(JSONObject.class, "put", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                H.p(H.msg("添加JSONObject", com.alibaba.fastjson.JSONObject.toJSONString(param.args),getStackTrace()));
            }
        });

        XposedBridge.hookAllMethods(JSONArray.class, "put", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                H.p(H.msg("添加JSONArry", com.alibaba.fastjson.JSONObject.toJSONString(param.args),getStackTrace()));
            }
        });

        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass("com.alibaba.fastjson.JSONObject",c), "put", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    H.p(H.msg("添加JSONObject", com.alibaba.fastjson.JSONObject.toJSONString(param.args),getStackTrace()));
                }
            });

            XposedBridge.hookAllMethods(XposedHelpers.findClass("com.alibaba.fastjson.JSONArray",c), "add", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    H.p(H.msg("添加JSONArry", com.alibaba.fastjson.JSONObject.toJSONString(param.args),getStackTrace()));
                }
            });

        }catch (Throwable t){

        }
    }

    private void putJson(){

        XposedBridge.hookAllMethods(JSONObject.class, "put", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                H.p(H.msg("添加JSONObject", com.alibaba.fastjson.JSONObject.toJSONString(param.args),getStackTrace()));
            }
        });

        XposedBridge.hookAllMethods(JSONArray.class, "put", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                H.p(H.msg("添加JSONArry", com.alibaba.fastjson.JSONObject.toJSONString(param.args),getStackTrace()));
            }
        });

        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass("com.alibaba.fastjson.JSONObject",c), "put", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    H.p(H.msg("添加JSONObject", com.alibaba.fastjson.JSONObject.toJSONString(param.args),getStackTrace()));
                }
            });

            XposedBridge.hookAllMethods(XposedHelpers.findClass("com.alibaba.fastjson.JSONArray",c), "add", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    H.p(H.msg("添加JSONArry", com.alibaba.fastjson.JSONObject.toJSONString(param.args),getStackTrace()));
                }
            });

        }catch (Throwable t){

        }
    }

    private void FProxy(){

        XposedBridge.hookAllMethods(System.class, "getProperty", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                String s = (String)param.args[0];
                if (s.equals("http.proxyHost")||s.equals("http.proxyPort")){
                    H.p(H.msg("输出日志","尝试检测代理",getStackTrace()));
                    param.setResult(null);
                }
            }
        });
        XposedBridge.hookAllMethods(Proxy.class, "getHost", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                H.p(H.msg("代理检测","尝试获取HOST",getStackTrace()));
                param.setResult(null);
            }
        });
        XposedBridge.hookAllMethods(Proxy.class, "getPort", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                H.p(H.msg("代理检测","尝试获取Port",getStackTrace()));
                param.setResult(null);
            }
        });
        XposedBridge.hookAllMethods(VpnService.Builder.class, "addAddress", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                H.p(H.msg("代理检测","添加地址: "+param.args[0],getStackTrace()));
            }
        });
        XposedBridge.hookAllConstructors(VpnService.Builder.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                H.p(H.msg("代理检测","初始化",getStackTrace()));
            }
        });
    }
}
