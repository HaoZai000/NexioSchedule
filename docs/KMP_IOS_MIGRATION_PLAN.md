# Nexio 课程表 · iOS（KMP）迁移计划

> 目标平台：**iOS**（iPad 一并覆盖）  
> 策略：渐进式，全程不打断 1.6.x 正常发版  
> 制定日期：2026-10-08 · 最近更新：2026-10-09（Kotlin 2.4.10，AGP 9.2.1，CMP 1.12.0）  
> 代码状态：`master` `b9ebd4b`，Android 侧 `assembleDebug` 通过；**iOS 尚未接入**

---

---

## 📍 当前进展（更新于 2026-10-09 · 已合入 master `b9ebd4b`）

> **安全网**：`master` 上打了永久标签 `backup/pre-merge-20261009`（合并前的状态）。
> 万一发现遗漏，`git branch <名字> backup/pre-merge-20261009` 即可恢复 —— 
> 标签不过期，比 reflog（90 天）可靠。

### 🔴 重要纠正：「:core 已 KMP 化」曾经只等于「Android/JVM 能编」

`:backdrop` / `:miuix` 有 `skikoMain`（走 jvm 目标），所以它们的 commonMain 是被验过的。
但 `:core` 此前**只有 android + jvm 两个 JVM 目标**，`compileCommonMainKotlinMetadata` 是
`SKIPPED` —— commonMain 从未被平台中立的 stdlib 检查过。后果是 4 个文件 7 处 JVM 专有 API
一路绿灯，**真正编 iOS 时会全部失败**：

| 位置 | 问题 |
|---|---|
| `AppStorage.kt` | `@Volatile`（靠 JVM 默认导入 `kotlin.jvm.*` 解析）、`synchronized` ×2 |
| `PlatformInfo.kt` / `StatsReporter.kt` | 同上 `@Volatile` |
| `Course.kt` | `System.currentTimeMillis()`、`String.format()`（**已合入 master 的遗留**） |
| `NoticeFetcher` / `AppreciationFetcher` / `StatsReporter` | `Dispatchers.IO` 在 Native 上是 `internal` |

**已全部修复**，并加了 `linuxX64` 编译门禁防止复发（详见「网络层专项」里的门禁小节）。
修复方式：`import kotlin.concurrent.Volatile`、copy-on-write 取代 `synchronized`、
`expect val ioDispatcher`（Native 用 `Dispatchers.Default`）、`Clock.System.now()`、
`padStart` 取代 `String.format`。

> 教训：**「能编过」必须明确是「哪个目标能编过」**。只要 commonMain 没有非 JVM 目标参与编译，
> 「KMP 化完成」就是没有依据的结论。

### 模块现状

| 模块 | 源文件 | 行数 | 源集 | 状态 |
|---|---:|---:|---|---|
| `:core` | 18 (+12 平台实现) | ~3,100 | common / android / **jvm / linuxX64(门禁)** | 数据层下沉 + 5 套跨平台抽象 |
| `:backdrop` | 64 | 5,458 | common / android / skiko | ✅ KMP 化，含 edgelight + capsule |
| `:miuix` | 103 | 26,464 | common / android / skiko | ✅ KMP 化，本轮新建 |
| `:app` | 154 | 70,559 | android | Android-only，**剩余迁移主体** |

编译验证：`core` / `backdrop` / `miuix` 的 `compileAndroidMain` + `compileKotlinJvm` 全绿，
`:app:assembleDebug` 通过（APK 19.83MB）。
> jvm target 复用 `skikoMain`，所以「jvm 编得过」= **SkSL 路径也编得过**。

### ✅ 本轮完成

**① `:miuix` 模块建立** —— Miuix fork 整体搬入，`git mv` 保证历史可读（108 个 rename）。
只 fork 了 `miuix-ui` 一个子库，其余（squircle / icons / blur / preference / navigation3-ui）
仍用官方 artifact。

**② edgelight + capsule 迁入 `:backdrop`** —— 纠正此前的错误结论：
**edgelight 不需要重写 911 行**，只需 3 处替换：
`BlurMaskFilter` → `paint.blur()`；`android.graphics.RuntimeShader` → backdrop 的 `RuntimeShader` 接口；
shader 的 `layout(color) half4` → `float4`（`layout(color)` 是 AGSL 专有修饰符）。

**③ 反向依赖全部解除**（`:miuix` / `:backdrop` 内零 `com.haooz` 引用）

| 手法 | 用于 | 例子 |
|---|---|---|
| CompositionLocal | App 的行为偏好，需根部注入 | `LocalChromeLensEnabled` / `LocalUseFakeProgressiveBlur` / `LocalPredictiveBackEnabled` |
| expect/actual | 平台存储 / 平台能力 | `isTabletWidth` / `isRenderEffectSupported` / `rememberAppSettingDark` / `rememberNavigationBack` |
| 接线层 | 逐调用点的 App 策略 | [EdgeLightBindings.kt](app/src/main/java/com/haooz/chedule/ui/utils/EdgeLightBindings.kt)，14 个调用点零改动 |

