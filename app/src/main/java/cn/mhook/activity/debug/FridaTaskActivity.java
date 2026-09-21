package cn.mhook.activity.debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import cn.mhook.debug.ApkClassIndex;
import cn.mhook.msu.su;
import cn.mhook.debug.FridaServerManager;
import cn.mhook.debug.GadgetManager;
import cn.mhook.debug.HookScriptBuilder;
import cn.mhook.debug.HookTaskStore;
import cn.mhook.mhook.R;
import cn.mhook.widget.GlassToast;

/**
 * 调试任务（阶段 1）：选目标方法 + 观测选项 → 生成 frida 脚本（可编辑）→ 注入 → 看参数/返回值。
 */
public class FridaTaskActivity extends Activity {

    private TextView tvTarget, tvOut;
    private EditText etClass, etMethod, etOverride, etScript, etCondition, etArgIndex, etArgValue;
    private CheckBox cbArgs, cbRet, cbStack, cbOverride, cbBypass, cbOverrideArgs;
    private ScrollView scrollLog;
    private ScrollView scrollOut;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;
    private String pkg = "";
    private String appName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_frida_task);

        tvTarget = findViewById(R.id.tv_target);
        tvOut = findViewById(R.id.tv_out);
        etClass = findViewById(R.id.et_class);
        etMethod = findViewById(R.id.et_method);
        etOverride = findViewById(R.id.et_override);
        etScript = findViewById(R.id.et_script);
        etCondition = findViewById(R.id.et_condition);
        etArgIndex = findViewById(R.id.et_arg_index);
        etArgValue = findViewById(R.id.et_arg_value);
        cbArgs = findViewById(R.id.cb_args);
        cbRet = findViewById(R.id.cb_ret);
        cbStack = findViewById(R.id.cb_stack);
        cbOverride = findViewById(R.id.cb_override);
        cbBypass = findViewById(R.id.cb_bypass);
        cbOverrideArgs = findViewById(R.id.cb_override_args);
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
        findViewById(R.id.btn_gen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                genScript();
            }
        });

        findViewById(R.id.btn_save_bp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cls = etClass.getText().toString().trim();
                String mth = etMethod.getText().toString().trim();
                if (cls.isEmpty() || mth.isEmpty()) {
                    GlassToast.warning(FridaTaskActivity.this, "请先填类名和方法名");
                    return;
                }
                HookTaskStore.Item it = new HookTaskStore.Item();
                it.className = cls;
                it.methodName = mth;
                it.condition = etCondition.getText().toString().trim();
                it.overrideArgs = cbOverrideArgs.isChecked();
                it.argIndex = etArgIndex.getText().toString().trim();
                it.argValue = etArgValue.getText().toString().trim();
                it.overrideExpr = etOverride.getText().toString().trim();
                it.printArgs = cbArgs.isChecked();
                it.printReturn = cbRet.isChecked();
                it.printStack = cbStack.isChecked();
                it.overrideReturn = cbOverride.isChecked();
                it.bypassDebug = cbBypass.isChecked();
                it.enabled = true;
                HookTaskStore.add(FridaTaskActivity.this, pkg, it);
                appendLog("已保存断点：" + it.title());
                GlassToast.success(FridaTaskActivity.this, "已保存断点");
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
        findViewById(R.id.btn_search_class).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchClasses();
            }
        });
        findViewById(R.id.btn_list_method).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listMethods();
            }
        });
    }

    private ApkClassIndex index;

    private ApkClassIndex index() {
        if (index == null) {
            try {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
                String src = ai.sourceDir;
                // 普通 App 读不到别的 App 的 APK，需 root 复制到私有目录再解析
                File dst = new File(getFilesDir(), "target_" + Math.abs(pkg.hashCode()) + ".apk");
                if (!dst.isFile() || dst.length() == 0) {
                    appendLog("复制目标 APK（需 root）...");
                    su.getOutput("cp \"" + src + "\" \"" + dst.getAbsolutePath()
                            + "\" && chmod 644 \"" + dst.getAbsolutePath() + "\"");
                }
                if (dst.isFile() && dst.length() > 0) {
                    appendLog("APK: " + (dst.length() >> 20) + " MB");
                    index = new ApkClassIndex(dst.getAbsolutePath());
                } else {
                    index = new ApkClassIndex(src);
                }
            } catch (Throwable t) {
                appendLog("无法获取目标 APK：" + t);
            }
        }
        return index;
    }

    private void searchClasses() {
        final String kw = etClass.getText().toString().trim();
        runTask("搜索类", new Runnable() {
            @Override
            public void run() {
                final ApkClassIndex idx = index();
                if (idx == null) {
                    appendLog("索引不可用");
                    return;
                }
                try {
                    final List<String> res = idx.searchClasses(kw, 500);
                    appendLog("匹配类 " + res.size() + " 个" + (kw.isEmpty() ? "" : "（关键字：" + kw + "）"));
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            showListDialog("匹配类（" + res.size() + "）", res, etClass);
                        }
                    });
                } catch (Throwable t) {
                    appendLog("搜索失败：" + t);
                }
            }
        });
    }

    private void listMethods() {
        final String cls = etClass.getText().toString().trim();
        if (cls.isEmpty()) {
            GlassToast.warning(this, "请先填类名（或先搜索类）");
            return;
        }
        runTask("列方法", new Runnable() {
            @Override
            public void run() {
                final ApkClassIndex idx = index();
                if (idx == null) {
                    appendLog("索引不可用");
                    return;
                }
                try {
                    final List<String> res = idx.listMethods(cls);
                    appendLog(cls + " 的方法 " + res.size() + " 个");
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            showListDialog("方法（" + res.size() + "）", res, etMethod);
                        }
                    });
                } catch (Throwable t) {
                    appendLog("列方法失败：" + t);
                }
            }
        });
    }

    private void showListDialog(String title, final List<String> items, final EditText target) {
        if (items == null || items.isEmpty()) {
            GlassToast.warning(this, "无结果");
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, items);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setAdapter(adapter, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        target.setText(items.get(which));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private FridaServerManager.Progress logger() {
        return new FridaServerManager.Progress() {
            @Override
            public void onLog(String s) {
                appendLog(s);
            }
        };
    }

    private void genScript() {
        HookScriptBuilder.Task t = new HookScriptBuilder.Task();
        t.className = etClass.getText().toString().trim();
        t.methodName = etMethod.getText().toString().trim();
        if (t.className.isEmpty() || t.methodName.isEmpty()) {
            GlassToast.warning(this, "请先填写类名和方法名");
            return;
        }
        t.printArgs = cbArgs.isChecked();
        t.printReturn = cbRet.isChecked();
        t.printStack = cbStack.isChecked();
        t.overrideReturn = cbOverride.isChecked();
        t.overrideExpr = etOverride.getText().toString();
        t.conditionExpr = etCondition.getText().toString();
        t.overrideArgs = cbOverrideArgs.isChecked();
        t.argIndex = etArgIndex.getText().toString();
        t.argValue = etArgValue.getText().toString();
        t.bypassDebug = cbBypass.isChecked();
        etScript.setText(HookScriptBuilder.build(t));
        appendLog("已生成脚本（可编辑后注入）");
    }

    private void doInject() {
        if (pkg.isEmpty()) {
            GlassToast.warning(this, "无目标应用");
            return;
        }
        final String script = etScript.getText().toString();
        if (script.trim().isEmpty()) {
            GlassToast.warning(this, "脚本为空，先点「生成脚本」");
            return;
        }
        runTask("注入并运行", new Runnable() {
            @Override
            public void run() {
                if (!GadgetManager.deploy(FridaTaskActivity.this, script, logger())) {
                    appendLog("部署失败，已中止");
                    return;
                }
                GadgetManager.inject(FridaTaskActivity.this, pkg, logger());
                appendLog("请重启目标 App，再点「刷新输出」查看参数/返回值");
            }
        });
    }

    private void readOut() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String s = GadgetManager.readLogcatOutput();
                if (s == null || s.trim().isEmpty()) {
                    s = "(暂无输出)\n提示：重启目标 App 后点刷新";
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
