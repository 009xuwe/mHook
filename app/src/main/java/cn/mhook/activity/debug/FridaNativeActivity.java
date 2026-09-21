package cn.mhook.activity.debug;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.mhook.debug.FridaServerManager;
import cn.mhook.debug.GadgetManager;
import cn.mhook.debug.NativeScriptBuilder;
import cn.mhook.mhook.R;
import cn.mhook.widget.GlassToast;

/**
 * Native 调试（阶段 2）：so 枚举 / 导出符号 / inline hook / 内存搜索 / 反调试绕过。
 */
public class FridaNativeActivity extends Activity {

    private TextView tvTarget, tvOut;
    private EditText etModule, etSymbol, etScan, etScript, etSize, etFilter;
    private ScrollView scrollLog;
    private ScrollView scrollOut;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;
    private String pkg = "";
    private String filterMode = "all";
    private String appName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_frida_native);

        tvTarget = findViewById(R.id.tv_target);
        tvOut = findViewById(R.id.tv_out);
        etModule = findViewById(R.id.et_module);
        etSymbol = findViewById(R.id.et_symbol);
        etScan = findViewById(R.id.et_scan);
        etScript = findViewById(R.id.et_script);
        etSize = findViewById(R.id.et_size);
        etFilter = findViewById(R.id.et_filter);
        scrollLog = findViewById(R.id.scroll_log);
        scrollOut = findViewById(R.id.scroll_out);

        Intent it = getIntent();
        pkg = it.getStringExtra("pkg");
        appName = it.getStringExtra("name");
        if (pkg == null) {
            pkg = "";
        }
        tvTarget.setText(appName != null && !appName.isEmpty() ? (appName + "  ·  " + pkg) : pkg);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btn_modules).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gen(NativeScriptBuilder.listModules());
            }
        });
        findViewById(R.id.btn_exports).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String m = etModule.getText().toString().trim();
                if (m.isEmpty()) {
                    GlassToast.warning(FridaNativeActivity.this, "请先填模块名");
                    return;
                }
                gen(NativeScriptBuilder.listExports(m));
            }
        });
        findViewById(R.id.btn_hook_sym).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String m = etModule.getText().toString().trim();
                String s = etSymbol.getText().toString().trim();
                if (m.isEmpty() || s.isEmpty()) {
                    GlassToast.warning(FridaNativeActivity.this, "请填模块名和符号名");
                    return;
                }
                gen(NativeScriptBuilder.hookExport(m, s, true, true));
            }
        });
        findViewById(R.id.btn_hook_off).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String m = etModule.getText().toString().trim();
                String off = etSymbol.getText().toString().trim();
                if (m.isEmpty() || off.isEmpty()) {
                    GlassToast.warning(FridaNativeActivity.this, "请填模块名和偏移(0x..)");
                    return;
                }
                gen(NativeScriptBuilder.hookOffset(m, off));
            }
        });
        findViewById(R.id.btn_calls).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String m = etModule.getText().toString().trim();
                String sym = etSymbol.getText().toString().trim();
                if (m.isEmpty() || sym.isEmpty()) {
                    GlassToast.warning(FridaNativeActivity.this, "请填模块名和符号名");
                    return;
                }
                gen(NativeScriptBuilder.captureCalls(m, sym));
            }
        });
        findViewById(R.id.btn_hex).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String m = etModule.getText().toString().trim();
                if (m.isEmpty()) {
                    GlassToast.warning(FridaNativeActivity.this, "请填模块名");
                    return;
                }
                gen(NativeScriptBuilder.hexDump(m, etSymbol.getText().toString(), etSize.getText().toString()));
            }
        });
        findViewById(R.id.btn_memdiff).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String m = etModule.getText().toString().trim();
                if (m.isEmpty()) {
                    GlassToast.warning(FridaNativeActivity.this, "请填模块名");
                    return;
                }
                gen(NativeScriptBuilder.memDiff(m, etSymbol.getText().toString(), etSize.getText().toString()));
            }
        });
        findViewById(R.id.btn_export_json).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTask("导出调用日志JSON", new Runnable() {
                    @Override
                    public void run() {
                        GadgetManager.exportCallLogJson(pkg, logger());
                    }
                });
            }
        });
        findViewById(R.id.btn_mode_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode("all");
            }
        });
        findViewById(R.id.btn_mode_call).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode("call");
            }
        });
        findViewById(R.id.btn_mode_return).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode("return");
            }
        });
        findViewById(R.id.btn_mode_error).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode("error");
            }
        });
        findViewById(R.id.btn_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = etScan.getText().toString().trim();
                if (t.isEmpty()) {
                    GlassToast.warning(FridaNativeActivity.this, "请填搜索关键字");
                    return;
                }
                gen(NativeScriptBuilder.scanString(t, 100));
            }
        });
        findViewById(R.id.btn_bypass).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gen(NativeScriptBuilder.bypassAntiDebug());
            }
        });

        findViewById(R.id.btn_dump).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String m = etModule.getText().toString().trim();
                if (m.isEmpty()) {
                    GlassToast.warning(FridaNativeActivity.this, "请填模块名");
                    return;
                }
                gen(NativeScriptBuilder.dumpModule(m, etSymbol.getText().toString(),
                        etSize.getText().toString(), pkg));
            }
        });
        findViewById(R.id.btn_write).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String addr = etSymbol.getText().toString().trim();
                String hex = etScan.getText().toString().trim();
                if (addr.isEmpty() || hex.isEmpty()) {
                    GlassToast.warning(FridaNativeActivity.this, "写入需填地址(符号框)与字节(搜索框)");
                    return;
                }
                gen(NativeScriptBuilder.writeMemory(addr, hex));
            }
        });

        findViewById(R.id.btn_export_dump).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTask("导出 Dump", new Runnable() {
                    @Override
                    public void run() {
                        String dst = GadgetManager.exportDumps(pkg, logger());
                        if (dst != null) {
                            appendLog("完成：" + dst);
                        }
                    }
                });
            }
        });

        findViewById(R.id.btn_jdwp_on).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTask("开启 JDWP", new Runnable() {
                    @Override
                    public void run() {
                        GadgetManager.enableJdwp(pkg, logger());
                    }
                });
            }
        });
        findViewById(R.id.btn_jdwp_off).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTask("关闭 JDWP", new Runnable() {
                    @Override
                    public void run() {
                        GadgetManager.disableJdwp(logger());
                    }
                });
            }
        });
        findViewById(R.id.btn_dex_zip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTask("一键脱壳导出ZIP", new Runnable() {
                    @Override
                    public void run() {
                        GadgetManager.cleanDexDump(pkg);
                        String script = NativeScriptBuilder.dumpDex(pkg);
                        if (!GadgetManager.deploy(FridaNativeActivity.this, script, logger())) {
                            appendLog("部署失败，已中止");
                            return;
                        }
                        if (!GadgetManager.inject(FridaNativeActivity.this, pkg, logger())) {
                            appendLog("注入失败，已中止");
                            return;
                        }
                        appendLog("重启目标 App 触发脱壳 ...");
                        GadgetManager.restartApp(pkg);
                        // 把本页拉回前台，避免等待/打包时被系统后台限制
                        try {
                            cn.mhook.msu.su.getOutput("am start -n cn.mhook.mhook/cn.mhook.activity.debug.FridaNativeActivity"
                                    + " --es pkg " + pkg + " --es name " + (appName == null ? "" : appName));
                        } catch (Throwable ignored) {
                        }
                        appendLog("等待脱壳完成（App 会自动启动，轮询 dexdump）...");
                        int last = -1, stable = 0;
                        for (int i = 1; i <= 60; i++) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ignored) {
                            }
                            int cnt = GadgetManager.countDexDump(pkg);
                            if (cnt == 0 && i % 3 == 0) {
                                String raw = cn.mhook.msu.su.getOutput("ls /data/data/" + pkg + "/files/dexdump 2>&1");
                                appendLog("  diag: [" + (raw == null ? "null" : raw.trim()) + "]");
                            }
                            if (cnt > 0 && cnt == last) {
                                stable++;
                                if (stable >= 2) {
                                    appendLog("  dexdump 稳定在 " + cnt + " 个 dex");
                                    break;
                                }
                            } else {
                                stable = 0;
                            }
                            last = cnt;
                            if (i % 3 == 0 || cnt > 0) {
                                appendLog("  " + (i * 2) + "s: dexdump=" + cnt);
                            }
                        }
                        java.io.File zip = GadgetManager.packageDexDump(FridaNativeActivity.this, pkg, logger());
                        if (zip != null) {
                            appendLog("完成：" + zip.getAbsolutePath());
                        }
                    }
                });
            }
        });
        findViewById(R.id.btn_inject).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doInject();
            }
        });
        findViewById(R.id.btn_uninject).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTask("取消注入", new Runnable() {
                    @Override
                    public void run() {
                        GadgetManager.uninject(pkg, logger());
                    }
                });
            }
        });
        findViewById(R.id.btn_readout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readOut();
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

    private void gen(String script) {
        etScript.setText(script);
        appendLog("已生成脚本（可编辑后注入）");
    }

    private void doInject() {
        if (pkg.isEmpty()) {
            GlassToast.warning(this, "无目标应用");
            return;
        }
        final String script = etScript.getText().toString();
        if (script.trim().isEmpty()) {
            GlassToast.warning(this, "脚本为空，先点上面任一操作生成");
            return;
        }
        runTask("注入并运行", new Runnable() {
            @Override
            public void run() {
                if (!GadgetManager.deploy(FridaNativeActivity.this, script, logger())) {
                    appendLog("部署失败，已中止");
                    return;
                }
                GadgetManager.inject(FridaNativeActivity.this, pkg, logger());
                appendLog("请重启目标 App，等其启动完成后再点「刷新输出」");
            }
        });
    }

    private void setMode(String mode) {
        filterMode = mode;
        int on = getResources().getColor(R.color.glass_accent_cyan);
        int off = getResources().getColor(R.color.glass_text_secondary);
        ((android.widget.TextView) findViewById(R.id.btn_mode_all)).setTextColor("all".equals(mode) ? on : off);
        ((android.widget.TextView) findViewById(R.id.btn_mode_call)).setTextColor("call".equals(mode) ? on : off);
        ((android.widget.TextView) findViewById(R.id.btn_mode_return)).setTextColor("return".equals(mode) ? on : off);
        ((android.widget.TextView) findViewById(R.id.btn_mode_error)).setTextColor("error".equals(mode) ? on : off);
    }

    private void readOut() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String s = GadgetManager.readLogcatOutput(etFilter.getText().toString(), filterMode);
                if (s == null || s.trim().isEmpty()) {
                    s = "(暂无输出)\n提示：重启目标 App 并等启动完成后点刷新";
                }
                final String f = s;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        tvOut.setText(f);
                    }
                });
            }
        }).start();
    }

    private void runTask(final String name, final Runnable r) {
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
                    r.run();
                } catch (Throwable t) {
                    appendLog("异常：" + t);
                } finally {
                    busy = false;
                }
            }
        }).start();
    }

    private void appendLog(final String s) {
        main.post(new Runnable() {
            @Override
            public void run() {
                tvOut.append(s + "\n");
                if (scrollLog != null) {
                    scrollLog.post(new Runnable() {
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