> `ProgressiveBlurTopBar` 有 29 个调用点 —— 走 CompositionLocal 后**一行调用点都不用改**。
> 遇到「调用点很多 + 值只有一个来源」时优先用 CompositionLocal。

### 🔴 本轮修掉的两个「编译全绿但功能失效」问题

这两类问题的共同点：**编译永远通过，只有真机能发现**。已写进 memory。

**1. OverlayDialog 的模糊与描边整体消失**
`:app` 里有**两个同名** `DialogContentLayout`：App 定制版（394 行，含 blur+edgeLight）与
上游 Miuix 版（411 行，纯色）。我搬走了上游那个，又在修参数报错时把
`liquidGlassBackdrop` / `isDark` / `enablePredictiveBackGesture` 当成「上游不需要」删了 ——
于是 App 那份带效果的实现变成零引用孤儿文件。影响 **71 个调用点**。

> 教训：改签名前先确认「当前文件是不是调用方原本用的那个」，
> 用 `git show HEAD:<调用方>` 看它 import 了谁，比看函数签名可靠。

**2. Android 预测性返回失效**
expect 封装写成了两个独立函数，各自调了一次 `rememberNavigationEventState()` ——
拿到**两个不同的 NavigationEventState**。handler 收到手势，但读进度的那个 state 恒为空，
跟手动画不触发。影响 3 个调用点（Dialog / BottomSheet / ListPopup）。

> 教训：抽象时不能改变「实例边界」。HEAD 是 1 个 state 喂两处，我改成 2 个 —— 就是 bug。
> 判断方法：改动前后数一数底层平台对象被创建了几次。

### ⚠️ 仍需真机确认

1. **描边亮度** —— `setColorUniform` 改用 `copy(alpha = 1f)`（alpha 已由
   `GraphicsLayer.alpha` 单独控制，原来相乘会偏暗）。但默认色是 `White.copy(alpha = 0.5f)`，
   **需确认描边是否偏亮**。
2. **对话框的模糊 / 描边 / 跟手返回** —— 上面两个 bug 修完后需回归确认。

### 📌 剩余工作量

- **`:app` 还有 574 处 Android 专用 import，分布在 115 个文件**（`android.*`、
  `androidx.core` / `navigationevent` / `activity` / `room` 等）—— 这是 iOS 迁移的主体。
- **阶段 2 日期迁移**：`:app` 尚余 34 处 `java.time` import
  （`TodayScreen` 5 / `Holidays` 5 / `SettingsScreen` 2 …）。逐个文件改 + 编译，
  **不要用正则批量改写**（曾破坏 lambda / when 分支 / `!` 优先级）。
- `Holidays.kt` 的调休 `followDate` 推算是全项目最敏感的部分，改它要逐点对照。
- iOS 工程接入需 macOS / `.konan`，本机无法编译验证。

---

**① `:core` KMP 模块已建立**（阶段 1 的第一批）
- `Course` / `ScheduleFolder` / `TimeConfig` / `CourseTimeResolver` / `PeriodTimeSource` 已下沉
- 包名保持 `com.haooz.chedule.data` 不变，所以 `:app` 侧 import 一行没改
- `:core` 的 `LocalDate` 用 kotlinx-datetime 0.6.2；`api(...)` 而非 `implementation`（否则下游看不到类型）
- 踩过的坑写在 `.workbuddy/memory/`，重点：`api` vs `implementation`、AGP 9 的 KMP 插件写法

**② `backdrop` 已升级为 KMP 模块**（原本是放在 `:app` 里的 Android-only fork）
- fork 自 `io.github.kyant0:backdrop:2.0.1`——**原库 2.x 本身就是 KMP 库**
  （1.x 是 Android-only，容易误判；务必确认最新版本再下结论）
- 源集分工：`commonMain`（上游 + 本项目定制）/ `skikoMain`（上游自带 SkSL）/ `androidMain`（原 fork 的实现，补 `actual`）
- Android 侧走的仍是你原来的实现，**行为不变**；iOS/Desktop 走 SkSL
- `assembleDebug` 通过，APK 正常产出

**③ 效果验证办法已就位（不用等 macOS）**
- 用 `ImageComposeScene`（Compose Desktop = 真 Skia 后端）离屏渲染，可截出 SkSL 路径的真实效果
- **desktop 与 iOS 共用同一份 `skikoMain` + `ImageFilter`**，所以桌面截图对 iOS 有直接参考价值
- 实测 `Highlight.Default` / `Ambient` / `lens+blur` 全部正常
- ⚠ Android 上**无法**临时切 SkSL：CMP 在 Android 就是 AndroidX Compose，Shader 必须是 `android.graphics.Shader`

**④ 关键前提已验证：kotlinx-datetime ≡ java.time**
- 11 个用例覆盖闰年/世纪年/跨年/取反优先级/Int 收窄边界，全部通过
- 结论：阶段 2 的日期替换是语义安全的
- ⚠ 该测试文件在提交前被删掉了（用户要求清理测试文件）。**做阶段 2 之前要按记忆里的对照表重新写回来**，
  否则日期算错没有安全网

### 修正过的判断

