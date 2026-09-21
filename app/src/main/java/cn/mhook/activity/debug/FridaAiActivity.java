package cn.mhook.activity.debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cn.mhook.ai.AiSession;
import cn.mhook.debug.FridaAiPrompt;
import cn.mhook.debug.FridaTools;
import cn.mhook.mhook.R;
import cn.mhook.widget.GlassToast;

/**
 * AI 调试助手：AI 通过内置 frida 工具直接注入/写脚本/读输出/迭代。
 */
public class FridaAiActivity extends Activity {

    private TextView aiAppName, aiOutput;
    private EditText aiInput;
    private String appPkg = "";
    private String appName = "";
    private boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai);
        aiAppName = findViewById(R.id.aiAppName);
        aiOutput = findViewById(R.id.aiOutput);
        aiInput = findViewById(R.id.aiInput);

        appPkg = getIntent().getStringExtra("pkg");
        appName = getIntent().getStringExtra("name");
        if (appPkg == null) {
            appPkg = "";
        }
        if (appName == null) {
            appName = "";
        }
        updateTitle();

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.aiClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aiOutput.setText("");
            }
        });
        findViewById(R.id.aiSelectAppCard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppPicker();
            }
        });
        findViewById(R.id.aiStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
        findViewById(R.id.aiStop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AiSession.stop();
                append("\n[已停止]\n");
            }
        });
    }

    private void updateTitle() {
        if (aiAppName != null) {
            aiAppName.setText(appPkg.isEmpty() ? "AI 调试助手（未选目标）"
                    : ("AI 调试助手 · " + appName + "（" + appPkg + "）"));
        }
    }

    private void send() {
        if (running) {
            GlassToast.info(this, "正在执行，请稍候");
            return;
        }
        final String req = aiInput.getText().toString().trim();
        if (req.isEmpty()) {
            return;
        }
        if (appPkg.isEmpty()) {
            GlassToast.warning(this, "请先选择目标应用");
            return;
        }
        FridaTools.setTarget(this, appPkg, appName);
        aiInput.setText("");
        running = true;
        append("\n\n> " + req + "\n");

        String system = FridaAiPrompt.build(this, appName + "（" + appPkg + "）");
        AiSession.run(this, system, req, new AiSession.Listener() {
            @Override
            public void onDelta(String text) {
                append(text);
            }

            @Override
            public void onReasoning(String text) {
            }

            @Override
            public void onToolEvent(String text) {
                append("\n" + text + "\n");
            }

            @Override
            public void onDone(String finalText) {
                running = false;
                append("\n");
            }

            @Override
            public void onError(Throwable t) {
                running = false;
                append("\n[错误] " + t + "\n");
            }
        });
    }

    private void append(final String s) {
        if (s == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                aiOutput.append(s);
                if (aiOutput.getParent() instanceof android.widget.ScrollView) {
                    ((android.widget.ScrollView) aiOutput.getParent()).fullScroll(View.FOCUS_DOWN);
                }
            }
        });
    }

    // ---- 应用选择 ----

    private static class AppInfo {
        String name;
        String pkg;
        Drawable icon;
    }

    private void showAppPicker() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> apps = loadInstalledApps();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (apps.isEmpty()) {
                            GlassToast.warning(FridaAiActivity.this, "未找到可启动的应用");
                            return;
                        }
                        new AlertDialog.Builder(FridaAiActivity.this)
                                .setTitle("选择目标应用（" + apps.size() + "）")
                                .setAdapter(new AppPickAdapter(apps), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        AppInfo a = apps.get(which);
                                        appPkg = a.pkg;
                                        appName = a.name;
                                        updateTitle();
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
        } catch (Throwable ignored) {
        }
        return out;
    }

    private class AppPickAdapter extends BaseAdapter {
        private final List<AppInfo> data;
        private final LayoutInflater inflater;

        AppPickAdapter(List<AppInfo> data) {
            this.data = data;
            this.inflater = LayoutInflater.from(FridaAiActivity.this);
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
}
