package cn.mhook.activity.debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.enums.SidePattern;
import com.lzf.easyfloat.interfaces.OnInvokeView;

import cn.mhook.debug.FridaAssetDownloader;
import cn.mhook.debug.FridaServerManager;
import cn.mhook.debug.GadgetManager;
import cn.mhook.mhook.R;
import cn.mhook.widget.GlassToast;

/**
 * 动态调试面板（阶段 0）：托管 frida-server 的部署 / 启停 / 状态。
 */
public class FridaDebugActivity extends Activity {

    private TextView tvStatus;
    private TextView tvLog;
    private TextView tvAppLog;
    private TextView tvTargetApp;
    private TextView tvTargetPkg;
    private ScrollView scrollLog;
    private ScrollView scrollOut;
    private String selectedPkg = "";
    private String selectedAppName = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;
    private View floatView;
    private android.view.WindowManager floatWm;

    private interface Task {
        void run() throws Throwable;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_frida_debug);
        tvStatus = findViewById(R.id.tv_status);
        tvAppLog = findViewById(R.id.tv_applog);
        tvTargetApp = findViewById(R.id.tv_target_app);
        tvTargetPkg = findViewById(R.id.tv_target_pkg);
        scrollLog = findViewById(R.id.scroll_log);
        scrollOut = findViewById(R.id.scroll_out);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvAppLog.setText("");
            }
        });
        findViewById(R.id.btn_inject).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String pkg = selectedPkg;
                if (pkg.isEmpty()) {
                    GlassToast.warning(FridaDebugActivity.this, "请先选择目标应用");
                    return;
                }
                runTask("注入 gadget → " + pkg, new Task() {
                    @Override
                    public void run() {
                        if (!GadgetManager.deploy(FridaDebugActivity.this, null, logger())) {
                            appendLog("部署失败，已中止");
                            return;
                        }
                        GadgetManager.inject(FridaDebugActivity.this, pkg, logger());
                        appendLog("提示：若目标 App 正在运行，请重启它使注入生效");
                    }
                });
            }
        });
        findViewById(R.id.btn_uninject).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String pkg = selectedPkg;
                if (pkg.isEmpty()) {
                    GlassToast.warning(FridaDebugActivity.this, "请先选择目标应用");
                    return;
                }
                runTask("取消注入 → " + pkg, new Task() {
                    @Override
                    public void run() {
                        GadgetManager.uninject(pkg, logger());
                    }
                });
            }
        });
        findViewById(R.id.btn_download_frida).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDownloadDialog();
            }
        });
        findViewById(R.id.btn_readout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readOutput();
            }
        });
        findViewById(R.id.btn_pick).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppPicker();
            }
        });
        findViewById(R.id.btn_task).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedPkg.isEmpty()) {
                    GlassToast.warning(FridaDebugActivity.this, "请先选择目标应用");
                    return;
                }
                android.content.Intent it = new android.content.Intent(
                        FridaDebugActivity.this, FridaTaskActivity.class);
                it.putExtra("pkg", selectedPkg);
                it.putExtra("name", selectedAppName);
                startActivity(it);
            }
        });
        findViewById(R.id.btn_native).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedPkg.isEmpty()) {
                    GlassToast.warning(FridaDebugActivity.this, "请先选择目标应用");
                    return;
                }
                android.content.Intent it = new android.content.Intent(
                        FridaDebugActivity.this, FridaNativeActivity.class);
                it.putExtra("pkg", selectedPkg);
                it.putExtra("name", selectedAppName);
                startActivity(it);
            }
        });

        findViewById(R.id.btn_ai_assist).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedPkg.isEmpty()) {
                    GlassToast.warning(FridaDebugActivity.this, "请先选择目标应用");
                    return;
                }
                android.content.Intent it = new android.content.Intent(
                        FridaDebugActivity.this, FridaAiActivity.class);
                it.putExtra("pkg", selectedPkg);
                it.putExtra("name", selectedAppName);
                startActivity(it);
            }
        });
        findViewById(R.id.btn_float).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (floatView != null) {
                        try {
                            floatWm.removeView(floatView);
                        } catch (Throwable ignored) {
                        }
                        floatView = null;
                        GlassToast.info(FridaDebugActivity.this, "已关闭悬浮窗");
                        return;
                    }
                    if (!android.provider.Settings.canDrawOverlays(FridaDebugActivity.this)) {
                        startActivity(new android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + getPackageName())));
                        GlassToast.info(FridaDebugActivity.this, "请授予悬浮窗权限后重试");
                        return;
                    }
                    floatWm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
                    floatView = getLayoutInflater().inflate(R.layout.float_status, null);
                    android.widget.TextView t = floatView.findViewById(R.id.float_target);
                    t.setText(selectedPkg.isEmpty() ? "未选择目标"
                            : (selectedAppName + " · " + selectedPkg));
                    int type = android.os.Build.VERSION.SDK_INT >= 26
                            ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : android.view.WindowManager.LayoutParams.TYPE_PHONE;
                    android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            type,
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            android.graphics.PixelFormat.TRANSLUCENT);
                    lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                    lp.x = 20;
                    lp.y = 300;
                    floatWm.addView(floatView, lp);
                    GlassToast.success(FridaDebugActivity.this, "悬浮窗已显示（透传，不挡操作）");
                } catch (Throwable t) {
                    GlassToast.warning(FridaDebugActivity.this, "悬浮窗失败：" + t.getMessage());
                }
            }
        });
        findViewById(R.id.btn_breakpoints).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedPkg.isEmpty()) {
                    GlassToast.warning(FridaDebugActivity.this, "请先选择目标应用");
                    return;
                }
                android.content.Intent it = new android.content.Intent(
                        FridaDebugActivity.this, BreakpointActivity.class);
                it.putExtra("pkg", selectedPkg);
                it.putExtra("name", selectedAppName);
                startActivity(it);
            }
        });

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void showDownloadDialog() {
        if (!FridaServerManager.isSupported()) {
            GlassToast.warning(this, "不支持的架构：" + FridaServerManager.abiDisplay());
            return;
        }
        final String abi = FridaServerManager.abiTag();
        final boolean sReady = FridaAssetDownloader.serverXzReady(this, abi);
        final boolean gReady = FridaAssetDownloader.gadgetXzReady(this, abi);
        final String[] items = new String[]{
                "全部下载（frida-server + gadget）" + (sReady && gReady ? "（均已存在）" : ""),
                "仅 frida-server" + (sReady ? "（已存在）" : ""),
                "仅 frida-gadget" + (gReady ? "（已存在）" : "")
        };
        new AlertDialog.Builder(this)
                .setTitle("下载 frida 组件 · " + abi + " · v" + FridaAssetDownloader.VERSION)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        downloadFrida(abi, which != 2, which != 1);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadFrida(final String abi, final boolean server, final boolean gadget) {
        runTask("下载 frida 组件(" + abi + ")", new Task() {
            @Override
            public void run() {
                boolean ok = true;
                if (server) {
                    appendLog("-- frida-server --");
                    File f = FridaAssetDownloader.download(FridaDebugActivity.this,
                            FridaAssetDownloader.serverUrl(abi),
                            FridaAssetDownloader.localServerXz(FridaDebugActivity.this, abi),
                            logger());
                    ok = f != null && ok;
                }
                if (gadget) {
                    appendLog("-- frida-gadget --");
                    File f = FridaAssetDownloader.download(FridaDebugActivity.this,
                            FridaAssetDownloader.gadgetUrl(abi),
                            FridaAssetDownloader.localGadgetXz(FridaDebugActivity.this, abi),
                            logger());
                    ok = f != null && ok;
                }
                appendLog(ok ? "组件已就绪：部署/注入时自动解压使用" : "部分组件未下载成功，请检查网络后重试");
            }
        });
    }

    private FridaServerManager.Progress logger() {
        return new FridaServerManager.Progress() {
            @Override
            public void onLog(String s) {
                appendLog(s);
            }
        };
    }

    private void runTask(final String name, final Task task) {
        if (busy) {
            GlassToast.info(this, "正在执行，请稍候");
            return;
        }
        busy = true;
        appendLog("==== " + name + " ====");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable t) {
                    appendLog("异常：" + t);
                } finally {
                    busy = false;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            refreshStatus();
                        }
                    });
                }
            }
        }).start();
    }

    private void refreshStatus() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder sb = new StringBuilder();
                boolean root = FridaServerManager.hasRoot();
                sb.append("Root 权限：").append(root ? "有" : "无（本模式需要 root）").append('\n');
                sb.append("CPU 架构：").append(FridaServerManager.abiDisplay())
                        .append("（对应 ").append(String.valueOf(FridaServerManager.abiTag())).append("）\n");
                File local = FridaServerManager.localBinary(FridaDebugActivity.this);
                boolean ex = FridaServerManager.isExtracted(FridaDebugActivity.this);
                sb.append("本地二进制：")
                        .append(ex ? ("已解压 " + (local.length() >> 20) + " MB") : "未解压")
                        .append('\n');
                String abi = FridaServerManager.abiTag();
                if (abi != null) {
                    boolean sxz = FridaAssetDownloader.serverXzReady(FridaDebugActivity.this, abi);
                    boolean gxz = FridaAssetDownloader.gadgetXzReady(FridaDebugActivity.this, abi);
                    sb.append("frida 组件：")
                            .append(sxz && gxz ? "已下载（server + gadget）"
                                    : sxz ? "仅 frida-server 已下载"
                                    : gxz ? "仅 frida-gadget 已下载"
                                    : "未下载，请点「下载 frida 组件」")
                            .append('\n');
                }
                if (root) {
                    boolean dep = FridaServerManager.isDeployed();
                    sb.append("远端部署：").append(dep ? FridaServerManager.REMOTE_PATH : "未部署").append('\n');
                    boolean run = FridaServerManager.isRunning();
                    sb.append("运行状态：").append(run
                            ? ("运行中 pid=" + FridaServerManager.pid()
                            + "，监听 127.0.0.1:" + FridaServerManager.DEFAULT_PORT)
                            : "未运行").append('\n');
                    if (dep) {
                        String v = FridaServerManager.remoteVersion();
                        if (!v.isEmpty()) {
                            sb.append("版本：").append(v).append('\n');
                        }
                    }
                }
                final String text = sb.toString().trim();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        tvStatus.setText(text);
                    }
                });
            }
        }).start();
    }

    private void updateTargetLabel(String pkg, String appName) {
        if (pkg == null || pkg.isEmpty()) {
            tvTargetApp.setText("点击选择目标应用");
            tvTargetApp.setTextColor(getResources().getColor(R.color.glass_text_primary));
            tvTargetPkg.setVisibility(View.GONE);
            updateFloatView();
            return;
        }
        tvTargetApp.setText(appName != null && !appName.isEmpty() ? appName : pkg);
        tvTargetApp.setTextColor(getResources().getColor(R.color.glass_accent_cyan));
        tvTargetPkg.setText(pkg);
        tvTargetPkg.setVisibility(View.VISIBLE);
        updateFloatView();
    }

    private void updateFloatView() {
        if (floatView == null) {
            return;
        }
        try {
            android.widget.TextView t = floatView.findViewById(R.id.float_target);
            if (t != null) {
                t.setText(selectedPkg.isEmpty() ? "未选择目标"
                        : (selectedAppName + " · " + selectedPkg));
            }
        } catch (Throwable ignored) {
        }
    }

    private static class AppInfo {
        String name;
        String pkg;
        Drawable icon;
    }

    private void showAppPicker() {
        appendLog("正在加载已安装应用 ...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> apps = loadInstalledApps();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (apps.isEmpty()) {
                            GlassToast.warning(FridaDebugActivity.this, "未找到可启动的应用");
                            return;
                        }
                        final AppPickAdapter adapter = new AppPickAdapter(apps);
                        new AlertDialog.Builder(FridaDebugActivity.this)
                                .setTitle("选择应用（" + apps.size() + "）")
                                .setAdapter(adapter, new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface d, int which) {
                                        AppInfo a = apps.get(which);
                                        selectedPkg = a.pkg;
                                        selectedAppName = a.name;
                                        updateTargetLabel(a.pkg, a.name);
                                        GlassToast.info(FridaDebugActivity.this, "已选择：" + a.name);
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> out = new ArrayList<AppInfo>();
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> list = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : list) {
                if (ai == null || ai.packageName == null) {
                    continue;
                }
                if (ai.packageName.equals(getPackageName())) {
                    continue;
                }
                // 只选可启动（有 launcher 入口）的应用
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) {
                    continue;
                }
                AppInfo a = new AppInfo();
                a.pkg = ai.packageName;
                try {
                    CharSequence label = pm.getApplicationLabel(ai);
                    a.name = label == null ? ai.packageName : label.toString();
                } catch (Throwable t) {
                    a.name = ai.packageName;
                }
                try {
                    a.icon = pm.getApplicationIcon(ai);
                } catch (Throwable t) {
                    a.icon = null;
                }
                out.add(a);
            }
            Collections.sort(out, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo x, AppInfo y) {
                    return x.name.compareToIgnoreCase(y.name);
                }
            });
        } catch (Throwable t) {
            appendLog("加载应用列表失败：" + t);
        }
        return out;
    }

    private class AppPickAdapter extends BaseAdapter {
        private final List<AppInfo> data;
        private final LayoutInflater inflater;

        AppPickAdapter(List<AppInfo> data) {
            this.data = data;
            this.inflater = LayoutInflater.from(FridaDebugActivity.this);
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_app_pick, parent, false);
            }
            AppInfo a = data.get(position);
            ImageView icon = convertView.findViewById(R.id.iv_app_icon);
            TextView name = convertView.findViewById(R.id.tv_app_name);
            TextView pkg = convertView.findViewById(R.id.tv_app_pkg);
            if (a.icon != null) {
                icon.setImageDrawable(a.icon);
            } else {
                icon.setImageResource(R.drawable.ic_xp);
            }
            name.setText(a.name);
            pkg.setText(a.pkg);
            return convertView;
        }
    }

    private void readOutput() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                // 主通道：logcat（root 读取，不依赖目标 App 的存储权限）
                try {
                    String lc = cn.mhook.msu.su.getOutput("logcat -d -s MHKDBG -t 800");
                    if (lc != null && !lc.trim().isEmpty()) {
                        for (String line : lc.split("\n")) {
                            int idx = line.indexOf("MHKDBG");
                            if (idx < 0) {
                                continue;
                            }
                            int c = line.indexOf(':', idx);
                            sb.append(c >= 0 ? line.substring(c + 1).trim() : line.trim()).append('\n');
                        }
                    }
                } catch (Throwable t) {
                    sb.append("logcat 读取失败：").append(t).append('\n');
                }
                // 次通道：脚本写出的日志文件（仅目标 App 有存储权限时存在）
                try {
                    File f = new File(GadgetManager.OUT_LOG);
                    if (f.exists() && f.length() > 0) {
                        int len = (int) Math.min(f.length(), 128 * 1024);
                        byte[] d = new byte[len];
                        FileInputStream in = new FileInputStream(f);
                        int off = 0;
                        while (off < len) {
                            int r = in.read(d, off, len - off);
                            if (r < 0) {
                                break;
                            }
                            off += r;
                        }
                        in.close();
                        int start = Math.max(0, off - 48 * 1024);
                        sb.append(new String(d, start, off - start, "UTF-8"));
                    }
                } catch (Throwable ignored) {
                }
                final String ft = sb.length() == 0
                        ? "(暂无输出)\n提示：先「注入」并重启目标 App，再点刷新"
                        : sb.toString();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        tvAppLog.setText(ft);
                    }
                });
            }
        }).start();
    }

    private void appendLog(final String s) {
        main.post(new Runnable() {
            @Override
            public void run() {
                tvAppLog.append(s + "\n");
                if (scrollOut != null) {
                    scrollOut.post(new Runnable() {
                        @Override
                        public void run() {
                            scrollOut.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        });
    }
}