| 原判断 | 实际 |
|---|---|
| backdrop 是 Android-only，iOS 必须自己写 SkSL | 原库 2.0.1 已 KMP 化，SkSL 现成 |
| Miuix fork「改了很多」= 27,425 行 | 相对 KMP 版只差 3,179 行，50/80 文件完全相同 |
| edgelight 需要重写 911 行 | **只需 3 处替换**，backdrop 早已把 `RuntimeShader` / `paint.blur()` 做成跨平台 |
| 「0 处 app 引用 ⇒ 死代码」 | 会漏**模块内部**引用（`CascadingMorphContent` 就引用了被判定为死的文件） |

> **通用教训**：遇到「需要重写」「这是死代码」这类结论时，先把已有 expect/actual
> 和模块内部引用摊开看一遍。两处都曾因此判错并返工。

### ⚠️ 排查「静默失效」的两条经验

本轮迁移中，**两个功能失效的问题都是编译全绿、只有真机才能发现**的：

1. **同名文件**：`:app` 里两个 `DialogContentLayout`，搬错了那个 →
   71 个调用点的模糊与描边消失。改签名前先 `git show HEAD:<调用方>` 确认它 import 的谁。
2. **实例边界被抽象改变**：expect 封装把「1 个 state 喂两处」拆成「2 个函数各自 remember」
   → 预测性返回进度恒为空。改动前后数一数底层平台对象被创建了几次。

配套的自查手段：找「参数声明了但从未向下传递」的断链 —— 但这类脚本**误报率高**
（局部 `val` 中转、位置传参都会误判），结论必须逐个人工确认。
- Android Studio 已在工作区删过两次文件（341 / 602 个），恢复见 memory。**提交前先关 IDE**

---

## 一、先认清 iOS 迁移的本质



iOS 走的是 **Kotlin/Native**，不是 JVM。这一条决定了所有工作量：

| 能力                    | Android / JVM       | iOS (Kotlin/Native)      |
| --------------------- | ------------------- | ------------------------ |
| Gson（运行时反射）           | ✅                   | ❌ **不存在**，必须换            |
| `java.time.*`         | ✅                   | ❌ 不可用，换 kotlinx-datetime |
| OkHttp                | ✅                   | ⚠️ 有 Darwin 引擎，可用但需验证    |
| 文件 IO（`java.io.File`） | ✅                   | ⚠️ 需 expect/actual       |
| 精确闹钟（AlarmManager）    | ✅ `setAlarmClock`   | ❌ **完全不存在**              |
| 后台执行代码                | ✅ BroadcastReceiver | ❌ **不存在**                |
| 控制系统免打扰               | ✅                   | ❌ **系统禁止**               |
| 桌面小部件                 | ✅ AppWidget         | ⚠️ WidgetKit，须 Swift 重写  |
| 系统壁纸                  | 未使用（仅 App 内背景）      | ✅ 不受影响                   |

**两个必须提前接受的事实：**

1. **UI 只能走 Compose Multiplatform。** 61.7k 行 Compose UI 不可能用 SwiftUI 重写一遍——那不叫迁移，那叫重写第二个 App。CMP 在 iOS 上用 Skia 自绘，包名仍是 `androidx.compose.*`，现有 import 大多数不用改。
2. **提醒功能会实质性降级。** `reminder/`（4,463 行）整个建立在 `AlarmManager.setAlarmClock` + `BroadcastReceiver` 之上，这套东西 iOS 一个都没有。iOS 只能预约本地通知，且**待触发通知上限 64 条**。这是 iOS 版最硬的架构约束，不是靠写代码能绕过去的。

---

## 二、iOS 功能落差清单（先定哪些能要，哪些不能要）

| 功能                      | Android 实现                       | iOS 方案                                        | 结论         |
| ----------------------- | -------------------------------- | --------------------------------------------- | ---------- |
| 上课/下课提醒                 | AlarmManager + 精确闹钟              | 本地通知 `UNCalendarNotificationTrigger`，**滚动预约** | ⚠️ 降级      |
| 课前提醒（提前 N 分钟）           | 同上                               | 同上，占用通知配额                                     | ⚠️ 降级      |
| 上课自动免打扰（ClassDndHelper） | AudioManager / 通知策略              | **系统禁止第三方控制**                                 | ❌ 砍掉       |
| 下课烟花 / 常驻倒计时            | 前台计算                             | CMP 内可算；App 退后台后**无法自动触发**                    | ⚠️ 仅前台     |
| 桌面小部件                   | AppWidget（7 文件 1,507 行）          | WidgetKit（Swift 重写）                           | 🔶 延后，单独排期 |
| 课表/备份导入导出               | 文件 + FileProvider                | `UIDocumentPicker` + 沙盒                       | ✅ 需平台代码    |
| 壁纸 / 主题外观               | App 内背景（非系统壁纸）                   | 纯 CMP，不受限                                     | ✅ 直接复用     |
| 教务导入                    | WebView + `@JavascriptInterface` | WKWebView + `WKScriptMessageHandler`          | 🔶 桥接层重写   |
| Shizuku / 小米穿戴          | AIDL / 穿戴 SDK                    | 无对应                                           | ❌ 砍掉       |
| 分享码 / 云同步               | OkHttp + 服务端                     | 网络层可复用                                        | ✅          |
| 平板双栏布局                  | `LocalConfiguration` 判定          | `ScreenInfo` expect/actual                    | ✅ 需改造      |

