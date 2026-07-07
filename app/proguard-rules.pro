# R8 优化配置
# ============================================================

# --- R8 全局优化开关 ------------------------------------------
-allowaccessmodification

# --- Gson 序列化 ----------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*

# Course 类
-keep class com.haooz.chedule.data.Course { <fields>; }

# TypeToken 的匿名子类
# 需要保留构造方法供 Gson 反射实例化
-keep class * extends com.google.gson.reflect.TypeToken { <init>(); }
-keep class com.google.gson.reflect.TypeToken { *; }

# --- Compose --------------------------------------------------
# R8 兼容模式处理 Compose 内部，只保护 @Composable 入口点
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# --- ViewModel ------------------------------------------------
# ViewModel 通过反射被 Android 框架重建
-keep class com.haooz.chedule.viewmodel.CourseViewModel { *; }

# --- Kotlin ---------------------------------------------------
# Kotlin When 映射枚举，保留其字段供反射访问
-keepclassmembers class **$WhenMappings { <fields>; }

# --- 忽略 JVM-only API 缺失类警告------------
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**

# --- JGit 运行时必需类 ------------------------------------------
-keep class org.eclipse.jgit.** { *; }
-keep class com.googlecode.javaewah.** { *; }

# ============================================================
# 超级岛通知 & Shizuku
# ============================================================

# --- Shizuku 应用层代码 ---
# PrivilegedServiceImpl 通过 ComponentName(name) 绑定到 Shizuku 进程
# IPrivilegedService.Stub Binder 协议依赖类名不被混淆
-keep class com.haooz.chedule.shizuku.** { *; }

# --- 超级岛通知 ---
# isIslandSupported() 使用反射调用 android.os.SystemProperties
# sendIslandNotification() 构建 miui.focus.param 焦点通知 extras
-keep class com.haooz.chedule.reminder.IslandNotificationHelper { *; }
-keep class com.haooz.chedule.reminder.IslandExpandReceiver { *; }

# --- Shizuku 库 (安全兜底，即使库自带 consumer rules) ---
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn rikka.sui.**

# --- Focus API 库 (超级岛焦点通知) ---
-keep class com.xzakota.hyper.notification.** { *; }
-dontwarn com.xzakota.hyper.notification.**
