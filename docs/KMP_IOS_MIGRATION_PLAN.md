# Nexio 课程表 · iOS（KMP）迁移计划

> 目标平台：**iOS**（iPad 一并覆盖）  
> 策略：渐进式，全程不打断 1.6.x 正常发版  
> 制定日期：2026-10-08 · 现状 1.6.4-1008（versionCode 164，Kotlin 2.4.10）

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

### 阶段 0 · 技术预研与地基（1~2 周）——**先验证，再投入**

这一步不做完，后面所有排期都是猜测。三个必须拿到结论的验证：

**验证 1（最高优先级）：backdrop 在 iOS 上能否复现**  
`com/kyant/backdrop` 重度依赖 `RenderEffect` / AGSL（Android GPU 着色器）。`ui/effects/liquidglass`、`edgelight`、`background` 全建立在它之上，iOS 的 Skia 后端没有对应物。

- 做法：写一个最小 CMP demo（iOS 模拟器跑），用 Skia 的 `BlurEffect` + 自定义 SkSL 尝试复现单层模糊与折射。
- **判定**：能复现 → 阶段 6 批次 4 正常排期；复现成本过高 → 接受「Android 保留原效果、iOS 降级为普通模糊」，用 expect/actual 隔离，并在阶段 3 就把接口定死。

**验证 2：kotlinx-datetime 替换后的日期一致性**  
项目里 `SimpleDateFormat` 依赖 JVM 默认 Locale 与时区，换过去后行为必须逐点对齐。这类 bug 只会在线上用户身上爆。

- 做法：挑 `Holidays.kt`（1,781 行）里节假日推算与调休映射的核心路径，写等价实现单文件验证边界（项目无 test 源集，沿用「单文件 `java Xxx.java`」的验证手法）。

**验证 3：iOS 通知配额下的滚动预约策略**  
64 条上限 vs 一学期几百节课。需要原型验证：

- 只预约未来 N 天内的前 64 条，App 每次前台启动时补充；
- 课表变更时批量重算（取消全部 + 重新预约）；
- 验证 `UNCalendarNotificationTrigger` 在跨夏令时/跨年的行为。

**产出：** Go / No-Go 决策 + `:core` 模块骨架（先空壳，只配 `androidTarget` + `iosArm64` + `iosSimulatorArm64`）。

---

### 阶段 1 · 纯逻辑下沉（1~2 周）

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
| 批次 4 | `ui/effects/*`（液态玻璃 / 边缘光）                                 | **最大**，取决于阶段 0 验证 1 的结论 |
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

### 改法：Ktor 包一层，OkHttp 不删

OkHttp 没有官方 KMP 版本，但**不需要删**——Ktor Client 在 Android 上的推荐引擎就是 OkHttp：

```
commonMain   ktor-client-core        → 统一的 get/post/custom 调用
androidMain  ktor-client-okhttp      → 底层仍是 OkHttp，现有超时配置可平移
iosMain      ktor-client-darwin      → NSURLSession
```

因为用法只到「带超时的一次性请求」这个程度，commonMain 侧的封装接口可以非常小，  
不需要引入 ContentNegotiation 之类的插件，也就不必把网络层跟 kotlinx.serialization 绑死。

### 顺带要修的真问题（与迁移无关）

**10 个文件各自 `new OkHttpClient()`**，等于开了 10 套独立的连接池与线程池。OkHttp 官方明确建议共享单例。  
迁移时统一收口到一处 `HttpClient` 注入，顺手把这个也解决掉。

---

## 五、风险登记表

| #  | 风险                                         | 影响             | 应对                                      |
| -- | ------------------------------------------ | -------------- | --------------------------------------- |
| R1 | backdrop / AGSL 在 iOS 无法复现                 | 视觉效果降级，批次 4 阻塞 | **阶段 0 先验证**；接受降级 + expect/actual 隔离    |
| R2 | Gson 替换破坏 4 条序列化通道                         | 用户数据丢失         | 分层替换 + round-trip 回归 + 旧格式读取路径          |
| R3 | iOS 本地通知 64 条上限                            | 提醒漏发           | 滚动预约策略，阶段 0 原型验证                        |
| R4 | Kotlin/Native 线程模型撞全局单例                    | 崩溃 / 状态错乱      | 阶段 3 改显式注入，阶段 4 专项验证                    |
| R5 | `SimpleDateFormat` → kotlinx-datetime 行为偏移 | 日期静默算错         | 逐点对照用例                                  |
| R6 | Miuix 是 fork 的 `-android` 变体               | 阻断跨平台          | 评估：改动提上游 / 改官方 KMP 依赖 + 局部自定义           |
| R7 | 云端分享白名单未同步                                 | 新字段静默丢弃        | 改客户端字段必须同改 `server/index.js` 并重新部署      |
| R8 | 61.7k 行 UI 迁移周期过长，拖垮主线开发                   | 项目停滞           | 严格按批次，每批次结束 `:app` 仍可发版                 |
| R9 | ~~OkHttp 在 iOS 不可用~~                       | 低              | 实测用法极浅，Ktor 包一层即可，Android 端仍走 OkHttp 引擎 |

---

## 六、红线原则

1. **不打断 1.6.x 开发。** 每阶段结束时 `:app` 必须能正常编译、打包、发布。任何阶段中途停下都能照常发版。
2. **数据兼容不可谈判。** 存量 SharedPreferences 数据在每步之后都必须正确读出。
3. **每步可回退。** `:core` 出问题就退回单模块。
4. **先验证再推广。** 阶段 0 三个验证不做完，不进入阶段 1 的全面投入。
5. **不要让 fork 变枷锁。** Miuix fork 解决了定制问题，但会阻断跨平台。

---

## 七、立即可做的第一步

从**阶段 0 验证 1** 开始——它是唯一可能推翻整个排期的未知数：

1. 建一个独立的最小 CMP demo 工程（不影响主项目），target 只开 `iosSimulatorArm64`；
2. 把 `com/kyant/backdrop/effects/Blur.kt` 和 `RenderEffect.kt` 抄进去，尝试用 Skia 复现；
3. 跑起来看效果，判定「可复现 / 需降级」。

同时并行启动**阶段 1**的代码搬迁（1,411 行，风险极低，不受验证 1 结论影响）：

```kotlin
// settings.gradle.kts
include(":core")

// core/build.gradle.kts
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.+")
        }
    }
}
```

按 `Course.kt` → `ScheduleFolder.kt` → `CourseTimeResolver.kt` → `CourseScheduleDateBounds.kt` → `TimeConfig.kt` 顺序搬，最后换掉 2 处 `LocalDate`，编译，冒烟。

**这两件事做完，iOS 迁移就算真正开始了。**
