# R8 优化配置
# ============================================================
# 这是唯一生效的规则文件。
#
# 排查最终生效规则：app/build/outputs/mapping/release/configuration.txt
# 它按来源文件分段列出合并后的规则、各 AAR 自带规则、aapt 自动生成的清单组件规则。

# --- R8 全局优化开关 ------------------------------------------
-allowaccessmodification
-optimizationpasses 3

# --- Gson 序列化 ----------------------------------------------
# Signature：TypeToken 靠它保留泛型签名，缺了会丢类型
# *Annotation*：@SerializedName 靠它保留运行期注解
-keepattributes Signature
-keepattributes *Annotation*

# Gson 反射读写的数据类，字段名不可被混淆
-keep class com.haooz.chedule.data.Course { <fields>; }
-keep class com.haooz.chedule.data.TimeConfig { <fields>; }
# 搭配外观快照与特殊时段块（嵌套在 TimeConfig 内）
-keep class com.haooz.chedule.data.CombinationStyle { <fields>; }
-keep class com.haooz.chedule.data.SpecialBlock { <fields>; }
-keep class com.haooz.chedule.data.SpecialItem { <fields>; }
# Gson 枚举按 name() 写入 / valueOf() 读回
-keep class com.haooz.chedule.data.CardContentAlignment { <fields>; }
-keep class com.haooz.chedule.data.CardTextColor { <fields>; }
-keep class com.haooz.chedule.data.CardRefractionLevel { <fields>; }

# 以下三类不需要自己写规则，对应 AAR 已内置 consumer rules，重复声明只会额外
# 禁止重命名、削弱优化（已用真机验证）：
#   - gson:          -keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken
#   - lifecycle:     -keepclassmembers,allowobfuscation class * extends AndroidViewModel
#                    { <init>(android.app.Application); }
#   - AGP 默认:      -keepclassmembers enum * { values(); valueOf(); }
#   - AGP 默认:      -keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# --- Kotlin ---------------------------------------------------
# Kotlin when 映射枚举，保留其字段（Kotlin 2.4 仍会生成 $WhenMappings）
-keepclassmembers class **$WhenMappings { <fields>; }

# --- Shizuku（确需 keep：走反射调用）---------------------------
# ShizukuManager 用 Class.forName("rikka.shizuku.Shizuku")
#   .getDeclaredMethod("newProcess", ...) 反射调用，R8 无法解析该方法名
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn rikka.sui.**
# PrivilegedServiceImpl 通过 ComponentName(name) 绑定到 Shizuku 进程，
# IPrivilegedService.Stub 的 Binder 协议依赖类名
-keep class com.haooz.chedule.shizuku.** { *; }

# --- 超级岛通知 ---
# isIslandSupported() 反射调用 android.os.SystemProperties；
# sendIslandNotification() 构建 miui.focus.param 焦点通知 extras
-keep class com.haooz.chedule.reminder.IslandNotificationHelper { *; }
-keep class com.haooz.chedule.reminder.IslandExpandReceiver { *; }

# --- 教务导入 ---
# AndroidBridge 的 @JavascriptInterface 方法由 WebView 按方法名反射调用
# （AGP 默认规则已覆盖 @JavascriptInterface；这里保留是为了同时固定其内部
#   JsonModel 嵌套类的字段名，Gson 靠字段名反序列化）
-keep class com.haooz.chedule.ui.web.AndroidBridge { *; }
-keep class com.haooz.chedule.ui.web.AndroidBridge$* { <fields>; }
# 这些类通过 Gson 反射解析，字段名不可被混淆
-keep class com.haooz.chedule.data.school.SchoolIndexData { <fields>; }
-keep class com.haooz.chedule.data.school.SchoolData { <fields>; }
-keep class com.haooz.chedule.data.school.AdapterData { <fields>; }

# ============================================================
# 已删除的死规则（匹配不到任何类，删除后 APK 字节数不变，仅作记录避免被"补"回来）：
#   -keep class org.eclipse.jgit.**              JGit 已不在依赖里，代码 0 引用
#   -keep class com.googlecode.javaewah.**       同上
#   -dontwarn java.lang.management.**
#   -dontwarn javax.management.**                这三条原本只为 JGit 的 JDK 可选依赖
#   -dontwarn org.ietf.jgss.**
#   -keep class com.xzakota.hyper.notification.** 超级岛已改自建 miui.focus.param extras
#   -dontwarn com.xzakota.hyper.notification.**
# ============================================================
