# 更新日志

## [2.0.9] - 2026-09-23

### 修复
- 修复模块自身进程 hook 不存在的 `MainActivity.xp()` 导致 LSPosed 日志刷 `NoSuchMethodError` / `InvocationTargetException`
- 修复内存脱壳在 Android 16 上的闪退（日志重入保护 + 文件监控去重/噪声过滤）

### 新增
- frida 脚本输入增强（方法 Hook / Native 调试）：
  - 新增「导入文件 / 粘贴脚本 / 清空」与实时字符数
  - 「导入文件」限定文本类型 + 2MB 上限 + 二进制检测（防止误选 APK 等大文件导致崩溃）
  - 「粘贴脚本」直接读取系统剪贴板（绕过输入法，避免长脚本被截断）

### 优化
- 增加系统进程防护：跳过 system_server / SystemUI / phone，降低被勾入「系统框架」作用域时卡开机的风险