**结论：iOS 版能拿到约 80% 的功能，砍掉的是"系统级控制"类（免打扰、精确闹钟、小部件、穿戴）。** 这些恰好都是 Android 独占能力，与代码质量无关。

---

## 三、目标架构

```
:core                        KMP 共享模块
  commonMain/                领域模型 · 时间计算 · 节假日 · 课程仓储 · 备份序列化
  commonMain/platform/       expect：KeyValueStore / FileStore / Notifier /
                             ScreenInfo / Haptics / WebBridge / Toast
  androidMain/               actual：SharedPreferences / AlarmManager / Notification
  iosMain/                   actual：NSUserDefaults / UserNotifications / UIDocumentPicker

:ui-shared                   CMP 共享 UI（阶段 5 才建）
  commonMain/                屏幕 · 组件 · 主题
  androidMain/iosMain/       backdrop 的 actual（Android 走 RenderEffect，iOS 走 Skia）

:app                         Android 壳（保留 reminder / widget / shizuku / wearable）
:iosApp                      Xcode 工程（SwiftUI/UIKit 壳 + CMP 内容）
```

**分层铁律：** `:core` 内禁止出现 `android.*`、`java.io.File`、`java.time.*`、反射式序列化。`androidx.compose.*` 允许。

---

## 四、分阶段路线

### 阶段 0 · 技术预研与地基 ✅ 三项已完成（详见开头「当前进展」）

这一步不做完，后面所有排期都是猜测。四件事，已完成三件：

**✅ 验证 1：backdrop 在 iOS 上能否复现 —— 能，而且不用我们做任何事**

原判断「backdrop 重度依赖 AGSL、iOS 的 Skia 后端没有对应物、必须先写 demo 验证」**是错的**：

- 上游是 `io.github.kyant0:backdrop`（仓库 `Kyant0/AndroidLiquidGlass`）
- **已有现成 KMP 移植** `promicx/KMPLiquidGlass`：
  Android 走 RenderEffect（API 31+）/ AGSL（API 33+），
  iOS / Desktop / Web 走 **SkSL**（Skia RuntimeEffect + ImageFilter）。
  其 README 写明「Both implementations produce visually equivalent results」。
- 本地 fork 的 `com/kyant/backdrop`（5,184 行）是 Android-only 快照，**迁移时直接换依赖**，
  只需把 fork 的定制改动搬到 KMP 版，不需要手写 SkSL。

**✅ 验证 1b：Miuix fork 的改动量 —— 实测 3,179 行，不是 27,425 行**

Miuix 官方也是完整 KMP 库（有 `miuix-core-iosarm64` / `miuix-blur-iosarm64` / `miuix-desktop` 等 artifact）。
拿上游 `miuix-ui:0.9.3` 的 commonMain（82 文件）与本地 fork（90 文件）逐文件 diff，结果：

| 项 | 数量 |
|---|---:|
| 完全相同（可直接用官方 artifact） | **50 文件** |
| 有改动 | 30 文件 |
| 仅本地有（定制新增） | 10 文件 |
| 仅上游有 | 2 文件 |
| **总差异行数** | **3,179** |

改动集中的几个大件：`ListPopup.kt` 683→1429、`NavigationRail.kt` 640→296、
`DynamicColors.kt` 10→337、`SearchBar.kt` 331→501、`ListPopupLayout.kt` 262→438。

而且**这些改动本身几乎没有 Android 耦合**：核心文件里 `NumberPicker` / `Dropdown` / `Button` /
`Slider` / `NavigationRail` / `LiquidOverlayDropdownPopup` 的 Android-only import 均为 **0**。
全项目合计只有约 15 处，主要是：

- `androidx.navigationevent.*`（4 处，`SearchBar` 的返回处理，CMP 无对应 → 需抽象）
- `android.graphics.BlurMaskFilter` / `Paint` / `Color.parseColor`（约 5 处 → CMP 有替代）
- `android.os.Build` / `android.provider.Settings` / `android.util.Log` / `Context`（少量）

→ **结论：Miuix 迁移 = 把 3,179 行定制挪到官方 KMP artifact 上，再抽掉约 15 处平台 API。**
不是重写 27,425 行。风险远低于预估。

**✅ 验证 2：kotlinx-datetime 与 java.time 的等价性 —— 已完成**

已在 `:core` 建立 `jvmTest`，同一批日期（闰年、世纪年、跨年、月末）同时喂给两套 API 断言等价，
11 个用例全部通过，覆盖 `parse` / 构造 / `plus(n,unit)` / `daysUntil` / `isoDayNumber` / `monthNumber` /
`toEpochDays` / `fromEpochDays` / 比较运算与取反优先级 / `lengthOfMonth` / `todayIn` / Int 收窄边界。

