package cn.mhook.ai;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用户自定义技能存储。技能放在 &lt;filesDir&gt;/skills/&lt;name&gt;/SKILL.md，
 * 与 assets/skills 下的内置技能同名时以用户版为准（可覆盖/修改内置技能）。
 * 删除用户版即恢复为内置版本。
 */
public class SkillStore {

    public static final int MAX_SKILL_BYTES = 200 * 1024;

    public static File skillsDir(Context c) {
        File dir = new File(c.getFilesDir(), "skills");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** 用户自定义技能名列表（排序）。 */
    public static String[] list(Context c) {
        File[] fs = skillsDir(c).listFiles();
        if (fs == null) {
            return new String[0];
        }
        List<String> out = new ArrayList<String>();
        for (File f : fs) {
            if (f.isDirectory() && new File(f, "SKILL.md").isFile()) {
                out.add(f.getName());
            }
        }
        Collections.sort(out);
        return out.toArray(new String[0]);
    }

    public static boolean has(Context c, String name) {
        return name != null && new File(skillsDir(c), name + "/SKILL.md").isFile();
    }

    public static String read(Context c, String name) {
        if (!has(c, name)) {
            return null;
        }
        try {
            InputStream is = new FileInputStream(new File(skillsDir(c), name + "/SKILL.md"));
            try {
                return readAll(is);
            } finally {
                is.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean write(Context c, String name, String content) {
        if (!isValidName(name)) {
            return false;
        }
        try {
            File dir = new File(skillsDir(c), name);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(new File(dir, "SKILL.md"));
            try {
                fos.write((content == null ? "" : content).getBytes("UTF-8"));
            } finally {
                fos.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean delete(Context c, String name) {
        if (name == null) {
            return false;
        }
        try {
            File dir = new File(skillsDir(c), name);
            if (!dir.exists()) {
                return false;
            }
            File[] fs = dir.listFiles();
            if (fs != null) {
                for (File f : fs) {
                    f.delete();
                }
            }
            return dir.delete();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 技能名只允许字母/数字/连字符/下划线/点，且不能以点开头或包含 ..（防路径穿越）。 */
    public static boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        name = name.trim();
        if (name.isEmpty() || name.length() > 64) {
            return false;
        }
        if (name.startsWith(".") || name.contains("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_' || ch == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String readAll(InputStream is) throws Exception {
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
    }
}
