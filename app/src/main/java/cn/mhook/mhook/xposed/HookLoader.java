package cn.mhook.mhook.xposed;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.tamsiree.rxkit.RxFileTool;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class HookLoader implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    //按照实际使用情况修改下面几项的值
    /**
     * 当前Xposed模块的包名,方便寻找apk文件
     */
    private final static String modulePackageName = "cn.mhook.mhook";

    /**
     * 实际hook逻辑处理类
     */
    private final String handleHookClass = XposedMain.class.getName();
    /**
     * 实际hook逻辑处理类的入口方法
     */
    private final String handleHookMethod = "handleLoadPackage";

    private final String initMethod = "initZygote";

    private IXposedHookZygoteInit.StartupParam startupparam;

    /**
     * 重定向handleLoadPackage函数前会执行initZygote
     *
     * @param loadPackageParam
     * @throws Throwable
     */
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (loadPackageParam.appInfo == null) {
            return;
        }
        // 排除系统应用
        if ((loadPackageParam.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 1) {
            return;
        }
        // 关键：绝不注入系统框架(system_server)与 SystemUI，否则 hook Activity.onCreate / System.getProperty /
        // Proxy / VpnService 等极易拖死系统进程，导致卡开机（Pixel/Android16 + LSPosed 已复现）。
        String pkg = loadPackageParam.packageName;
        if ("android".equals(pkg) || "com.android.systemui".equals(pkg)
                || "com.android.phone".equals(pkg)) {
            return;
        }
        Class<?> cls = getApkClass(modulePackageName);
        Object instance = cls.newInstance();
        try {
            cls.getDeclaredMethod(initMethod, startupparam.getClass()).invoke(instance, startupparam);
        }catch (NoSuchMethodException e){
            // 找不到initZygote方法
        }
        // 模块自身逻辑异常不应向上抛（否则 LSPosed 会记录 InvocationTargetException）
        try {
            cls.getDeclaredMethod(handleHookMethod, loadPackageParam.getClass()).invoke(instance, loadPackageParam);
        } catch (Throwable e) {
            android.util.Log.d("mhook", "handleLoadPackage 跳过 " + pkg + ": " + e.getMessage());
        }

        /*
        //将loadPackageParam的classloader替换为宿主程序Application的classloader,解决宿主程序存在多个.dex文件时,有时候ClassNotFound的问题
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                loadPackageParam.classLoader = context.getClassLoader();
                Class<?> cls = getApkClass(context, modulePackageName, handleHookClass);
                Object instance = cls.newInstance();
                try {
                    cls.getDeclaredMethod(initMethod, startupparam.getClass()).invoke(instance, startupparam);
                }catch (NoSuchMethodException e){
                    // 找不到initZygote方法
                }
                cls.getDeclaredMethod(handleHookMethod, loadPackageParam.getClass()).invoke(instance, loadPackageParam);
            }
        });*/
    }

    /**
     * 实现initZygote，保存启动参数。
     *
     * @param startupParam
     */
    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) {
        this.startupparam = startupParam;
    }

    private Class<?> getApkClass(String modulePackageName) throws Throwable {
        ClassLoader moduleLoader = getClass().getClassLoader();
        if (moduleLoader != null) {
            try {
                return Class.forName(handleHookClass, false, moduleLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
        currentActivityThreadMethod.setAccessible(true);
        Object activityThread = currentActivityThreadMethod.invoke(activityThreadClass);
        Method getSystemContextMethod = activityThread.getClass().getDeclaredMethod("getSystemContext");
        getSystemContextMethod.setAccessible(true);
        Context systemContext = (Context) getSystemContextMethod.invoke(activityThread);
        PackageManager pm = systemContext.getPackageManager();
        String path = pm.getApplicationInfo(modulePackageName, 0).publicSourceDir;

        Class<?> cls;
        if (Build.VERSION.SDK_INT < 26) {
            PathClassLoader pluginDexClassLoader = new PathClassLoader(path, ClassLoader.getSystemClassLoader());
            cls = Class.forName(handleHookClass, true, pluginDexClassLoader);
        }else {
            DexClassLoader pluginDexClassLoader = new DexClassLoader(path, null, null, moduleLoader);
            cls = Class.forName(handleHookClass, true, pluginDexClassLoader);
        }
        return cls;
    }

    private File findApkFile(String packageName){

        String mApkFilePath = null;
        //查找apk路径
        if (mApkFilePath == null){
            //1、从 /data/app/com.lanshifu.xposeddemo-1.apk 找
            mApkFilePath = String.format("/data/app/%s-%s.apk", packageName, 1);
            if (!new File(mApkFilePath).exists()) {
                //2、从 /data/app/com.lanshifu.xposeddemo-2.apk 找
                mApkFilePath = String.format("/data/app/%s-%s.apk", packageName, 2);
                if (!new File(mApkFilePath).exists()) {
                    //3、从 /data/app/com.lanshifu.xposeddemo-1/base.apk 找
                    mApkFilePath = String.format("/data/app/%s-%s/base.apk", packageName, 1);
                    if (!new File(mApkFilePath).exists()) {
                        //4、从 /data/app/com.lanshifu.xposeddemo-2/base.apk 找
                        mApkFilePath = String.format("/data/app/%s-%s/base.apk", packageName, 2);
                        if (!new File(mApkFilePath).exists()) {
                            mApkFilePath = null;
                        }
                    }
                }
            }
        }
        return new File(mApkFilePath);
    }

    /**
     * 根据包名构建目标Context,并调用getPackageCodePath()来定位apk
     *
     * @param context           context参数
     * @param modulePackageName 当前模块包名
     * @return apk file
     */
    private File findApkFile(Context context, String modulePackageName) {
        if (context == null) {
            return null;
        }
        try {
            Context moudleContext = context.createPackageContext(modulePackageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            String apkPath = moudleContext.getPackageCodePath();
            return new File(apkPath);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}