**结论：替换是语义等价的，日期迁移可以放心做。** 每改一处日期代码都跑 `./gradlew :core:jvmTest`。

**✅ 验证 3：真正的 Android-only 绘制代码只占 2,756 行**

不是 61.7k 行 UI，而是其中的自研特效：

| 子包 | 行数 | 依赖 | iOS 结论 |
|---|---:|---|---|
| `edgelight/` | 911 | ~~`android.graphics.RuntimeShader`~~ | ✅ **已迁入 `:backdrop` commonMain**（非重写） |
| `miuix/` | 743 | `Context` / `ActivityManager` / `navigationevent` | ✅ **已作为 `:miuix` 模块搬迁**，navigationevent 收成 expect |
| `liquidglass/` | 510 | backdrop 的 RuntimeShader | ✅ `InteractiveHighlight` 等已入 `:miuix` |
| `background/` | 344 | GLSL + `top.yukonga.miuix.kmp.blur.RuntimeShader` | Miuix 是 KMP 库，换 artifact 即可 |

> **⚠️ 本行结论已被实测推翻**（保留原判断以免后人重犯）：
> 曾判断「edgelight 需要重写 911 行」。实际迁入只做了 3 处替换，各 3 行：
> `BlurMaskFilter` → backdrop 的 `paint.blur()`；`android.graphics.RuntimeShader` →
> backdrop 的 `RuntimeShader` 接口；shader 的 `layout(color) half4` → `float4`。
> **教训**：遇到「需要重写」结论时，先把已有 expect/actual 摊开看一遍 ——
> backdrop 早就把这三个能力做成了跨平台的。

**edgelight 是 UI 共享的唯一真实阻塞点**：它直接用 Android 13 原生的 `android.graphics.RuntimeShader`，
CMP 无对应物，且用得很广（顶栏按钮、下拉菜单、底部 Tab、DayColumn、回到今天悬浮按钮、平板侧边栏…）。

补充查证（比原估计乐观）：源库里没有叫 edgelight 的东西，对应的是 backdrop 的 **`highlight`**，
两者**同为 AGSL**，且项目的 shader 是从 backdrop **复制粘贴**来的 ——
`EdgeLightShaders.kt` 的 `RoundedRectSDF` 与 backdrop 逐行相同，
`EdgeLightStyle.Directional(angle, falloff)` 的 shader 与上游 `DefaultHighlightShaderString` 几乎逐行一致。
差别只有三处：多一个 `Glow` 样式（上游没有）、多 `width`/`blurRadius` 参数、用原生 RuntimeShader 而非 Compose 的。

所以实际工作量是：
- `Directional` → KMP 移植版的 highlight 已用 SkSL 实现过，**可直接复用/对照**
- `Glow` → 需自己写一个 SkSL（外发光 = 模糊描边，逻辑简单）
- `Uniform` → 无 shader，降级路径已存在
- 若不想等：`isRuntimeShaderSupported()` 守卫会让 iOS 自动走 `Uniform`，先降级上线也行

**⬜ 验证 4（未做）：iOS 通知配额下的滚动预约策略**
64 条上限 vs 一学期几百节课：
- 只预约未来 N 天内的前 64 条，App 每次前台启动时补充；
- 课表变更时批量重算（取消全部 + 重新预约）；
- 验证 `UNCalendarNotificationTrigger` 在跨夏令时/跨年的行为。

---

### 阶段 1 · 纯逻辑下沉（1~2 周）🟡 进行中

**目标：** 建 `:core`，搬最干净的 5 个文件，`:app` 行为零变化。

**迁移清单（共 1,411 行，Android 依赖为 0）：**

| 文件                            |  行数 | 需处理                                  |
| ----------------------------- | --: | ------------------------------------ |
| `Course.kt`                   | 276 | 无                                    |
| `ScheduleFolder.kt`           |  13 | 无                                    |
| `CourseTimeResolver.kt`       |  54 | 无                                    |
| `CourseScheduleDateBounds.kt` | 314 | `java.time.LocalDate` / `ChronoUnit` |
| `TimeConfig.kt`               | 754 | `java.time.LocalDate`                |

**顺手做掉：** 上面两处 `LocalDate` 直接换成 `kotlinx.datetime.LocalDate`。只有 2 个文件 2 种类型，现在换比阶段 2 换便宜一个数量级，且阶段 0 验证 2 已经铺好路。

**验收：**

- `./gradlew :app:assembleDebug` 通过
- 用同一份真实备份文件，迁移前后对比课程时间、周次、节假日判定，**逐条一致**
- Kotlin 版本保持 2.4.10 不动

**风险：极低。** 不触碰任何持久化格式。

---


### 阶段 2 · 时间与序列化换血（3~4 周）

**目标：** 让 `:core` 具备上 Kotlin/Native 的资格。全计划中技术决策最密集的一步。

**2.1 `java.time` → `kotlinx-datetime`（22 个文件，阶段 1 已做 2 个）**

重点盯 `SimpleDateFormat` 的每个调用点，逐个写对照用例验证输出一致。

**2.2 Gson 分层替换 —— 不要全局无脑替换**

