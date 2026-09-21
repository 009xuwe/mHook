package cn.mhook.debug;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * frida 组件按需下载（frida-server / frida-gadget xz）。
 *
 * 默认从 GitHub frida release 下载（VERSION），产物以 xz 存到 filesDir/frida/，
 * 与 FridaServerManager / GadgetManager 的解压入口对齐（同名 xz 即命中本地源）。
 */
public class FridaAssetDownloader {

    public static final String VERSION = "16.5.2";
    public static final String DOWNLOAD_BASE =
            "https://github.com/frida/frida/releases/download/" + VERSION + "/";

    private static void log(FridaServerManager.Progress p, String s) {
        if (p != null) {
            p.onLog(s);
        }
    }

    private static File fridaDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "frida");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** 与 assets/frida 同名的 xz，下载后即被解压入口识别。 */
    public static File localServerXz(Context ctx, String abi) {
        if (abi == null) {
            return null;
        }
        return new File(fridaDir(ctx), "frida-server-" + abi + ".xz");
    }

    public static File localGadgetXz(Context ctx, String abi) {
        if (abi == null) {
            return null;
        }
        return new File(fridaDir(ctx), "frida-gadget-" + abi + ".so.xz");
    }

    public static boolean serverXzReady(Context ctx, String abi) {
        File f = localServerXz(ctx, abi);
        return f != null && f.isFile() && f.length() > 0;
    }

    public static boolean gadgetXzReady(Context ctx, String abi) {
        File f = localGadgetXz(ctx, abi);
        return f != null && f.isFile() && f.length() > 0;
    }

    public static String serverUrl(String abi) {
        return DOWNLOAD_BASE + "frida-server-" + VERSION + "-android-" + abi + ".xz";
    }

    public static String gadgetUrl(String abi) {
        return DOWNLOAD_BASE + "frida-gadget-" + VERSION + "-android-" + abi + ".so.xz";
    }

    /** 下载到 filesDir/frida/<目标名>；成功返回文件，失败返回 null（含已存在跳过）。 */
    public static File download(Context ctx, String url, File target, FridaServerManager.Progress p) {
        if (target == null) {
            log(p, "目标为空");
            return null;
        }
        if (target.isFile() && target.length() > 0) {
            log(p, target.getName() + " 已存在，跳过下载");
            return target;
        }
        File dir = new File(ctx.getFilesDir(), "frida");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File tmp = new File(dir, target.getName() + ".tmp");
        try {
            log(p, "下载 " + url);
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 mHook");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                log(p, "HTTP " + code + "：" + url);
                conn.disconnect();
                return null;
            }
            long total = conn.getContentLengthLong();
            InputStream in = new BufferedInputStream(conn.getInputStream(), 1 << 16);
            OutputStream os = new FileOutputStream(tmp);
            byte[] buf = new byte[1 << 16];
            long got = 0;
            long last = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                got += n;
                if (got - last >= (2L << 20)) {
                    last = got;
                    log(p, "  已下载 " + (got >> 20) + " MB"
                            + (total > 0 ? " / " + (total >> 20) + " MB" : ""));
                }
            }
            os.close();
            in.close();
            conn.disconnect();
            if (target.exists() && !target.delete()) {
                log(p, "覆盖旧文件失败");
            }
            if (!tmp.renameTo(target)) {
                tmp.delete();
                log(p, "临时文件重命名失败");
                return null;
            }
            log(p, "下载完成：" + (target.length() >> 20) + " MB -> " + target.getName());
            return target;
        } catch (Throwable t) {
            log(p, "下载失败：" + t);
            try {
                tmp.delete();
            } catch (Throwable ignored) {
            }
            return null;
        }
    }
}