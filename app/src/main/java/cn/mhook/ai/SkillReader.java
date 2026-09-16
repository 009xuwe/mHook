package cn.mhook.ai;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class SkillReader {

    private static final int MAX_SKILL_BYTES = 100 * 1024;

    /**
     * 列出全部可用技能：assets 内置技能 + 用户自定义技能，去重排序。
     * 用户不可修改内置 assets，但可用同名自定义技能覆盖它们（见 SkillStore）。
     */
    public static String[] listSkills(Context c) {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        String[] entries = null;
        try {
            entries = c.getAssets().list("skills");
        } catch (Throwable ignored) {
        }
        if (entries != null) {
            for (String e : entries) {
                if (hasSkill(c, e)) {
                    set.add(e);
                }
            }
        }
        for (String u : SkillStore.list(c)) {
            set.add(u);
        }
        List<String> out = new ArrayList<String>(set);
        Collections.sort(out);
        return out.toArray(new String[0]);
    }

    /**
     * 读取技能内容：用户自定义版本优先，其次 assets 内置版本。
     */
    public static String readSkill(Context c, String name) {
        if (name == null) {
            return null;
        }
        String user = SkillStore.read(c, name);
        if (user != null) {
            return truncate(user);
        }
        try {
            InputStream is = c.getAssets().open("skills/" + name + "/SKILL.md");
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                int total = 0;
                while ((n = is.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_SKILL_BYTES) {
                        bos.write(buf, 0, n - (total - MAX_SKILL_BYTES));
                        return bos.toString("UTF-8") + "\n...[技能文档过长已截断]";
                    }
                    bos.write(buf, 0, n);
                }
                return bos.toString("UTF-8");
            } finally {
                is.close();
            }
        } catch (Throwable e) {
            return null;
        }
    }

    /** 读取内置（assets）技能原文，未经过用户覆盖。 */
    public static String readBuiltin(Context c, String name) {
        if (name == null) {
            return null;
        }
        try {
            InputStream is = c.getAssets().open("skills/" + name + "/SKILL.md");
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                return bos.toString("UTF-8");
            } finally {
                is.close();
            }
        } catch (Throwable e) {
            return null;
        }
    }

    /** 是否为 assets 内置技能。 */
    public static boolean isBuiltin(Context c, String name) {
        return hasSkill(c, name);
    }

    private static String truncate(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() > MAX_SKILL_BYTES) {
            return content.substring(0, MAX_SKILL_BYTES) + "\n...[技能文档过长已截断]";
        }
        return content;
    }

    private static boolean hasSkill(Context c, String name) {
        try {
            InputStream is = c.getAssets().open("skills/" + name + "/SKILL.md");
            is.close();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