Gson 靠运行时反射，KN 上不存在。但更要命的是**架构依赖**：

- 全量备份走 `exportAllPreferences()` 的 **prefs 级透传**，导入侧 `gson.toJson → Map` 自动展开，**两端都不写字段清单**；
- 单课表备份、分享码、教务导入 4 条通道依赖同样的"反射自动展开"。

| 通道                                     | 处理                                                       |
| -------------------------------------- | -------------------------------------------------------- |
| 跨平台数据模型（Course / TimeConfig / Holiday） | `@Serializable` + `@SerialName`，字段清单显式化                  |
| 全量备份（prefs 级透传）                        | **保留 Gson，留在 Android 侧**——它本质是平台备份，不进 commonMain         |
| 单课表备份 / 分享码 / 教务导入                     | 改 kotlinx.serialization，**补齐显式字段清单**（唯一会破坏现有"自动展开"设计的地方） |

> ⚠️ 分享走云端，服务端 `server/index.js` 的 `sanitizeShareScheduleData` 有白名单。改客户端字段必须同步改服务端并重新部署，否则新字段被静默丢弃。

**2.3 数据兼容红线（不可谈判）**

- `Json { ignoreUnknownKeys = true; coerceInputValues = true }`
- 所有 `@SerialName` 与现有 Gson 字段名**逐字对齐**
- 保留旧格式读取路径
- **round-trip 回归**：真实用户备份文件，导出→导入→导出，二次导出必须与首次完全一致

**风险：高。** 历史上「时间配置丢失」是本项目最严重的事故类型（`pruneOrphanTimeConfigs` 曾误删在用配置）。每一步必须可回退。

---


### 阶段 3 · 平台能力抽象 + 仓储下沉（3~4 周）

**3.1 定 expect/actual 接口（只定最小集合，够用就停）**

```kotlin
expect class KeyValueStore { fun getString(k: String, d: String): String; fun putString(k: String, v: String); ... }
expect fun appFilesDir(): String
expect class Notifier { fun schedule(id: String, at: Instant, title: String, body: String); fun cancel(id: String); fun cancelAll() }
expect class ScreenInfo      // 替代 LocalConfiguration（42 处）
expect object Haptics
expect fun showToast(msg: String)
```

Android actual **必须继续走 SharedPreferences**，否则老用户数据全丢。

**3.2 下沉 `data/` 剩余部分**

| 文件                                                   |    行数 | 难点                                                |
| ---------------------------------------------------- | ----: | ------------------------------------------------- |
| `CourseRepository.kt`                                | 2,810 | 全局单例 → KN 线程模型，需改显式注入                             |
| `Holidays.kt`                                        | 1,781 | 3 处 android import（Context/Log/SharedPreferences） |
| `ScheduleAppearance.kt`                              |   610 | 9 处 android import，含 Bitmap 处理                    |
| `ScheduleBackup.kt`                                  |   450 | 文件 IO                                             |
| `SchoolIndex` / `ScriptRepository` / `StatsReporter` |   522 | OkHttp + 文件                                       |

**3.3 留在 `:app` 不动**  
`reminder/`（4,463）、`widget/`（1,507）、`shizuku/`、`wearable/`、`provider/`、`ui/web/`。

**验收：** Android 端全量回归；`:core` 同时编出 android 与 ios 产物。

---

### 阶段 4 · iOS 逻辑层贯通（2~3 周）——**先不碰 UI**

**目标：** 证明 `:core` 在 `iosArm64` 上算出来和 Android 一模一样。这一步不做 UI，只做逻辑验证。

**做什么：**

1. 补齐 `iosMain` actual：`NSUserDefaults` / `UserNotifications` / 沙盒文件。
2. **处理 Kotlin/Native 线程模型**：`CourseRepository` 等全局可变单例在 KN 下需谨慎，必要时 `@ThreadLocal` 或约束到主线程。这是本阶段最容易踩的坑。
3. 写一套跨平台一致性用例：同一份课表 JSON，Android 与 iOS 分别算出今日课程、下一节课、节假日判定，逐条比对。
4. 网络层：OkHttp 换成 Ktor Client 外壳（详见下节）。

**验收：** iOS 模拟器上跑逻辑层，输出与 Android 逐条一致。

---

### 阶段 5 · iOS UI 首版（4~6 周）

**目标：** iPhone 上能看到课表、切课表、看今日课程。

**做什么：**

1. 建 `:ui-shared`（CMP），`:iosApp` Xcode 壳，CMP 内容页嵌入 `UIViewController`。
2. **UI 先挑软柿子**：`TodayScreen`（1,777 行）+ `MainScheduleScreen`（1,932 行）做首批，避开 backdrop 重灾区。
3. 处理 `LocalConfiguration`（42 处）→ `ScreenInfo`；`LocalContext`（32 处）→ 平台接口；`LocalHapticFeedback` 枚举对齐。
4. iPad 双栏布局（`TabletSettingsScreen` / `TabletCourseManagePane`）用 `ScreenInfo` 重写判定。

**验收：** iPhone + iPad 双端可加载课表、切换课表、查看今日课程。

---

