package cn.mhook.debug;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.Method;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 目标 APK 的类/方法索引（阶段 1：类名/方法名模糊搜索 → 精准 Hook）。
 * 用 dexlib2 静态解析 APK 内的 classes*.dex，结果缓存。
 */
public class ApkClassIndex {

    private final String apkPath;
    private List<String> classList;
    private final Map<String, List<String>> methodCache = new HashMap<String, List<String>>();

    public ApkClassIndex(String apkPath) {
        this.apkPath = apkPath;
    }

    public static String toDexType(String dotted) {
        if (dotted == null) {
            return "";
        }
        String s = dotted.trim();
        if (s.startsWith("L") && s.endsWith(";")) {
            return s;
        }
        return "L" + s.replace('.', '/') + ";";
    }

    public static String toDotted(String dexType) {
        if (dexType == null) {
            return "";
        }
        String s = dexType;
        if (s.length() > 2 && s.charAt(0) == 'L' && s.endsWith(";")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace('/', '.');
    }

    public synchronized List<String> allClasses() throws Exception {
        if (classList != null) {
            return classList;
        }
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        int dexCount = 0;
        Throwable lastErr = null;
        ZipFile zip = new ZipFile(apkPath);
        try {
            Enumeration<? extends ZipEntry> en = zip.entries();
            Opcodes op = Opcodes.forApi(33);
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.getName().endsWith(".dex")) {
                    continue;
                }
                dexCount++;
                InputStream in = new BufferedInputStream(zip.getInputStream(e));
                try {
                    DexBackedDexFile dex = DexBackedDexFile.fromInputStream(op, in);
                    for (DexBackedClassDef c : dex.getClasses()) {
                        set.add(toDotted(c.getType()));
                    }
                } catch (Throwable t) {
                    lastErr = t;
                } finally {
                    in.close();
                }
            }
        } finally {
            zip.close();
        }
        android.util.Log.i("ApkClassIndex", "dex=" + dexCount + " classes=" + set.size()
                + " err=" + lastErr);
        List<String> out = new ArrayList<String>(set);
        Collections.sort(out);
        classList = out;
        return out;
    }

    /** 模糊搜索类名（点分），最多 limit 个。 */
    public synchronized List<String> searchClasses(String keyword, int limit) throws Exception {
        String kw = keyword == null ? "" : keyword.trim();
        List<String> res = new ArrayList<String>();
        for (String t : allClasses()) {
            if (kw.isEmpty() || t.contains(kw)) {
                res.add(t);
                if (res.size() >= limit) {
                    break;
                }
            }
        }
        return res;
    }

    /** 列出指定类（点分）的方法名（去重）。 */
    public synchronized List<String> listMethods(String dottedClass) throws Exception {
        String key = dottedClass == null ? "" : dottedClass.trim();
        List<String> cached = methodCache.get(key);
        if (cached != null) {
            return cached;
        }
        String target = toDexType(key);
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        ZipFile zip = new ZipFile(apkPath);
        try {
            Enumeration<? extends ZipEntry> en = zip.entries();
            Opcodes op = Opcodes.forApi(33);
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.getName().endsWith(".dex")) {
                    continue;
                }
                InputStream in = new BufferedInputStream(zip.getInputStream(e));
                try {
                    DexBackedDexFile dex = DexBackedDexFile.fromInputStream(op, in);
                    for (DexBackedClassDef c : dex.getClasses()) {
                        if (c.getType().equals(target)) {
                            for (Method m : c.getMethods()) {
                                names.add(m.getName());
                            }
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    in.close();
                }
            }
        } finally {
            zip.close();
        }
        List<String> out = new ArrayList<String>(names);
        Collections.sort(out);
        methodCache.put(key, out);
        return out;
    }
}
