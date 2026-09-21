package cn.mhook.activity.debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.mhook.debug.FridaServerManager;
import cn.mhook.debug.GadgetManager;
import cn.mhook.debug.HookScriptBuilder;
import cn.mhook.debug.HookTaskStore;
import cn.mhook.mhook.R;
import cn.mhook.widget.GlassToast;

/**
 * 断点列表（阶段 3）：管理已保存的调试任务，勾选后一次性合并注入。
 */
public class BreakpointActivity extends Activity {

    private TextView tvTarget, tvOut;
    private LinearLayout bpList;
    private ScrollView scrollLog;
    private ScrollView scrollOut;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;
    private String pkg = "";
    private String appName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breakpoint);
        tvTarget = findViewById(R.id.tv_target);
        tvOut = findViewById(R.id.tv_out);
        bpList = findViewById(R.id.bp_list);
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
        findViewById(R.id.btn_inject_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                injectAll();
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

        refresh();
    }

    private void refresh() {
        bpList.removeAllViews();
        List<HookTaskStore.Item> list = HookTaskStore.load(this, pkg);
        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无断点。到「调试任务」里配置后点「保存为断点」。");
            empty.setTextColor(getResources().getColor(R.color.glass_text_tertiary));
            empty.setTextSize(12);
            empty.setPadding(dp(8), dp(8), dp(8), dp(8));
            bpList.addView(empty);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            bpList.addView(makeRow(list.get(i), i));
        }
    }

    private View makeRow(final HookTaskStore.Item it, final int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        CheckBox cb = new CheckBox(this);
        cb.setChecked(it.enabled);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                HookTaskStore.setEnabled(BreakpointActivity.this, pkg, index, checked);
            }
        });
        card.addView(cb);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t1 = new TextView(this);
        t1.setText(it.title());
        t1.setTextColor(getResources().getColor(R.color.glass_text_primary));
        t1.setTextSize(14);
        texts.addView(t1);
        TextView t2 = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (it.printArgs) sb.append("参数 ");
        if (it.printReturn) sb.append("返回值 ");
        if (it.printStack) sb.append("堆栈 ");
        if (it.overrideReturn) sb.append("篡改=").append(it.overrideExpr).append(" ");
        if (it.condition != null && !it.condition.trim().isEmpty()) sb.append("条件=").append(it.condition).append(" ");
        if (it.bypassDebug) sb.append("反调试 ");
        t2.setText(sb.toString().trim());
        t2.setTextColor(getResources().getColor(R.color.glass_text_tertiary));
        t2.setTextSize(11);
        texts.addView(t2);
        card.addView(texts);

        TextView del = new TextView(this);
        del.setText("删除");
        del.setTextColor(getResources().getColor(R.color.glass_accent_red));
        del.setTextSize(12);
        del.setPadding(dp(10), dp(6), dp(4), dp(6));
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(BreakpointActivity.this)
                        .setTitle("删除断点")
                        .setMessage(it.title())
                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                HookTaskStore.remove(BreakpointActivity.this, pkg, index);
                                refresh();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        card.addView(del);
        return card;
    }

    private void injectAll() {
        List<HookTaskStore.Item> list = HookTaskStore.load(this, pkg);
        final List<HookScriptBuilder.Task> tasks = new ArrayList<HookScriptBuilder.Task>();
        boolean bypass = false;
        for (HookTaskStore.Item it : list) {
            if (!it.enabled) {
                continue;
            }
            HookScriptBuilder.Task t = new HookScriptBuilder.Task();
            t.className = it.className;
            t.methodName = it.methodName;
            t.printArgs = it.printArgs;
            t.printReturn = it.printReturn;
            t.printStack = it.printStack;
            t.overrideReturn = it.overrideReturn;
            t.overrideExpr = it.overrideExpr;
            t.conditionExpr = it.condition;
            t.overrideArgs = it.overrideArgs;
            t.argIndex = it.argIndex;
            t.argValue = it.argValue;
            t.bypassDebug = it.bypassDebug;
            bypass = bypass || it.bypassDebug;
            tasks.add(t);
        }
        if (tasks.isEmpty()) {
            GlassToast.warning(this, "没有勾选的断点");
            return;
        }
        final boolean fbypass = bypass;
        runTask("全部注入（" + tasks.size() + " 个）", new Runnable() {
            @Override
            public void run() {
                String script = HookScriptBuilder.buildMulti(tasks, fbypass);
                if (!GadgetManager.deploy(BreakpointActivity.this, script, logger())) {
                    appendLog("部署失败，已中止");
                    return;
                }
                GadgetManager.inject(BreakpointActivity.this, pkg, logger());
                appendLog("请重启目标 App，再点「刷新输出」");
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

    private FridaServerManager.Progress logger() {
        return new FridaServerManager.Progress() {
            @Override
            public void onLog(String s) {
                appendLog(s);
            }
        };
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

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
