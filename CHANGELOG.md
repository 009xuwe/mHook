# 更新日志

## [2.0.9] - 2026-09-23

### 修复
- 修复模块自身进程 hook 不存在的 `MainActivity.xp()` 导致 LSPosed 日志刷 `NoSuchMethodError` / `InvocationTargetException`
- 修复内存脱壳在 Android 16 上的闪退（日志重入保护 + 文件监控去重/噪声过滤）

### 新增
- 设置页新增「推介-智盾加固」（点击跳转 zdcod.com）
- frida 脚本输入增强（方法 Hook / Native 调试）：
  - 新增「导入文件 / 粘贴脚本 / 清空」与实时字符数
  - 「导入文件」限定文本类型 + 2MB 上限 + 二进制检测（防止误选 APK 等大文件导致崩溃）
  - 「粘贴脚本」直接读取系统剪贴板（绕过输入法，避免长脚本被截断）

### 优化
- 增加系统进程防护：跳过 system_server / SystemUI / phone，降低被勾入「系统框架」作用域时卡开机的风险

## [2.0.8] - 2026-09-21

### 新增
- 动态调试：主页新增「动态调试」入口（需 Root），集成 frida-server 托管（从 assets/frida 分发、su 部署到 /data/local/tmp/frida-server、自动拉起、状态面板）
- 分阶段调试：
  - 阶段0：frida-server 托管与设备状态
  - 阶段1：目标方法 Hook（选目标 App + 方法 → 生成/编辑/注入 frida 脚本，运行监控与返回值）
  - 阶段2：Native 调试（so 模块枚举 / 导出函数 / inline hook / 内存 / 反调试）
- 断点调试：BreakpointActivity（断点设置/命中日志）
- AI 驱动 frida：AI 会话新增 frida 工具集（查看状态 / 注入 gadget / 运行脚本 / 重启目标 / 取输出 / 清日志 / 列 so 模块 / 内存 dump dex）
