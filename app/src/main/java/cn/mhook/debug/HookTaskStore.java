package cn.mhook.debug;

import android.content.Context;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 断点/调试任务存储（阶段 3）：按目标包名保存 hook 任务列表，支持启停与批量注入。
 */
public class HookTaskStore {

    public static class Item {
        public String className = "";
        public String methodName = "";
        public String condition = "";
        public String overrideExpr = "";
        public boolean printArgs = true;
        public boolean printReturn = true;
        public boolean printStack = false;
        public boolean overrideReturn = false;
        public boolean overrideArgs = false;
        public String argIndex = "0";
        public String argValue = "";
        public boolean bypassDebug = false;
        public boolean enabled = true;

        public String title() {
            String c = className == null ? "" : className;
            int dot = c.lastIndexOf('.');
            if (dot >= 0 && dot < c.length() - 1) {
                c = c.substring(dot + 1);
            }
            return c + "#" + (methodName == null ? "" : methodName);
        }
    }

    private static File file(Context c, String pkg) {
        return new File(c.getFilesDir(), "hook_tasks_" + (pkg == null ? "unknown" : pkg) + ".json");
    }

    public static List<Item> load(Context c, String pkg) {
        List<Item> out = new ArrayList<Item>();
        try {
            File f = file(c, pkg);
            if (!f.isFile() || f.length() == 0) {
                return out;
            }
            byte[] d = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int off = 0;
            while (off < d.length) {
                int r = in.read(d, off, d.length - off);
                if (r < 0) {
                    break;
                }
                off += r;
            }
            in.close();
            JSONArray arr = JSON.parseArray(new String(d, 0, off, "UTF-8"));
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Item it = new Item();
                    it.className = o.getString("className");
                    it.methodName = o.getString("methodName");
                    it.condition = o.getString("condition");
                    it.overrideExpr = o.getString("overrideExpr");
                    it.printArgs = o.getBooleanValue("printArgs");
                    it.printReturn = o.getBooleanValue("printReturn");
                    it.printStack = o.getBooleanValue("printStack");
                    it.overrideReturn = o.getBooleanValue("overrideReturn");
                    it.overrideArgs = o.getBooleanValue("overrideArgs");
                    it.argIndex = o.getString("argIndex");
                    it.argValue = o.getString("argValue");
                    it.bypassDebug = o.getBooleanValue("bypassDebug");
                    it.enabled = o.getBooleanValue("enabled");
                    out.add(it);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void save(Context c, String pkg, List<Item> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Item it : list) {
                JSONObject o = new JSONObject(true);
                o.put("className", it.className);
                o.put("methodName", it.methodName);
                o.put("condition", it.condition);
                o.put("overrideExpr", it.overrideExpr);
                o.put("printArgs", it.printArgs);
                o.put("printReturn", it.printReturn);
                o.put("printStack", it.printStack);
                o.put("overrideReturn", it.overrideReturn);
                o.put("overrideArgs", it.overrideArgs);
                o.put("argIndex", it.argIndex);
                o.put("argValue", it.argValue);
                o.put("bypassDebug", it.bypassDebug);
                o.put("enabled", it.enabled);
                arr.add(o);
            }
            FileOutputStream fos = new FileOutputStream(file(c, pkg));
            fos.write(arr.toJSONString().getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
        }
    }

    public static void add(Context c, String pkg, Item it) {
        List<Item> list = load(c, pkg);
        list.add(it);
        save(c, pkg, list);
    }

    public static void remove(Context c, String pkg, int index) {
        List<Item> list = load(c, pkg);
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            save(c, pkg, list);
        }
    }

    public static void setEnabled(Context c, String pkg, int index, boolean enabled) {
        List<Item> list = load(c, pkg);
        if (index >= 0 && index < list.size()) {
            list.get(index).enabled = enabled;
            save(c, pkg, list);
        }
    }
}
