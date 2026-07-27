# R8 优化配置
# ============================================================

# --- R8 全局优化开关 ------------------------------------------
-allowaccessmodification
-optimizationpasses 3

# --- Gson 序列化 ----------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*

# 数据类（通过 Gson 反射序列化/反序列化）
-keep class com.haooz.chedule.data.Course { <fields>; }
-keep class com.haooz.chedule.data.TimeConfig { <fields>; }

# TypeToken 的匿名子类
# 需要保留构造方法供 Gson 反射实例化
-keep class * extends com.google.gson.reflect.TypeToken { <init>(); }
-keep class com.google.gson.reflect.TypeToken { *; }

# --- Compose --------------------------------------------------
# R8 兼容模式处理 Compose 内部，只保护本项目的 @Composable 入口点
-keepclassmembers class com.haooz.chedule.** {
    @androidx.compose.runtime.Composable <methods>;
}

# --- ViewModel ------------------------------------------------
# ViewModel 通过反射被 Android 框架重建，保留所有 ViewModel
-keep class com.haooz.chedule.viewmodel.* { *; }

# --- Kotlin ---------------------------------------------------
# Kotlin When 映射枚举，保留其字段供反射访问
-keepclassmembers class **$WhenMappings { <fields>; }

# --- 忽略 JVM-only API 缺失类警告----------------------------
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**

# --- JGit 运行时必需类 ------------------------------------------
# 教务导入功能使用 JGit 克隆/拉取 Gitee 仓库（HTTPS 协议）
# JGit 内部通过 ServiceLoader 和反射加载大量类，保守保留整个包
-keep class org.eclipse.jgit.** { *; }
-keep class com.googlecode.javaewah.** { *; }

# --- Shizuku 应用层代码 ---
# PrivilegedServiceImpl 通过 ComponentName(name) 绑定到 Shizuku 进程
# IPrivilegedService.Stub Binder 协议依赖类名不被混淆
-keep class com.haooz.chedule.shizuku.** { *; }

# --- 超级岛通知 ---
# isIslandSupported() 使用反射调用 android.os.SystemProperties
# sendIslandNotification() 构建 miui.focus.param 焦点通知 extras
-keep class com.haooz.chedule.reminder.IslandNotificationHelper { *; }
-keep class com.haooz.chedule.reminder.IslandExpandReceiver { *; }

# --- Shizuku 库 (consumer rules 已内置，安全兜底) ---
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn rikka.sui.**

# --- Focus API 库 (超级岛焦点通知，通过 extras 间接使用) ---
-keep class com.xzakota.hyper.notification.** { *; }
-dontwarn com.xzakota.hyper.notification.**

# --- 教务导入 Gson 数据类 ---
# 这些类通过 Gson 反射解析，字段名不可被混淆
-keep class com.haooz.chedule.ui.web.AndroidBridge { *; }
-keep class com.haooz.chedule.ui.web.AndroidBridge$* { <fields>; }
-keep class com.haooz.chedule.data.school.SchoolIndexData { <fields>; }
-keep class com.haooz.chedule.data.school.SchoolData { <fields>; }
-keep class com.haooz.chedule.data.school.AdapterData { <fields>; }
