package cn.mhook.activity.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.mhook.ai.SkillReader;
import cn.mhook.ai.SkillStore;
import cn.mhook.mhook.R;
import cn.mhook.widget.GlassToast;

/**
 * 技能管理：列出全部技能（内置 + 自定义），支持新建、编辑（保存为自定义覆盖内置）、删除/恢复内置。
 */
public class SkillManageActivity extends Activity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skill_manage);
        list = findViewById(R.id.skill_list);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btn_new).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNewDialog();
            }
        });
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        String[] skills = SkillReader.listSkills(this);
        if (skills.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂无技能");
            empty.setTextColor(getResources().getColor(R.color.glass_text_tertiary));
            empty.setTextSize(13);
            empty.setPadding(dp(8), dp(16), dp(8), dp(8));
            list.addView(empty);
            return;
        }
        for (String name : skills) {
            list.addView(makeRow(name));
        }
    }

    private View makeRow(final String name) {
        final boolean builtin = SkillReader.isBuiltin(this, name);
        final boolean user = SkillStore.has(this, name);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        card.setPadding(dp(16), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(getResources().getColor(R.color.glass_text_primary));
        title.setTextSize(15);
        texts.addView(title);

        TextView status = new TextView(this);
        String s;
        if (builtin && user) {
            s = "内置技能 · 已自定义覆盖";
        } else if (builtin) {
            s = "内置技能";
        } else {
            s = "自定义技能";
        }
        status.setText(s);
        status.setTextColor(getResources().getColor(R.color.glass_text_tertiary));
        status.setTextSize(11);
        texts.addView(status);
        card.addView(texts);

        TextView edit = makeBtn("编辑", R.color.glass_accent_cyan);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditDialog(name);
            }
        });
        card.addView(edit);

        if (user) {
            TextView del = makeBtn("删除", R.color.glass_accent_red);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(name, builtin);
                }
            });
            card.addView(del);
        }
        return card;
    }

    private void showEditDialog(final String name) {
        String initial = SkillStore.has(this, name)
                ? SkillStore.read(this, name)
                : SkillReader.readBuiltin(this, name);
        if (initial == null) {
            initial = "";
        }
        final EditText et = makeEditor();
        et.setText(initial);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("编辑技能：" + name)
                .setView(et)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (SkillStore.write(SkillManageActivity.this, name, et.getText().toString())) {
                            GlassToast.success(SkillManageActivity.this, "已保存");
                            refresh();
                        } else {
                            GlassToast.warning(SkillManageActivity.this, "保存失败");
                        }
                    }
                })
                .setNegativeButton("取消", null);
        if (SkillStore.has(this, name) && SkillReader.isBuiltin(this, name)) {
            b.setNeutralButton("恢复内置", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    SkillStore.delete(SkillManageActivity.this, name);
                    GlassToast.success(SkillManageActivity.this, "已恢复内置版本");
                    refresh();
                }
            });
        }
        b.show();
    }

    private void showNewDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));

        final EditText name = new EditText(this);
        name.setHint("技能名（字母/数字/-/_/.）");
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        name.setTextColor(getResources().getColor(R.color.glass_text_primary));
        name.setHintTextColor(getResources().getColor(R.color.glass_text_tertiary));
        name.setTextSize(14);
        box.addView(name);

        final EditText content = makeEditor();
        content.setHint("SKILL.md 内容（AI 按需读取的说明/命令/模板）");
        box.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("新建技能")
                .setView(box)
                .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        if (!SkillStore.isValidName(n)) {
                            GlassToast.warning(SkillManageActivity.this, "名称非法：仅限字母/数字/-/_/.，不超过64字符");
                            return;
                        }
                        if (SkillStore.write(SkillManageActivity.this, n, content.getText().toString())) {
                            GlassToast.success(SkillManageActivity.this, "已创建");
                            refresh();
                        } else {
                            GlassToast.warning(SkillManageActivity.this, "创建失败");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(final String name, final boolean builtin) {
        new AlertDialog.Builder(this)
                .setTitle("删除技能")
                .setMessage(builtin ? "将删除自定义内容并恢复内置版本？" : "确定删除自定义技能「" + name + "」？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        SkillStore.delete(SkillManageActivity.this, name);
                        GlassToast.success(SkillManageActivity.this, builtin ? "已恢复内置版本" : "已删除");
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText makeEditor() {
        EditText et = new EditText(this);
        et.setMinLines(12);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setTextColor(getResources().getColor(R.color.glass_text_primary));
        et.setHintTextColor(getResources().getColor(R.color.glass_text_tertiary));
        et.setTextSize(12);
        return et;
    }

    private TextView makeBtn(String text, int colorRes) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getResources().getColor(colorRes));
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setBackgroundResource(R.drawable.bg_glass_btn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
