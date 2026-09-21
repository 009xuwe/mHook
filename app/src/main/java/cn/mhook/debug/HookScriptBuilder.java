package cn.mhook.debug;

import java.util.List;

/**
 * 调试任务脚本生成器（阶段 1/3）。
 * 支持单任务与多任务（断点列表）合并生成。
 *
 * 注意：gadget 通过 LD_PRELOAD 在进程极早期加载，此时 ART 未就绪，必须轮询等待 Java.available
 * 后再执行 Java.perform，否则 hook 不会安装。脚本注释一律用英文（中文注释会导致 frida 加载失败）。
 */
public class HookScriptBuilder {

    public static class Task {
        public String className = "";
        public String methodName = "";
        public boolean printArgs = true;
        public boolean printReturn = true;
        public boolean printStack = false;
        public boolean overrideReturn = false;
        public String overrideExpr = "";
        /** 参数篡改：把第 argIndex 个参数改成 argValue（JS 表达式），调用原方法前生效。 */
        public boolean overrideArgs = false;
        public String argIndex = "0";
        public String argValue = "";
        /** 条件断点表达式（可选），引用 arg0..arg4 或 args，例如：arg0 == 5 */
        public String conditionExpr = "";
        /** 反调试旁路 */
        public boolean bypassDebug = false;
    }

    public static String build(Task t) {
        return buildMulti(java.util.Collections.singletonList(t), t.bypassDebug);
    }

    /** 合并多个任务为一个脚本。 */
    public static String buildMulti(List<Task> tasks, boolean bypassDebug) {
        StringBuilder sb = new StringBuilder();
        sb.append("// mHook debug tasks: ").append(tasks.size()).append("\n");
        sb.append("var TAG = \"MHKDBG\";\n");
        sb.append("function log(s){ console.log(\"[MHOOK] \"+s); try{ Java.use(\"android.util.Log\").i(TAG, s); }catch(e){} }\n\n");
        sb.append("function install(){\n");
        sb.append("  Java.perform(function () {\n");
        if (bypassDebug) {
            sb.append("    try { Java.use(\"android.os.Debug\").isDebuggerConnected.implementation = function(){ return false; }; log(\"bypass: isDebuggerConnected -> false\"); } catch(e){}\n");
            sb.append("    try { Java.use(\"android.os.Debug\").waitForDebugger.implementation = function(){ log(\"bypass: waitForDebugger skipped\"); }; } catch(e){}\n");
        }
        for (Task t : tasks) {
            sb.append(hookBlock(t));
        }
        sb.append("  });\n");
        sb.append("}\n\n");
        sb.append("var _t=0,_tm=setInterval(function(){\n");
        sb.append("  _t++;\n");
        sb.append("  if(Java.available){ clearInterval(_tm); log(\"java ready, installing \" + ")
                .append(tasks.size()).append(" + \" hook(s)...\"); install(); }\n");
        sb.append("  else if(_t>=60){ clearInterval(_tm); }\n");
        sb.append("},200);\n");
        return sb.toString();
    }

    /** 单个任务的 hook 代码块（在 Java.perform 内执行）。 */
    private static String hookBlock(Task t) {
        final String CLASS = esc(t.className);
        final String METHOD = esc(t.methodName);
        final String COND = (t.conditionExpr == null) ? "" : t.conditionExpr.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("    try {\n");
        sb.append("      var CLASS = \"").append(CLASS).append("\";\n");
        sb.append("      var METHOD = \"").append(METHOD).append("\";\n");
        sb.append("      var clazz = Java.use(CLASS);\n");
        sb.append("      var overloads = clazz[METHOD].overloads;\n");
        sb.append("      var n = 0;\n");
        sb.append("      overloads.forEach(function (m) {\n");
        sb.append("        n++;\n");
        sb.append("        m.implementation = function () {\n");
        sb.append("          var self = this;\n");
        sb.append("          var args = Array.prototype.slice.call(arguments);\n");
        sb.append("          var arg0=args[0], arg1=args[1], arg2=args[2], arg3=args[3], arg4=args[4];\n");
        boolean cond = !COND.isEmpty();
        if (cond) {
            sb.append("          if (!(").append(COND).append(")) {\n");
            sb.append("            return m.call(self, ...args);\n");
            sb.append("          }\n");
        }
        if (t.printArgs) {
            sb.append("          var desc = args.map(function(a,i){ return \"arg\"+i+\"=\"+a; }).join(\", \");\n");
            sb.append("          log(\"-> \"+CLASS+\".\"+METHOD+\"(\"+desc+\")\");\n");
        } else {
            sb.append("          log(\"-> \"+CLASS+\".\"+METHOD+\"(\" + args.length + \" args)\");\n");
        }
        if (t.printStack) {
            sb.append("          try{ log(\"  stack: \"+Java.use(\"android.util.Log\").getStackTraceString(Java.use(\"java.lang.Throwable\").$new())); }catch(e){}\n");
        }
        if (t.overrideArgs && t.argValue != null && !t.argValue.trim().isEmpty()) {
            String idx = (t.argIndex == null || t.argIndex.trim().isEmpty()) ? "0" : t.argIndex.trim();
            sb.append("          try{ args[").append(idx).append("] = (").append(t.argValue.trim())
                    .append("); log(\"arg[").append(idx).append("] -> \" + args[").append(idx).append("]); }catch(e){ log(\"overrideArg err: \"+e); }\n");
        }
        if (t.overrideReturn && t.overrideExpr != null && !t.overrideExpr.trim().isEmpty()) {
            sb.append("          var _ov = (").append(t.overrideExpr.trim()).append(");\n");
            sb.append("          log(\"<- override return = \"+_ov);\n");
            sb.append("          return _ov;\n");
        } else {
            sb.append("          var ret = m.call(self, ...args);\n");
            if (t.printReturn) {
                sb.append("          log(\"<- \"+CLASS+\".\"+METHOD+\" ret=\"+ret);\n");
            }
            sb.append("          return ret;\n");
        }
        sb.append("        };\n");
        sb.append("      });\n");
        sb.append("      log(\"hooked \"+CLASS+\".\"+METHOD+\" (\"+n+\" overload(s))\");\n");
        sb.append("    } catch (e) {\n");
        sb.append("      log(\"hook failed: \" + \"").append(CLASS).append("#").append(METHOD).append(" -> \" + e);\n");
        sb.append("    }\n");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
