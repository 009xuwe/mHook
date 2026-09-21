package cn.mhook.debug;

import android.content.Context;
import android.os.Build;

import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import cn.mhook.msu.su;

/**
 * frida-server 托管（阶段 0：基座与通信 / Root 模式）。
 *
 * 流程：assets/frida/frida-server-&lt;abi&gt;.xz → 解压到私有目录 → su 复制到
 * /data/local/tmp/frida-server → chmod 755 → 后台启动 → 监听 127.0.0.1:27042。
 */
public class FridaServerManager {

    public static final String REMOTE_PATH = "/data/local/tmp/frida-server";
    public static final int DEFAULT_PORT = 27042;

    public interface Progress {
        void onLog(String s);
    }

    private static void log(Progress p, String s) {
        if (p != null) {
            p.onLog(s);
        }
    }

    /** 当前设备对应的 assets 文件名后缀（arm64 / x86_64），不支持返回 null。 */
    public static String abiTag() {
        if (Build.SUPPORTED_ABIS == null) {
            return null;
        }
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                return "arm64";
            }
            if ("x86_64".equals(abi)) {
                return "x86_64";
            }
        }
        return null;
    }

    public static String abiDisplay() {
        return (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                ? Build.SUPPORTED_ABIS[0] : "unknown";
    }

    public static boolean isSupported() {
        return abiTag() != null;
    }

    /** 私有目录里的本地二进制（解压产物）。 */
    public static File localBinary(Context ctx) {
        String abi = abiTag();
        if (abi == null) {
            return null;
        }
        return new File(new File(ctx.getFilesDir(), "frida"), "frida-server-" + abi);
    }

    public static boolean isExtracted(Context ctx) {
        File f = localBinary(ctx);
        return f != null && f.isFile() && f.length() > 0;
    }

    /** 从 assets 解压 xz 到私有目录；返回本地文件，失败返回 null。 */
    public static File extractFromAssets(Context ctx, Progress p) {
        String abi = abiTag();
        if (abi == null) {
            log(p, "不支持的 CPU 架构：" + abiDisplay());
            return null;
        }
        File dir = new File(ctx.getFilesDir(), "frida");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File out = new File(dir, "frida-server-" + abi);
        File tmp = new File(dir, "frida-server-" + abi + ".tmp");
        String asset = "frida/frida-server-" + abi + ".xz";
        InputStream raw;
        try {
            // 优先本地已下载的 xz（FridaAssetDownloader），缺失时回退 assets
            File xz = new File(dir, "frida-server-" + abi + ".xz");
            if (xz.isFile() && xz.length() > 0) {
                log(p, "解压本地 " + xz.getName() + " ...");
                raw = new FileInputStream(xz);
            } else {
                log(p, "解压 " + asset + " ...");
                raw = ctx.getAssets().open(asset);
            }
        } catch (Throwable t) {
            log(p, "frida-server 未内置且未下载，请先点「下载 frida 组件」");
            return null;
        }
        try {
            XZInputStream in = new XZInputStream(new BufferedInputStream(raw, 1 << 16));
            OutputStream os = new FileOutputStream(tmp);
            byte[] buf = new byte[1 << 16];
            int n;
            long total = 0;
            long lastReport = 0;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                total += n;
                if (total - lastReport >= (8L << 20)) {
                    lastReport = total;
                    log(p, "  已解压 " + (total >> 20) + " MB");
                }
            }
            os.close();
            in.close();
            raw.close();
            if (out.exists() && !out.delete()) {
                log(p, "无法覆盖旧文件");
            }
            if (!tmp.renameTo(out)) {
                // rename 失败时退回复制
                copy(tmp, out);
                tmp.delete();
            }
            log(p, "解压完成：" + (out.length() >> 20) + " MB");
            return out;
        } catch (Throwable t) {
            log(p, "解压失败：" + t);
            try {
                tmp.delete();
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    public static boolean hasRoot() {
        String out = su.getOutput("id");
        return out != null && out.contains("uid=0");
    }

    /** 复制到 /data/local/tmp 并赋可执行权限（需要 root）。 */
    public static boolean deploy(Context ctx, Progress p) {
        File local = localBinary(ctx);
        if (local == null || !local.isFile()) {
            log(p, "本地二进制不存在，请先解压");
            return false;
        }
        if (!hasRoot()) {
            log(p, "无 root 权限，无法部署");
            return false;
        }
        log(p, "复制到 " + REMOTE_PATH + " ...");
        String cmd = "cp \"" + local.getAbsolutePath() + "\" " + REMOTE_PATH
                + " && chmod 755 " + REMOTE_PATH;
        su.getOutput(cmd);
        String chk = su.getOutput("test -x " + REMOTE_PATH + " && echo MHOOK_OK || echo MHOOK_NO");
        boolean ok = chk != null && chk.contains("MHOOK_OK");
        log(p, ok ? "部署成功" : "部署失败（校验未通过）");
        return ok;
    }

    /** frida-server 进程 pid（空串表示未运行）。只取首个 pid，避免匹配到别的进程。 */
    public static String pid() {
        String out = su.getOutput("pidof frida-server");
        if (out == null) {
            out = "";
        }
        out = out.trim();
        if (!out.isEmpty()) {
            return out.split("\\s+")[0];
        }
        // 回退：按进程名精确匹配（不用 pgrep -f，否则会匹配到命令行里含该串的进程）
        out = su.getOutput("ps -A | grep -w frida-server | head -n 1");
        if (out == null) {
            out = "";
        }
        out = out.trim();
        if (out.isEmpty()) {
            return "";
        }
        for (String col : out.split("\\s+")) {
            if (col.matches("\\d+")) {
                return col;
            }
        }
        return "";
    }

    public static boolean isRunning() {
        return !pid().isEmpty();
    }

    /** 远端二进制是否存在且可执行。 */
    public static boolean isDeployed() {
        String chk = su.getOutput("test -x " + REMOTE_PATH + " && echo MHOOK_OK || echo MHOOK_NO");
        return chk != null && chk.contains("MHOOK_OK");
    }

    public static String remoteVersion() {
        String out = su.getOutput(REMOTE_PATH + " --version");
        return out == null ? "" : out.trim();
    }

    public static boolean start(Progress p) {
        if (!hasRoot()) {
            log(p, "无 root 权限");
            return false;
        }
        if (isRunning()) {
            log(p, "frida-server 已在运行 pid=" + pid());
            return true;
        }
        log(p, "启动 frida-server（127.0.0.1:" + DEFAULT_PORT + "）...");
        su.getOutput("nohup " + REMOTE_PATH + " -l 127.0.0.1:" + DEFAULT_PORT
                + " </dev/null >/dev/null 2>&1 &");
        for (int i = 0; i < 12; i++) {
            sleep(300);
            if (isRunning()) {
                log(p, "已启动 pid=" + pid());
                return true;
            }
        }
        log(p, "启动失败：未检测到进程");
        return false;
    }

    public static boolean stop(Progress p) {
        if (!isRunning()) {
            log(p, "frida-server 未在运行");
            return true;
        }
        log(p, "停止 frida-server ...");
        // killall 按进程名精确匹配（不经命令行，避免误杀）
        su.getOutput("killall -9 frida-server");
        for (int i = 0; i < 10; i++) {
            sleep(200);
            if (!isRunning()) {
                log(p, "已停止");
                return true;
            }
        }
        String one = pid();
        if (!one.isEmpty()) {
            su.getOutput("kill -9 " + one);
        }
        sleep(300);
        boolean stopped = !isRunning();
        log(p, stopped ? "已停止" : "停止失败");
        return stopped;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static void copy(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
            out.close();
        }
    }
}