### 阶段 6 · 全量 UI 迁移与功能补齐（长期轨道）

**与阶段 3 起即可并行，按批次推进，先易后难：**

| 批次   | 内容                                                         | 阻塞点                     |
| ---- | ---------------------------------------------------------- | ----------------------- |
| 批次 1 | `SettingsScreen`、`CourseEditScreen`、`AddCourseDialog` 等表单类 | 少，Toast / Intent        |
| 批次 2 | `CourseDetailScreen`、`TimeConfigEditScreen`（2,259 行）       | 中，haptics + 配置查询        |
| 批次 3 | `MainScheduleScreen`、`CustomizeScheduleScreen`（1,951 行）    | 大，平板/折叠屏判定              |
| 批次 4 | `ui/effects/*` 2,756 行 | ✅ **已完成**：edgelight 911 迁入 `:backdrop`，liquidglass 510 迁入 `:miuix`，capsule 1,763 迁入 `:backdrop`；余 background 344（换 Miuix KMP artifact 即可） |
| 批次 5 | 教务导入（WKWebView 桥接重写，1,080 行）                               | 大，需 iOS 平台代码            |
| 批次 6 | 提醒专项（滚动预约，见验证 3）                                           | 大，架构约束                  |
| 批次 7 | WidgetKit 小部件（Swift 重写）                                    | 独立排期                    |

---

## 附：网络层专项（OkHttp → Ktor Client）

实测结论：**OkHttp 确实在用，但用法极浅，改造成本很低。**

### 使用点（10 处，`AboutActivity` 那条只是致谢文本，不算）

| 文件                          | 用途           | 形态                                            |
| --------------------------- | ------------ | --------------------------------------------- |
| `AppreciationFetcher`       | 赞赏数据         | GET                                           |
| `NoticeFetcher`             | 公告           | GET                                           |
| `UpdateChecker`             | 版本检查         | GET                                           |
| `TodayAssistant`            | 天气           | GET ×2                                        |
| `ScheduleImport`            | 分享码下载        | GET                                           |
| `ScriptRepository`          | 教务脚本下载       | GET + `byteStream()`                          |
| `StatsReporter`             | 统计上报         | POST JSON                                     |
| `ScheduleExport`            | 分享上传         | POST JSON                                     |
| `ScheduleBackup`            | WebDAV 备份    | **PROPFIND / MKCOL / PUT / GET + Basic Auth** |
| `WebViewRequestInterceptor` | 桌面模式 POST 转发 | POST，纯 Android，留在 `:app`                      |

### 好消息：全部是最基础的能力

- **零**拦截器、零响应缓存、零 WebSocket、零连接池配置、零证书锁定、零 authenticator
- 只有 `connectTimeout` / `readTimeout` / `callTimeout` + `retryOnConnectionFailure(true)`
- 无流式、无 SSE（排查过 `byteStream` 只用于下载文件，非流式读取）
- 唯一有门槛的是 WebDAV 的 `PROPFIND` / `MKCOL` 自定义方法——Ktor 的 `HttpMethod.parse()` 支持

### 改法：**改为自建 `HttpService` 接口，暂不引入 Ktor**（2026-10-09 修订）

~~Ktor 包一层，OkHttp 不删~~ —— 原计划是引入 Ktor Client。实测后改了：

| Ktor 版本 | 连带把 `kotlinx-coroutines` 从 1.9.0 顶到 |
|---|---|
| 3.6.0 | **1.11.0** |
| 3.1.3 | **1.10.2** |

`:app` 的协程 1.9.0 来自 Compose 传递依赖，而提醒/闹钟/同步/小组件全压在协程上。
**把「升级核心异步库」捆进「KMP 迁移」会让故障无法归因**，也无法单独回退。

因此当前实现是 `:core` 自定义 `HttpService` 接口：

```
commonMain   HttpService        → get / post / request(自定义方法)，超时用 HttpTimeouts
androidMain  OkHttpHttpService  → 引擎、超时、重试语义与迁移前逐字一致
jvmMain      HttpURLConnection  → 纯 JDK，零依赖（WebDAV 自定义方法受限，仅桌面调试用）
nativeMain   **未实现，调用即抛** → 接 iOS 前必须替换（见下）
```

`HttpTimeouts` 保留了迁移前实测存在的 4 种超时组合（5/5、10/30、10/10+15、12/20+30），
`callTimeout` 只在显式给了才设置。

### ⚠ 未完成：Kotlin/Native 的 HTTP 实现

`nativeMain/HttpService.native.kt` 目前是**显式抛异常**的占位。OkHttp 无 Native 版本，
需要二选一：
- Ktor `ktor-client-darwin`（iOS）+ `ktor-client-cio`（其他 Native）—— 届时协程版本问题需单独评估
- 或直接调平台 `NSURLSession`

之所以留占位而不是干脆不加 Native 目标：`:core` 现在挂了一个 `linuxX64` **编译门禁**
（见下节），它需要 actual 才能编译通过。

### 🔒 编译门禁：`:core` 的 `linuxX64` + 全仓 `checkKmpPurity`

