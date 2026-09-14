package cn.mhook.activity.dump;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemChildClickListener;
import com.chad.library.adapter.base.listener.OnItemChildLongClickListener;
import com.tamsiree.rxkit.RxActivityTool;
import com.tamsiree.rxkit.RxAppTool;
import com.tamsiree.rxkit.RxFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.mhook.activity.selectapp.SelectActivity;
import cn.mhook.activity.selectapp.SelectAppItem;
import cn.mhook.mhook.R;
import cn.mhook.widget.GlassToast;

import static cn.mhook.mData.mDir;
import static cn.mhook.msu.su.exec;
import static cn.mhook.msu.su.getOutput;
import static cn.mhook.msu.su.set777;

public class DumpActivity extends Activity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private Handler handler;
    private List<SelectAppItem> datas = new ArrayList<>();
    private DumpAdapter adapter;
    private FloatingSearchView floatingSearchView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump);
        handler = new Handler();
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        initListView();
        initAddBtn();
    }

    private void initAddBtn() {
        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle();
                bundle.putString("appType", "all");
                RxActivityTool.skipActivityForResult(DumpActivity.this, SelectActivity.class, bundle, 9008);
            }
        });
    }

    private void initListView() {
        recyclerView = findViewById(R.id.config_recycler_view);
        refreshLayout = findViewById(R.id.refresh_layout);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        refreshLayout.setRefreshing(true);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                initList("");
            }
        });
        initList("");
        adapter = new DumpAdapter(datas);
        adapter.setEmptyView(LayoutInflater.from(this).inflate(R.layout.view_empty, null));
        adapter.addChildClickViewIds(R.id.appInfoItem);
        adapter.addChildClickViewIds(R.id.item_dump_btn);
        adapter.addChildClickViewIds(R.id.item_dir_btn);
        adapter.addChildLongClickViewIds(R.id.appInfoItem);
        adapter.setOnItemChildClickListener(new OnItemChildClickListener() {
            @Override
            public void onItemChildClick(@NonNull BaseQuickAdapter adapter, @NonNull View view, int position) {
                final String pkg = datas.get(position).getPkg();
                if (view.getId() == R.id.item_dump_btn) {
                    dumpNow(pkg);
                    return;
                }
                if (view.getId() == R.id.item_dir_btn) {
                    openDumpDir(pkg);
                    return;
                }
                final boolean on = isDumpOn(pkg);
                new AlertDialog.Builder(DumpActivity.this)
                        .setTitle(on ? "关闭脱壳" : "开启脱壳")
                        .setMessage(on ? "关闭后将删除该应用的 dump 开关目录（已脱壳的 dex 也会被移除）"
                                : "开启后重启目标应用即自动脱壳。\n\ndex 文件保存位置：\n/data/mHook/" + pkg + "/dump/")
                        .setNegativeButton("取消", null)
                        .setPositiveButton(on ? "关闭" : "开启", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (setDump(pkg, !on)) {
                                    GlassToast.success(DumpActivity.this, on ? "已关闭脱壳" : "已开启脱壳，dex 保存到 /data/mHook/" + pkg + "/dump/，重启目标应用后生效");
                                } else {
                                    GlassToast.warning(DumpActivity.this, "开启失败：内存脱壳需要 root 权限写入 /data/mHook，当前设备似乎未授予 root");
                                }
                                initList("");
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        });
        adapter.setOnItemChildLongClickListener(new OnItemChildLongClickListener() {
            @Override
            public boolean onItemChildLongClick(@NonNull BaseQuickAdapter adapter, @NonNull View view, int position) {
                final String pkg = datas.get(position).getPkg();
                new AlertDialog.Builder(DumpActivity.this)
                        .setTitle("提示")
                        .setMessage("确定要移除该应用吗？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                RxFileTool.deleteDir(mDir + pkg + "/dump/");
                                initList("");
                                dialog.dismiss();
                            }
                        })
                        .create().show();
                return true;
            }
        });
        recyclerView.setAdapter(adapter);
        floatingSearchView = findViewById(R.id.floating_search_view);
        floatingSearchView.setOnQueryChangeListener(new FloatingSearchView.OnQueryChangeListener() {
            @Override
            public void onSearchTextChanged(String oldQuery, String newQuery) {
                initList(newQuery);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9008 && resultCode == RESULT_OK) {
            String pkg = data.getStringExtra("pkg");
            if (setDump(pkg, true)) {
                GlassToast.success(this, "已开启脱壳，重启目标应用后生效");
            } else {
                GlassToast.warning(this, "添加失败：内存脱壳需要 root 权限写入 /data/mHook，当前设备似乎未授予 root");
            }
            initList("");
        }
    }

    private static boolean isDumpOn(String pkg) {
        return new File(mDir + pkg + "/dump").exists();
    }

    /** 立即脱壳：向目标进程写 dump_now 触发文件，其脱壳线程会执行一次枚举。 */
    private void dumpNow(String pkg) {
        if (!isAppRunning(pkg)) {
            GlassToast.warning(this, "目标应用未运行，请先启动 " + pkg);
            return;
        }
        if (!isDumpOn(pkg)) {
            GlassToast.warning(this, "请先开启该应用的脱壳");
            return;
        }
        exec("mkdir -p '" + mDir + pkg + "' && chmod 777 '" + mDir + pkg
                + "' && echo 1 > '" + mDir + pkg + "/dump_now'");
        GlassToast.success(this, "已触发立即脱壳，稍候自动刷新");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initList("");
            }
        }, 3000);
    }

    /** 跳转脱壳目录：root 复制到 /sdcard/mHookDump/<pkg>/dump，再尝试用系统文件管理器打开。 */
    private void openDumpDir(final String pkg) {
        final File src = new File(mDir + pkg + "/dump");
        if (!src.exists()) {
            GlassToast.warning(this, "该应用脱壳目录不存在");
            return;
        }
        final File dst = new File(Environment.getExternalStorageDirectory(), "mHookDump/" + pkg + "/dump");
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    exec("rm -rf '" + dst.getAbsolutePath() + "'; mkdir -p '" + dst.getParentFile().getAbsolutePath()
                            + "'; cp -r '" + src.getAbsolutePath() + "' '" + dst.getAbsolutePath()
                            + "'; chmod -R 777 '" + dst.getAbsolutePath() + "'");
                    // app 进程可能无全盘存储权限看不到 /sdcard，用 su 视角判定导出结果
                    String cnt = getOutput("ls -A '" + dst.getAbsolutePath() + "' 2>/dev/null | wc -l");
                    ok = cnt != null && !cnt.trim().isEmpty() && !"0".equals(cnt.trim());
                } catch (Throwable t) {
                    ok = false;
                }
                final boolean result = ok;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!result) {
                            GlassToast.warning(DumpActivity.this, "导出失败：非 root 或目录为空");
                            return;
                        }
                        openDirIntent(pkg, dst);
                    }
                });
            }
        }).start();
    }

    private void openDirIntent(String pkg, File dir) {
        // 1) 系统文件管理器 documentsui 的目录 uri（Android 11+ 通用）
        String rel = "mHookDump/" + pkg + "/dump";
        Intent vi = new Intent(Intent.ACTION_VIEW);
        vi.setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"
                + Uri.encode(rel)), "vnd.android.document/directory");
        vi.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (vi.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(vi);
                return;
            } catch (Throwable ignored) {
            }
        }
        // 2) FileProvider 目录 uri
        try {
            Uri fp = FileProvider.getUriForFile(this, "cn.mhook.mhook.fileProvider", dir);
            Intent i2 = new Intent(Intent.ACTION_VIEW);
            i2.setDataAndType(fp, "resource/folder");
            i2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (i2.resolveActivity(getPackageManager()) != null) {
                startActivity(i2);
                return;
            }
        } catch (Throwable ignored) {
        }
        // 3) 无可用文件管理器：路径进剪贴板
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("mHookDump", dir.getAbsolutePath()));
        GlassToast.warning(this, "未找到可打开目录的文件管理器，路径已复制到剪贴板：" + dir.getAbsolutePath());
    }

    private boolean isAppRunning(String pkg) {
        try {
            return cn.mhook.msu.su.hasProcess(pkg);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean setDump(String pkg, boolean on) {
        String dir = mDir + pkg + "/dump";
        if (on) {
            // 用 su 同步创建并设 777，确保目标应用进程能写入脱壳 dex
            exec("mkdir -p '" + dir + "' && chmod 777 '" + dir + "'");
            new File(dir).mkdirs();
        } else {
            RxFileTool.deleteDir(dir);
        }
        try {
            set777();
        } catch (Throwable ignored) {
        }
        // 免root 设备无法写入 /data/mHook，目录创建失败时返回 false 以便提示
        return new File(dir).exists();
    }

    private void initList(final String query) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (datas.size() > 0) {
                    datas.clear();
                }
                java.util.List<File> fileList = RxFileTool.listFilesInDir(mDir, false);
                if (fileList != null) {
                    for (File file : fileList) {
                        String filePath = file.getPath();
                        if (RxFileTool.fileExists(filePath + "/dump")) {
                            String pkg = file.getName();
                            if (pkg.contains(query) || RxAppTool.getAppName(DumpActivity.this, pkg).contains(query)) {
                                datas.add(new SelectAppItem(pkg, RxAppTool.getAppVersionName(DumpActivity.this, pkg), RxAppTool.getAppName(DumpActivity.this, pkg), false));
                            }
                        }
                    }
                }
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                        refreshLayout.setRefreshing(false);
                    }
                }, 0);
            }
        }).start();
    }
}