**第一道（最强）：`:core` 挂 `linuxX64()` 目标，由编译器真编译。**

`:core` 只产出编译产物、不发布。原因是真实教训：此前 `:core` 只有 android + jvm 两个
**JVM** 目标，`compileCommonMainKotlinMetadata` 是 `SKIPPED`，于是 commonMain 从未被
平台中立的 stdlib 检查过 —— 5 类 JVM 专有 API 一路绿灯，真正编 iOS 时会全部失败。

`linuxX64` 同为 Kotlin/Native（同样没有 `kotlin.jvm.*` 默认导入），且能在 Windows 上交叉编译。

```
./gradlew :core:compileKotlinLinuxX64 :core:compileTestKotlinLinuxX64
```

`linuxX64Test` 被 Kotlin 自动禁用（非 Linux 宿主），所以不影响 `check`。

**第二道：`:backdrop` / `:miuix` 只能用静态检查。**

这两个模块做不到编译门禁 —— **Compose Multiplatform 不支持 `linuxX64`**，
而 iOS 目标需要 macOS 宿主。退化为根项目的 `checkKmpPurity` 任务：

- 扫描 `core` / `backdrop` / `miuix` 的 `commonMain` + `skikoMain`（169 个文件）
- 规则覆盖已发现的全部坑：`@Volatile`（无限定时）、`synchronized`、`Dispatchers.IO`、
  `java.*` / `kotlin.jvm.*` 导入、`System.currentTimeMillis`、`String.format`、
  `String.toByteArray()`、`::class.java`、`java.io.*`、`Thread`
- 已接到各模块的 `check` 上，`./gradlew check` 会自动跑

> 两个**已实测确认**的误报陷阱（写规则时踩过，已修）：
> 1. `@Volatile` 只有在**文件未导入** `kotlin.concurrent.Volatile` 时才是问题 —— 必须文件级判断
> 2. `@JvmInline` / `import kotlin.jvm.JvmInline` 是**合法**的（Kotlin 对 value class
>    的注解有特殊处理，探针在 linuxX64 上实测编过），必须排除

静态检查不如编译器完备（未知 API 查不出来），但把已知的坑全覆盖了。

### 顺带要修的真问题（与迁移无关）

~~**10 个文件各自 `new OkHttpClient()`**~~ → **已修**：`OkHttpHttpService` 内部按
`HttpTimeouts` 缓存 Client，迁移过来的文件不再各建一套连接池与线程池。

---

## 五、风险登记表

| #  | 风险                                         | 影响             | 应对                                      |
| -- | ------------------------------------------ | -------------- | --------------------------------------- |
| ~~R1~~ | ~~backdrop / AGSL 在 iOS 无法复现~~                 | — | **已排除**：有现成 KMP 移植（SkSL，视觉等价），换依赖即可 |
| R2 | Gson 替换破坏 4 条序列化通道                         | 用户数据丢失         | 分层替换 + round-trip 回归 + 旧格式读取路径          |
| R3 | iOS 本地通知 64 条上限                            | 提醒漏发           | 滚动预约策略，阶段 0 原型验证                        |
| R4 | Kotlin/Native 线程模型撞全局单例                    | 崩溃 / 状态错乱      | 阶段 3 改显式注入，阶段 4 专项验证                    |
| R5 | `SimpleDateFormat` → kotlinx-datetime 行为偏移 | 日期静默算错         | 逐点对照用例                                  |
| R6 | Miuix 是 fork 的 `-android` 变体               | 阻断跨平台          | 评估：改动提上游 / 改官方 KMP 依赖 + 局部自定义           |
| R7 | 云端分享白名单未同步                                 | 新字段静默丢弃        | 改客户端字段必须同改 `server/index.js` 并重新部署      |
| R8 | 61.7k 行 UI 迁移周期过长，拖垮主线开发                   | 项目停滞           | 严格按批次，每批次结束 `:app` 仍可发版                 |
| R9 | ~~OkHttp 在 iOS 不可用~~                       | 低              | **改法已修订**：自建 `HttpService`，Android/JVM 复用 OkHttp；Native 侧未实现（见「网络层专项」） |

---

## 六、红线原则

1. **不打断 1.6.x 开发。** 每阶段结束时 `:app` 必须能正常编译、打包、发布。任何阶段中途停下都能照常发版。
2. **数据兼容不可谈判。** 存量 SharedPreferences 数据在每步之后都必须正确读出。
3. **每步可回退。** `:core` 出问题就退回单模块。
4. **先验证再推广。** 阶段 0 三个验证不做完，不进入阶段 1 的全面投入。
5. **不要让 fork 变枷锁。** Miuix fork 解决了定制问题，但会阻断跨平台。

---

## 七、立即可做的第一步

**下一步：真机验证 Android 零回归。** 装上刚打出的 APK，重点看用了 edgeLight 的地方
（顶栏按钮 / 下拉菜单 / 底部 Tab / DayColumn / 回到今天悬浮按钮 / 平板侧栏），
确认液态玻璃与改动前一致。

验证通过后进入**阶段 2 · 日期迁移**，从叶子文件开始逐个改，不要用脚本批量改写。
