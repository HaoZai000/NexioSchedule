# Nexio 课程表 · iOS（KMP）迁移计划

> 目标平台：**iOS**（iPad 一并覆盖）  
> 策略：渐进式，全程不打断 1.6.x 正常发版  
> 制定日期：2026-10-08 · 最近更新：2026-10-09（Kotlin 2.4.10，AGP 9.2.1，CMP 1.12.0）  
> 代码状态：`master` `ada9380`，Android 侧 `assembleDebug` 通过；**iOS 尚未接入**

---

## 🔄 接手须知（新会话 / 新人从这一节开始）

> 这一节是为了让**没有任何历史上下文的人**能直接接手。
> 代码状态：`master` = `6367c55` + **2026-10-10 未提交的存储层收口改动**
> （清单见 `docs/NAVIGATION_MIGRATION_HANDOFF.md` 顶部的「本轮工作区改动尚未提交」）。
> 三条最重要的事实：**① 有测试没入库 ② 别推翻下面那几条决定 ③ 网络层的 Native 实现是空壳。**

### 1. 现状一句话

`:androidApp` 仍是 Android-only（迁移主体，349 处 `android.*` import / 93 文件）；
数据层与 `data/school/` 已整体下沉 `:core`，**节假日纯逻辑集群也已全部下沉**（⑨）；
**存储层已三平台闭环**（2026-10-10，见 handoff 文档 7.7）：`KeyValueStore` 的 Android 实现
（`SharedPreferencesStore`）搬到了 `core/src/androidMain`，iOS 实现
（`core/src/iosMain/.../UserDefaultsStore.kt`）已写好 —— ✅ **编译已验证**
（2026-10-10 实测 Windows 上就能编 iOS klib，见 A1-bis；运行时三点仍需真机）；
`:miuix` / `:backdrop` 已是 KMP 模块，**`skikoMain` 已针对非 JVM 目标（wasmJs）编译通过**
（2026-10-10，见 D 节专节）；
**iOS target 已声明但默认关闭**（`-Pnexio.ios=true` 开启，原因见 C 节），**仍无 Xcode 工程**；
**网络层已实现**（2026-10-10，`iosMain/HttpService.ios.kt`，NSURLSession delegate，
编译通过，未引入 Ktor）。

### 2. ✅ 测试已全部入库（2026-10-10，`057d6ab`）

`core/src/commonTest/`（10 个）与 `core/src/jvmTest/`（14 个）共 **241 个用例已提交**，
连同 `jvmTest/resources/school_index.pb`（77KB，真实 248 校生产索引，作差分基准夹具）。

此前这两处是 untracked、但 `core/build.gradle.kts` 已提交测试依赖 —— 新克隆的仓库
`:core:check` 跑不到任何测试。**这个不一致已消除**，现在版本库里能真跑到这 241 个用例。
跑法：`./gradlew :core:jvmTest --rerun-tasks`（22 个测试类 / 0 失败）。

> ⚠ **提交前先确认**：`school_index.pb` 是**真实生产索引**。若此仓库会公开，
> 需评估是否换成脱敏/合成索引（当前已入库）。

> 同一次提交还把模块目录 `app/` → `androidApp/` 改名并入库（212 个文件，
> git 全部识别为 `R100` 改名，`--follow` 能穿透读到历史）。此前 `androidApp/`
> 整个是 untracked —— 任何 `git checkout .` / `git clean -fd` 都会让它与恢复出来的
> `app/` 变成双份源码（7 万行 UI 只靠「没提交」吊着）。
> **包名 / applicationId 未动**（仍是 `com.haooz.chedule`），存量数据不受影响。


### 3. 下一步做什么（三个候选，各自的阻塞）

| 候选 | 阻塞 / 前提 | 风险 |
|---|---|---|
| ✅ **拆 `Holidays.kt` 的类型集群** | **已完成**（2026-10-09）：1782 行拆成 4 个文件，6 个平台中立类型已下沉 `:core`（详见「当前进展 ⑦」） | — |
| ✅ **`java.time` → kotlinx-datetime** | **已完成**（2026-10-09）：`:androidApp` 全部 22 个文件换血，`:core` 补 `DateExt` 兼容层 + 12 个差分等价用例（详见「当前进展 ⑧」） | — |
| ✅ **节假日四个纯逻辑文件下沉 `:core`** | **已完成**（2026-10-09）：`CourseScheduleDateBounds` / `TeachingWeekReorganization` / `HolidayCourseExclusion` / `HolidayCountdown` 全部进 commonMain（详见「当前进展 ⑨」） | — |
| ✅ **`HolidayManager` 存储层下沉 `:core`** | **已完成**（2026-10-09）：`Context`/`org.json`/Gson/`@Synchronized`/`CourseRepository` 反向依赖全部处理掉，节假日整条链现在完全跨平台（详见「当前进展 ⑩」） | — |
| ✅ **`CourseRepository` 下沉 `:core`** | **已完成**（2026-10-09）：**数据层全部完成**，顺带修掉两个「用户无法恢复自己备份」的真实缺陷（见「当前进展 ⑫」） | — |
| ✅ **`ScheduleBackup` 下沉 `:core`** | **已完成**（2026-10-09）：WebDAV 收敛到 `HttpService`，iOS 侧不再有第二处网络实现要写（见「当前进展 ⑬」） | — |
| ✅ **存储层三平台闭环（`KeyValueStore`）** | **已完成**（2026-10-10）：Android 实现 `SharedPreferencesStore` 搬到 `core/src/androidMain`；iOS 实现 `UserDefaultsStore` 已写好；`:androidApp` 直调 `getSharedPreferences` **88 → 46 处**（剩余全属类别 C）。详见 handoff 文档 **7.7** | 中 —— ✅ **编译已验证**（2026-10-10，Windows 上 `compileKotlinIosArm64` 通过）；**运行时**三点仍需真机，见 A2 |

**UI 导航改造（阶段 5 前置）已起步** —— 增量 1 做完（路由器基础设施 + 关于页宿主化，
Manifest 23 → 20 个 Activity）。详见「🎯 UI 导航改造」一节，**下一个增量是把宿主迁到 `MainActivity`**。

**数据层已全部完成**（⑫⑬）。`:androidApp/data` 只剩两个**按归属不该进 `:core`** 的文件
（见 ⑬-c）：`ScheduleAppearance`（Bitmap）/ `WallpaperTransform`（Compose `Offset`），
它们的归属是阶段 5 的 `:ui-shared`。

剩下的候选：

| 候选 | 说明 | 风险 |
|---|---|---|
| ~~**Native HTTP 实现**（`HttpService.native`）~~ | ✅ **已完成**（2026-10-10）：`iosMain/HttpService.ios.kt`（NSURLSession delegate 流式读），**未引入 Ktor**，双 iOS 目标编译通过；占位挪到 `linuxX64Main`。剩下的只是**真机运行验证**（WebDAV PROPFIND / 8MB 截断 / 断网抛错） | ~~中~~ → 低 |
| **Gson 4 条通道**（10 个文件） | 全在 `:androidApp` 的 UI/导入导出层。**现阶段不需要动** —— Gson 留在 `:androidApp` 是允许的，只有阶段 5 把 UI 搬进共享模块时才必须清掉 | 高（等 UI 迁移时再做） |
| **阶段 5 的 UI 迁移** | 61.7k 行 Compose UI，`LocalConfiguration` 42 处 / `LocalContext` 32 处 | 大，按批次 |
| **阶段 5 前置：建 `:ui-shared`** | CMP 共享 UI 模块；`ScheduleAppearance` / `WallpaperTransform` 的归属地 | 中 |
| **Gson 迁移**（文档风险表里的 **R2，最高**） | **真实备份已到手**（2026-10-09）→ 前置调研做完，配置要求已量出（见「🔬 R2 前置调研」）。仍需 `@Serializable` + 显式字段清单。⚠ `TeachingWeekReorganization` 的 Gson **已经换掉了**（⑨-b），剩的是单课表备份 / 分享码 / 教务导入 + 全量备份 4 条通道 | 高 —— 数据格式一变，存量用户读不出来。**已从「未知风险」降为「有明确配置要求」** |
| **`AppFile` 的 iOS 实现**（A3） | Native HTTP 已完成后，iOS 侧**剩余的唯一硬实现**：`NSFileManager` 实现 `AppFile` 接口 + 启动注入（约 30 行，照抄 `FileAppFile.kt`）。契约与落盘路径逐字要求见 A3 | 中（路径抄错 = 学校索引引导失效） |

**建议顺序**：`CourseRepository`（**数据层最后一块**）→ 再 Gson 4 条通道（等拿到真实备份）。

### 4. 每次改完必跑

```bash
# 红线（--offline，模拟 CI）
./gradlew :core:check :miuix:check :backdrop:check --offline
./gradlew :androidApp:assembleDebug          # Android 零回归 —— 这是红线，不许破

# 或拆开跑（等价）
./gradlew :core:compileKotlinLinuxX64 :core:compileTestKotlinLinuxX64   # commonMain/Test 平台中立
./gradlew checkKmpPurity              # 已知 JVM 专有 API 静态扫描
./gradlew :core:jvmTest --rerun-tasks # 241 个用例（已入库）

# 动了 commonMain / skikoMain 时，额外跑非 JVM 编译门禁（需联网 + PATH 有 Node）
./gradlew -Pnexio.wasm=true checkKmpWasmJs
```

> ⚠ **wasmJs 门禁与 check 不能同时跑**：开启 `-Pnexio.wasm=true` 期间 Kotlin/Wasm 插件
> 会在配置阶段注册 Distributions 仓库，与 `FAIL_ON_PROJECT_REPOS` 冲突，导致 `check` 直接失败。
> 要跑 `check` 就回到默认态（不加 property）。原因详见 D 节专节。
>
> **PowerShell 里 `-P` 参数要加引号**：`.\gradlew.bat '-Pnexio.wasm=true' checkKmpWasmJs`，
> 否则 PowerShell 会把 `-Pnexio.wasm=true` 当成任务名，报 `Task '.wasm=true' not found`。

> **「能编过」必须问清楚是哪个目标能编过。** 这个项目踩过一次大坑：
> `:core` 一度只有 android + jvm 两个 **JVM** 目标，commonMain 从未被平台中立的 stdlib
> 检查过，于是 6 类 JVM 专有 API 共 13 处一路绿灯，真正编 iOS 时会全部失败。

### 5. 本机环境（不看会白踩半天）

| 事项 | 说明 |
|---|---|
| PowerShell | **必须是 pwsh 7**；5.1 会把中文输出弄成乱码 |
| `JAVA_TOOL_OPTIONS` | 跑 `java.exe` 前先 `Remove-Item Env:\JAVA_TOOL_OPTIONS`，否则 `-Dfile.encoding` 被污染 |
| Python | PATH 里**没有** `python`；用 `C:\Users\43908\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe` |
| `JAVA_HOME` | `D:\JDK` |
| 内嵌中文的 pwsh 脚本 | 存成 UTF-8 **带 BOM**，否则 pwsh 7 可能按 ANSI 读 |
| `.workbuddy/` | 在 `.gitignore` 里 |

### 6. 本地长期笔记（不进 git，换机器就没了）

`.workbuddy/memory/`：`MEMORY.md` 是索引，另有按日期（`2026-10-03` … `2026-10-09`）的详细笔记。
里面记录了**数据兼容基线 1.5.6 的判据**、时间配置丢失事故的根因、调休补课映射规则、
TimeConfig 的 4 条序列化通道、服务端部署流程等 —— 都是踩出来的，**建议接手前先读一遍**。

### 7. 已定型、别推翻的决定

| 决定 | 原因 |
|---|---|
| 存储 / 文件用**接口 + 启动注入**，不用 expect/actual | 让 `:core` 的 commonMain 保持零平台实现，`linuxX64` 编译门禁才编得过 |
| **不引入 Ktor** | 实测任何现代 Ktor 都会把全 app 的 `kotlinx-coroutines` 从 1.9.0 顶到 1.10.2 / 1.11.0，而提醒·闹钟·同步·小组件全压在协程上 |
| 偏好文件名**不做集中常量表** | 手抄错名字 = 老用户数据静默读不出来（曾把 `holiday_settings` 误写成 `holiday_prefs`）；项目共 **17 个**偏好文件名 |
| Android actual 必须继续走 SharedPreferences / `java.io.File` | 换实现 = 存量用户数据读不出来 |
| 不引入 `withHostTest {}` | 实测会让 8 个测试因 `Build.MODEL` 为 null 而失败 |
| `wearable/` 不动 | 不由用户维护 |
| 版本号由用户自己管 | 审查时不提醒 |

---

## 🤝 给 iOS 开发者的交接清单

> 本节写给接手 iOS 的人：**哪些必须由你实现、哪些已经能直接用、当前验证到什么程度**。
> 基于 2026-10-09 的代码状态。带 ⚠ 的都是会踩的坑。

### A. 必须由你实现（3 个平台实现 + 3 个启动注入 + 3 项工程配置）

#### A0. ⚠ 三项工程配置 —— **不做则 App 起不来 / 调不到 Kotlin 代码**（2026-10-10 补）

这三项是官方文档明确要求、此前本计划遗漏的。**任何一项缺失都会在第一步就卡住。**

**A0-1. `:core` 必须声明 `binaries.framework`，否则 Swift 根本调不到 Kotlin 代码。**

`compileKotlinIosArm64` 绿了**不代表能用** —— Swift 侧需要一个可 embed 的 framework：

```kotlin
// core/build.gradle.kts 的 kotlin { } 内
val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
if (enableIos) {
    iosArm64()
    iosSimulatorArm64()
    // ↓ 缺这一段，Swift 侧无法 import Shared
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
}
```

然后在 Xcode 里 embed 这个 `Shared.framework`，Swift 侧才能 `import Shared`。
官方教程：kotlinlang.org「Make your app multiplatform」→「Create an iOS project in Xcode」。

**A0-2. `Info.plist` 必须设 `CADisableMinimumFrameDurationOnPhone = true`，否则 iOS 一启动就崩溃。**

CMP 自 1.7.3 起**强制**检查这个键，缺失或为 `false` 会直接 crash
（`ComposeUIViewControllerConfiguration.enforceStrictPlistSanityCheck` 默认开启）。

```xml
<key>CADisableMinimumFrameDurationOnPhone</key>
<true/>
```

来源：[What's new in CMP 1.7.3](https://kotlinlang.org/docs/multiplatform/whats-new-compose-170.html)。

**A0-3. Xcode 的 shared scheme 要提交，否则 CI 上 `xcodebuild` 失败。**

Xcode 自动生成的 scheme 落在 `xcuserdata`（通常被 gitignore），CI 上会报
`does not contain a scheme named YourApp`。要手动提交
`YourApp.xcodeproj/xcshareddata/xcschemes/`。

> **iOS 集成方式**：本计划采用 direct integration（最直接）。
> 其他选项见官方 [iOS integration methods overview](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html)。

#### A1. `HttpService` 的 Native 实现 —— ✅ **已实现（2026-10-10）**

- **iOS 实现**：`core/src/iosMain/kotlin/com/haooz/chedule/data/HttpService.ios.kt`
  （`NSURLSession` + delegate 流式读，零第三方依赖，**未引入 Ktor**）
- **linuxX64 占位**：`core/src/linuxX64Main/.../HttpService.linuxX64.kt`
  （原 `nativeMain/HttpService.native.kt` 挪过去了 —— iOS 已有真 actual，
  两个 actual 不能同时覆盖同一目标。linuxX64 仅作编译门禁，保留显式失败占位）
- **编译验证**：`-Pnexio.ios=true :core:compileKotlinIosArm64` +
  `:core:compileKotlinIosSimulatorArm64` **均通过**（在 **Windows** 上，见下方「重要发现」）
- **为什么是 delegate 而不是 completionHandler**：契约要求 `maxBytes` 在**读取过程中**
  生效；`dataTaskWithRequest(completionHandler:)` 要等整个响应体收完才回调，大响应会先
  把内存吃满。实现走 `NSURLSessionDataDelegate` 三件套（`didReceiveResponse` 预检
  声明长度 → 已超限直接 cancel、一个字节不收，**与 Android 的「声明超限 → 空字节 +
  truncated」逐字对齐**；`didReceiveData` 边收边计数、超限保留到上限即 `task.cancel()`，
  语义对齐 `readAtMost`：`total > maxBytes` 才算截断；`didCompleteWithError` 统一收口）
- **签名来源**：全部来自本机 `klib dump-metadata` 导出的 `platform.Foundation`
  （Kotlin/Native 2.4.10 / ios_arm64），**没有一个是猜的** —— 协议名带 `Protocol` 后缀、
  disposition 是 `Long` 常量、setter 是扩展函数，三处都踩过坑后修正
- **与 Android 的已知差异**（都记在实现文件的 KDoc 表格里）：
  iOS 无独立 connect 超时（共用 `timeoutIntervalForRequest`）、`callSeconds` 映射到
  `timeoutIntervalForResource`；Cookie 持久化与 URLCache 已显式关闭以对齐 OkHttp 默认

> ⚠ **运行时验证仍需真机/模拟器**（Windows 只能编译，不能链接运行）：
> 重点回归 WebDAV（`PROPFIND`/`MKCOL` 自定义方法）、脚本下载的 8MB 截断、
> 以及网络异常路径（断网时应抛 `Exception` 而不是被吞）。

> **若将来仍想引入 Ktor**：实测任何现代 Ktor 都会顶掉 `kotlinx-coroutines` 版本
> （3.6.0 → 1.11.0，3.1.3 → 1.10.2），而本项目当前是 **1.9.0**，提醒/闹钟/同步/小组件
> 全压在协程上。这正是当初**刻意不引入 Ktor** 的原因。
> 若要引入，**请单独评估、单独提交**，不要和其他改动混在一起 ——
> 否则真机出现时序类异常时无法归因，也无法单独回退。

#### A1-bis. 🔑 重要发现：**Windows 上就能编译 iOS klib**（2026-10-10 实测）

此前两份文档都写着「Windows 上编译不了 iOS（缺 Xcode SDK）」—— **对链接成立，对编译不成立**：

```powershell
.\gradlew.bat '-Pnexio.ios=true' :core:compileKotlinIosArm64          # Windows 上实测 BUILD SUCCESSFUL
.\gradlew.bat '-Pnexio.ios=true' :core:compileKotlinIosSimulatorArm64  # 同样通过
```

- **能做的**：iOS 目标的 **klib 编译**（纯 Kotlin → klib，不需要 Apple 链接器）。
  `UserDefaultsStore.kt` / `HttpService.ios.kt` 的**编译错误现在就能在本机抓到**。
- **不能做的**：**链接与运行**（需要 macOS + Xcode 的 Apple SDK / ld）。
  三条运行时验证点（NSNumber 装箱、`persistentDomainForName`、真机联网）仍需 macOS。
- 用法照旧：首次联网拉 iOS klib 变体；iOS target 默认关（`-Pnexio.ios=true` 开启）。

- **必须遵守的契约**（`core/src/commonMain/.../HttpService.kt`，别改签名）：

  | 项 | 约定 |
  |---|---|
  | 方法 | `get(url, headers, maxBytes)` / `post(url, body, contentType, headers)` / `request(method, url, body, contentType, headers)` |
  | 返回值 | `HttpResult(code, bytes, truncated = false)`，`isSuccessful` = `code in 200..299`；`maxBytes > 0` 时超限返回 `truncated = true`（**不是抛异常**），调用方按失败处理 |
  | 异常 | **网络异常照常抛出，不要吞** —— 调用点普遍自带 `try/catch`，吞掉会让那些兜底失效 |
  | 响应体 | 一次性读入内存（现有调用点没有流式消费）；`maxBytes` 必须在**读取过程中**生效，不能读完再检查大小 |
  | 超时 | `HttpTimeouts(connectSeconds, readSeconds, callSeconds)`；`callSeconds <= 0` 表示不设整体超时 |
  | 自定义方法 | WebDAV 用到 `PROPFIND` / `MKCOL`（在 `ScheduleBackup`，目前仍留在 `:androidApp`） |

#### A2. `KeyValueStore` 的实现（`NSUserDefaults`）—— ✅ **已写好，待你在 macOS 上验证**

- **已实现**：`core/src/iosMain/kotlin/com/haooz/chedule/data/UserDefaultsStore.kt`（2026-10-10，约 190 行）
- **接口**：`core/src/commonMain/kotlin/com/haooz/chedule/data/KeyValueStore.kt`
- **Android 参考**：`core/src/androidMain/kotlin/com/haooz/chedule/data/SharedPreferencesStore.kt`
  （2026-10-10 也从 `:androidApp` 搬到了 `core/src/androidMain`，包名未变）
- **契约**（每条都有对应的 Android 行为，别想当然）：

  | 项 | 约定 |
  |---|---|
  | ⚠ **按名字分文件** | 项目有 **17 个**偏好文件名（`app_preferences` / `course_schedule_prefs` / `course_reminder_prefs` …）。每个名字必须对应**独立**存储，**绝不能合并成一个** —— 合并等于篡改存量数据的落盘位置 |
  | ⚠ 名字来源 | 各业务文件自带的 `private const val PREFS = "…"`，**不要新建集中常量表**（手抄错名字 = 老用户数据静默读不出来） |
  | `getStringSet` | 必须返回**副本**；Android 上就地改会抛 `UnsupportedOperationException`，而 `Collections.singleton` 的坑就在这里 |
  | 类型不符 | 返回默认值。Android 侧是抛 `ClassCastException`、调用方用 `runCatching` 兜底 —— 两种路径都要能工作 |
  | `edit {}` | 语义 = 立即提交（等价 Android 的 `apply()`） |
  | `all()` | 返回**拷贝**（备份/迁移功能依赖它） |

- ⚠ **`all()` 的类型必须精确 —— 这是 iOS 侧最容易埋雷的一处**：
  `CourseRepository` 用 `when (value) { is Int -> putInt … is Long -> putLong … }` **复制课表**，
  `exportAllPreferences()` 也按类型导出全量备份。而 plist 里所有数值都是 `NSNumber`，
  读出来**分不清 Int 与 Long** —— 若统一按 Long 还原，复制课表会把 Int 键写成 Long，
  之后 Android 侧 `getInt` 拿到 Long 抛 `ClassCastException`、被既有 `runCatching` 兜底成默认值，
  **用户设置静默丢失**。故 `UserDefaultsStore` 把类型记在独立域 `suiteName.__kvtypes`
  （必须独立：`all()` 会被遍历，标记键同域会被当成业务数据复制/导出）。**别删这个域。**

- ⚠ **首次运行必须验证的三点**（这三条是**运行时**语义，编译器查不出来 ——
  编译已在 Windows 上通过，见 A1-bis；运行需真机/模拟器）：
  1. Kotlin 的 `Int/Long/Float/Boolean` 传给 `setObject(_:forKey:)` 是否自动装箱成 `NSNumber`
  2. `persistentDomainForName` 对自定义 suite 是否返回非空（`all()` 依赖它）
  3. `raw is String` 对 plist 里的 `NSString` 是否成立

#### A3. `AppFile` 的实现（`NSFileManager`）+ 启动注入

- **接口**：`core/src/commonMain/kotlin/com/haooz/chedule/data/AppFile.kt`
- **照抄参考**：`androidApp/src/main/java/com/haooz/chedule/data/FileAppFile.kt`（Android，约 30 行）
- **契约**：`writeBytes` **必须自动创建父目录**（调用方不再手写 `mkdirs`）；
  `resolve(relative)` 以 `/` 分隔；`root` 指向应用沙盒私有目录。
- **落盘路径必须逐字保持**：`repo/index/school_index.pb`、`repo/schools/resources`

#### A4. 启动注入（3 个 `init`，缺任何一个都会在首次使用时抛异常）

**示意代码**（Kotlin/Native 通过 cinterop 调 Foundation；API 名以实际 cinterop 绑定为准）：

```kotlin
// iOS 入口（AppDelegate / @main，越早越好；对应 Android 的 NexioApplication.onCreate）
val version = NSBundle.mainBundle
    .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"

AppInfo.init(version = version)
AppStorage.init { name -> UserDefaultsStore(name) }            // 见 A2 —— 已实现，直接用
AppFiles.init(
    root = NsFileAppFile(defaultDocumentsDir()),               // 见 A3
    readAsset = { path ->                                      // 内置学校索引引导
        // 把 school_index.pb 放进 Xcode bundle 的 Resources，按 path 取；
        // **失败要抛异常** —— SchoolRepository 会捕获、打日志、跳过引导（原逻辑如此）
        val p = NSBundle.mainBundle.pathForResource(path, null)
            ?: throw IOException("内置资源缺失: $path")
        NSData.dataWithContentsOfFile(p)!!.toByteArray()
    },
)
```

**Android 的真实对应实现**可直接对照：`androidApp/src/main/java/com/haooz/chedule/NexioApplication.kt`
（`onCreate` 开头那几行 `AppStorage.init` / `AppInfo.init` / `AppFiles.init`）。

### B. 已经能直接用，不需要你写的

| 能力 | 位置 | iOS 现状 |
|---|---|---|
| 日志 `platformLog` | `core/src/nativeMain/.../PlatformLog.native.kt` | ✅ 已实现（println）。想接 `NSLog` 可覆盖，结构不用变 |
| `ioDispatcher` | `core/src/nativeMain/.../IoDispatcher.native.kt` | ✅ `Dispatchers.Default`（Native 侧不用 `Dispatchers.IO` —— 它在 Native 上是 `internal`） |
| 设备信息 `currentDeviceInfo()` | `core/src/nativeMain/.../PlatformInfo.native.kt` | ⚠ 能编能跑，但只是 `Platform.osFamily` 兜底。**建议改成 `UIDevice`**（`model` / `systemVersion`），`sdkLevel` 保持 0（那是 Android SDK_INT 的语义） |
| 学校索引解析 | `core/src/commonMain/.../school/SchoolIndex.kt` | ✅ 手写 protobuf 解析器，已用**真实 248 校索引**做过新旧实现等价性对比 |
| `:miuix` / `:backdrop` 的 Skia 实现 | `*/src/skikoMain/` | ✅ **wasmJs 编译已验证**（2026-10-10，见 D 节专节）。运行层面 iOS 仍需真机 |

#### F. ⚠ 资源迁移（`res` → `composeResources`）—— 阶段 5 的独立工作量，此前计划未计入

官方指南明确列出 4 条与本项目相关的差异，**本计划此前完全没提**：

| 官方提醒 | 本项目实际数量（2026-10-10 实测） |
|---|---|
| 资源目录要从 `res` 改名成 `composeResources` | `androidApp/src/main/res/drawable/` **38 个** + `drawable-night/` **9 个** |
| 生成的资源访问类叫 `Res`，不是 `R` | 约 **30 处** `R.drawable.*` 落在会进共享模块的页面 |
| XML 里的 `@android:color` 要换成颜色十六进制 | `themes.xml:10-11` × 2（`values/` 与 `values-night/`）的 `@android:color/transparent` |
| CMP 1.8.0 起 `androidLibrary` 目标要显式开资源 | 将来建 `:ui-shared` 时必须写 `android { androidResources.enable = true }`，否则 `MissingResourceException` |

**按归属规则，真正需要迁的是一小部分**（2026-10-10 逐文件核过）：

- ✅ **不用迁**（留在 `:androidApp`）：`widget/` + `reminder/` 那约 **40 处** `R.mipmap.ic_launcher` /
  `R.layout.*` / `R.drawable.widget_*` —— RemoteViews 与 Notification 是 Android 专有。
- ⚠ **要迁**（页面会进共享模块）：
  - `TodayAssistant.kt:75-89` 天气图标 **20 个** `R.drawable.icon_*`（`icon_cloudy` / `icon_heavy_rain` / …）
  - `AboutScreen.kt:1107,1130` `ic_github` / `ic_gitee`
  - `AppreciateAuthorScreen.kt:413,445,517` `ic_anonymous_avatar` / `zanshangma`
  - `PreferenceSettingsScreen.kt:210,222` `theme_preview_light` / `theme_preview_night`
  - `WidgetIntroScreen.kt:204,255,294,313` `ic_miuix_logo` / `ic_widget_clock` / `ic_widget_location`
  - `WebViewScreen.kt:883` `ic_phone_pc`

> `TodayAssistant` 那 20 个天气图标是唯一有规模的：`type.contains("冰雹") -> R.drawable.icon_t_storm`
> 这类分支要整段改成 `Res.drawable.icon_t_storm`。
> `drawable-night/` 的 9 个在 CMP 里对应 `composeResources/drawable-night/`。

**另外两条 CMP 变更，阶段 5 会用到**：

- **`@Preview` 用哪个包**：CMP 1.10.0 起已统一，`commonMain` 直接用
  `androidx.compose.ui.tooling.preview.Preview`；`org.jetbrains.compose.ui.tooling.preview.Preview`
  与 desktop 专用的 `androidx.compose.desktop.ui.tooling.preview.Preview` **均已废弃**。
  我们在 CMP 1.12.0 上，**别按官方旧指南走 `org.jetbrains.compose` 那个**。
  来源：[What's new in CMP 1.10.3](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html)。
- **`LocalLifecycleOwner` 换包**：CMP 1.7.3 起从 `androidx.compose.ui.platform` 移到了 lifecycle 包。
  当前 `:androidApp` **0 处使用**（2026-10-10 实测），但阶段 5 大概率会用到。

#### G. ⚠ Xcode shared scheme 要提交（CI 会踩）

官方指南专门强调：Xcode 自动生成的 scheme 落在 `xcuserdata`（通常被 gitignore），
于是 CI 上 `xcodebuild -scheme YourApp` 会报 `does not contain a scheme named YourApp`。
要手动提交 `YourApp.xcodeproj/xcshareddata/xcschemes/`。

### C. 加 iOS target 的步骤（**需要 macOS**）

1. ~~`core/build.gradle.kts` 加目标~~ —— ✅ **已加好**（2026-10-10：`iosArm64()` + `iosSimulatorArm64()`），
   但**默认关闭**，用 `-Pnexio.ios=true` 显式开启。默认关的原因是：
   声明 iOS target 会让 `:core:check` 去解析 iOS 的 klib 依赖
   （`kotlinx-datetime/coroutines/serialization` 的 `-iosarm64/-iossimulatorarm64` 变体），
   而门禁是 `--offline` 跑的 → `:core:check` 直接失败，把「Android 零回归」红线弄脏
   （实测报 `Could not resolve … iosSimulatorArm64CompileKlibraries`，详见 handoff 文档 7.7）。
   **所以 macOS 上要这样跑**（首次需联网拉 iOS klib）：
   ```bash
   ./gradlew -Pnexio.ios=true :core:compileKotlinIosArm64
   ```
   `linuxX64` 是编译门禁用，保留不动。
2. ✅ **`:miuix` / `:backdrop` 的 `skikoMain` → iOS 源集接线 —— 已做好**（2026-10-10，`ca95feb`）。
   原判断是「现在它只挂在 jvm 上，iOS 侧要加同样一行」；现已改成三处条件式接线：
   ```kotlin
   jvmMain  { kotlin.srcDir("src/skikoMain/kotlin") }
   if (enableWasm) { wasmJsMain { kotlin.srcDir("src/skikoMain/kotlin") } }
   if (enableIos)  { iosMain    { kotlin.srcDir("src/skikoMain/kotlin") } }
   ```
   **必须条件式**：`wasmJs` / iOS target 默认关闭，此时这些源集不存在，
   无条件引用会配置失败（`Unresolved reference 'iosMain'`）。
   ⚠ 接线这件事本身已在 wasmJs 目标上**闭环验证过**（见 D 节），
   漏接会报 10 处 `no actual declaration` —— 那条坑不会再出现了。
3. ✅ **风险 R6 已排除**（2026-10-10，Windows 上纯依赖解析，**不需 macOS**）：
   官方 miuix 5 个 artifact **都有 `-iosarm64` 变体**，且能连到 `:backdrop` 提供的 iosArm64 klib。
   验证命令：
   ```bash
   # 需先给 :miuix / :backdrop 加 iosArm64()（默认关，-Pnexio.ios=true 开启）
   ./gradlew -Pnexio.ios=true :miuix:dependencies --configuration iosArm64CompileKlibraries
   ```
   实测输出（全链解析成功，`project :backdrop` 不再 FAILED）：
   ```
   miuix-squircle / miuix-icons / miuix-blur / miuix-preference / miuix-navigation3-ui
   → 各自的 -iosarm64 变体 → miuix-core / miuix-shader / miuix-ui 的 -iosarm64
   ```
   即「Miuix 是 fork 的 `-android` 变体会阻断跨平台」这个风险**不存在**。
   剩下要保证的只有一件事：本项目 fork 的 `miuix-ui` 在 iOS 侧有 `skikoMain` 的 actual 兜底（第 2 条）。
4. ⚠ **`:core` 必须声明 `binaries.framework`，否则 Swift 调不到** —— 这是官方文档明确要求、
   此前本计划遗漏的一步。**没有它，`compileKotlinIosArm64` 绿了也没用。**
   ```kotlin
   // core/build.gradle.kts 的 kotlin { } 内
   val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
   if (enableIos) {
       iosArm64()
       iosSimulatorArm64()
       // ↓ 缺这一段，Swift 侧无法 import
       listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
           target.binaries.framework {
               baseName = "Shared"        // Swift 侧 import Shared
               isStatic = true
           }
       }
   }
   ```
   然后 Xcode 侧 embed 这个 framework（KMP 官方教程「Make your app multiplatform」
   →「Create an iOS project in Xcode」那一步）。
5. ⚠ **`Info.plist` 必须设 `CADisableMinimumFrameDurationOnPhone = true`，否则 iOS 一启动就崩溃。**
   CMP 自 1.7.3 起**强制**检查这个键，缺失或为 `false` 会直接 crash
   （`ComposeUIViewControllerConfiguration.enforceStrictPlistSanityCheck` 默认开启，
   可显式置 false 关掉检查，但正确做法是补上键）。
   来源：[What's new in CMP 1.7.3](https://kotlinlang.org/docs/multiplatform/whats-new-compose-170.html)。
   ```xml
   <key>CADisableMinimumFrameDurationOnPhone</key>
   <true/>
   ```
6. 跑 `./gradlew -Pnexio.ios=true :core:compileKotlinIosArm64`，**不要只跑 android / jvm** —— 原因见 D 节。


### D. 当前验证边界（请勿高估）

| 说法 | 真实程度 |
|---|---|
| `:core/commonMain` 平台中立 | ✅ **编译器验证**：`:core` 挂了 `linuxX64`（Native）目标，`compileKotlinLinuxX64` + `compileTestKotlinLinuxX64` 通过 |
| `:miuix` / `:backdrop` 的 commonMain + skikoMain 平台中立 | ✅ **编译器验证（wasmJs）**：2026-10-10 起挂 wasmJs 目标，`compileKotlinWasmJs` 通过。**不再是「仅静态检查」** —— 见下方专节 |
| `:miuix` / `:backdrop` 用 linuxX64 做门禁 | ❌ **不可能**：实测 CMP 1.12.0 发布的变体只有 android / desktop(jvm) / iosArm64 / iosSimulatorArm64 / js / macosArm64 / wasmJs，**无任何 linux 目标** |
| skikoMain 能在 iOS 跑 | ⚠ **编译已验证（wasmJs），运行未验证**。wasmJs 与 iOS 同为非 JVM，但 Kotlin/Wasm 与 Kotlin/Native 的 stdlib 仍有差异（cinterop / `NSUserDefaults` 等只存在于 Native） |
| `KeyValueStore` 的 iOS 实现 | ✅ **编译已验证**（2026-10-10，Windows 上 `-Pnexio.ios=true :core:compileKotlinIosArm64` 通过）。**运行时**三点仍需真机/模拟器，见 A2 |
| iOS 网络层 | ✅ **已实现并编译通过**（2026-10-10）：`iosMain/HttpService.ios.kt`（NSURLSession delegate 流式读），linuxX64 占位已挪到 `linuxX64Main`。**运行时**（真机联网 / WebDAV PROPFIND / 8MB 截断）待验证 |
| Windows 能否编译 iOS | ✅ **能编 klib，不能链接**（2026-10-10 实测）：`compileKotlinIosArm64` / `compileKotlinIosSimulatorArm64` 在 Windows BUILD SUCCESSFUL；链接与运行仍需 macOS + Xcode（见 A1-bis） |
| iOS 已可用 | ❌ 仍**没有 Xcode 工程**；`AppFile` 的 iOS 实现未写（A3）；`binaries.framework` 未声明（A0-1）；`Info.plist` 键未加（A0-2） |

#### wasmJs 编译门禁（2026-10-10 新增，`ca95feb`）

**为什么需要**：`:miuix` / `:backdrop` 的 `skikoMain` 此前只被 jvm 目标复用，
从未针对非 JVM 目标编译过，而 `checkKmpPurity` 只能扫**已知模式**，未知 API 查不出来。

**为什么是 wasmJs 而不是 linuxX64**：实测证伪 —— CMP 1.12.0 不发任何 linux 目标。
本机 konan 虽有 `x86_64-unknown-linux-gnu-gcc` 交叉工具链 + `llvm-21`，但没有可链的 klib 变体。
wasmJs 同为非 JVM 目标（没有 `kotlin.jvm.*` 默认导入），因此同样能拦
`@Volatile` / `synchronized` / `Dispatchers.IO` / `System.currentTimeMillis` 这类污染。

**用法**：
```bash
./gradlew -Pnexio.wasm=true checkKmpWasmJs     # 首次需联网 + PATH 中有 Node
./gradlew checkKmpWasmJs                        # 不给 property 时打印提示，不报错
```

⚠ **两条限制，务必知道**：
1. **默认关闭**。只要 wasmJs 目标存在于构建中，Kotlin/Wasm 插件就会在**配置阶段**
   为 `kotlinWasmNodeJsSetup` 注册 Distributions 仓库，与 `settings.gradle.kts` 的
   `FAIL_ON_PROJECT_REPOS` 硬冲突：
   `"repository 'Distributions at https://nodejs.org/dist' was added by unknown code"`
   这是**配置期失败**，与挂不挂 check 无关 —— 连 `--offline` 的 Android 门禁都跑不了。
   把 `WasmNodeJsEnvSpec.download` 置 `false` 也无效（注册发生在读取 `download` 之前）。
2. **开启期间跑不了 `:backdrop:check` / `:miuix:check`**（同一冲突）。
   要跑 check 就回到默认态（不加 property）。

**它已经抓到的 3 个真实问题**（静态检查全都没查出来，**在 iOS 上同样会炸**）：

| # | 问题 | 位置 |
|---|---|---|
| 1 | 漏声明 `org.jetbrains:annotations` → `@Language("AGSL")` 共 8 处报 `Unresolved reference 'intellij'`。`:miuix` 一直有声明，`:backdrop` 漏了 | `RuntimeShader` / `RuntimeShaderCache` / `RenderEffect` / `Shaders` |
| 2 | **未使用**的 `import androidx.compose.ui.graphics.asImageBitmap` —— 该 API 在 wasmJs 上不存在，仅 import 就足以编译失败 | `DrawBackdropModifier.kt:16` |
| 3 | `skikoMain` 未接给 `wasmJsMain` / `iosMain` → 10 处 `no actual declaration` | 两个 build.gradle.kts |

第 3 条即本计划 C 节第 2 条原本预警的坑，**现已闭环**。

> **历史教训（为什么值得反复强调）**：`:core` 一度只有 android + jvm 两个 **JVM** 目标，
> `compileCommonMainKotlinMetadata` 是 `SKIPPED`，commonMain 从未被平台中立的 stdlib
> 检查过。于是 6 类 JVM 专有 API（`@Volatile` / `synchronized` / `Dispatchers.IO` /
> `System.currentTimeMillis()` / `String.format()` / `String.toByteArray()`）共 13 处
> 一路绿灯，**真正编 iOS 时会全部失败**，而 Android 侧毫无察觉。
>
> 结论：**「能编过」必须问清楚是「哪个目标能编过」。**

### E. 提交前请跑这几条

```bash
# 默认态（不加任何 property）—— 这三条是红线
./gradlew :core:compileKotlinLinuxX64 :core:compileTestKotlinLinuxX64   # commonMain/Test 平台中立
./gradlew checkKmpPurity                                                 # 已知 JVM 专有 API 静态扫描
./gradlew :androidApp:assembleDebug                                      # Android 零回归（红线）

# 或合并成一条（--offline，模拟 CI）
./gradlew :core:check :miuix:check :backdrop:check --offline

# 动了 skikoMain / commonMain 时，额外跑非 JVM 编译门禁（需联网）
./gradlew -Pnexio.wasm=true checkKmpWasmJs
```


---

## 📍 当前进展（更新于 2026-10-10 · `master` `6367c55` + 未提交的存储层收口改动）

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
| `:core` | 34 (+15 平台实现) | ~9,365 | common / android / **jvm / linuxX64(门禁)** | **整个数据层**（课程/时间配置/节假日/课表名/文件夹/备份/WebDAV）+ 9 套跨平台抽象 |
| `:backdrop` | 64 | 5,458 | common / android / skiko | KMP 化，含 edgelight + capsule；**skikoMain 未针对 Native 编译过** |
| `:miuix` | 103 | 26,464 | common / android / skiko | KMP 化；**skikoMain 未针对 Native 编译过** |
| `:androidApp` | 148 | 69,983 | android | Android-only，**剩余迁移主体** |

`:core` 已有的 8 套跨平台能力：`NexioLog`（日志）/ `KeyValueStore`+`AppStorage`（存储）/
`HttpService`（网络）/ `JsonSupport`（JSON）/ `PlatformInfo`（设备信息）/ `AppFile`+`AppFiles`（文件）/
`ioDispatcher`（调度器 —— 不用 `Dispatchers.IO`，它在 Kotlin/Native 上是 `internal`）/
`synchronizedOn`（互斥 —— Native 暂时直通，见「当前进展 ⑩-c」的已知缺口）。

**节假日纯逻辑集群已整体下沉**（⑨）：`CourseScheduleDateBounds` / `TeachingWeekReorganization` /
`HolidayCourseExclusion` / `HolidayCountdown` / `HolidayEntry` / `HolidayTypes` / `DateExt`
—— 即「日期边界计算 + 调休改周映射 + 假期课程剔除 + 假期倒计时」这条链现在完全跨平台。
只剩存储层 `HolidayManager` 留在 `:androidApp`。

**`data/school/` 已整体下沉**（`SchoolIndex` / `SchoolRepository` / `ScriptRepository`），
`:androidApp` 侧该包目录已清空 —— 学校索引与脚本下载这条链现在完全跨平台。

**编译验证**（务必按目标区分，别笼统说「编得过」）：
- `:core/commonMain` → `compileKotlinLinuxX64` 通过 ⇒ **Native 目标也能编**（`commonTest` 同）
- `:backdrop` / `:miuix` 的 `commonMain` + `skikoMain` → 只有 `compileKotlinJvm` / `compileAndroidMain`，
  **从未针对 Native 编译**；平台专有 API 靠 `checkKmpPurity` 静态兜底
- `:androidApp:assembleDebug` 通过（APK 19.96MB）

### ✅ 已完成批次 · 模块拆分（`:miuix` 建立与反向依赖解除）

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
| 接线层 | 逐调用点的 App 策略 | [EdgeLightBindings.kt](androidApp/src/main/java/com/haooz/chedule/ui/utils/EdgeLightBindings.kt)，14 个调用点零改动 |

> `ProgressiveBlurTopBar` 有 29 个调用点 —— 走 CompositionLocal 后**一行调用点都不用改**。
> 遇到「调用点很多 + 值只有一个来源」时优先用 CompositionLocal。

### 🔴 本轮修掉的两个「编译全绿但功能失效」问题

这两类问题的共同点：**编译永远通过，只有真机能发现**。已写进 memory。

**1. OverlayDialog 的模糊与描边整体消失**
`:androidApp` 里有**两个同名** `DialogContentLayout`：App 定制版（394 行，含 blur+edgeLight）与
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

### ✅ 已完成批次 · 数据层下沉与定位修复

**④ 文件系统抽象 + `SchoolRepository` 下沉**

新增 `interface AppFile` + `AppFiles.init(root, readAsset)`（注入式，与 `AppStorage` 同模式）。
选注入而非 expect/actual 的原因见「阶段 3.1」：这样 `:core` 的 commonMain 保持**零平台实现**，
`linuxX64` 门禁才编得过；Android 侧继续用 `java.io.File`（`FileAppFile`，行为零变化）。

顺带把「自动创建父目录」收进 `writeBytes` —— 原来每个调用点都手写 `parentFile?.mkdirs()`。

`SchoolRepository`（85 行）下沉 `:core`。改的是**落盘路径**，所以补了 9 个用例验证行为等价：

| 用例 | 验证点 |
|---|---|
| 引导写入路径与迁移前一致 | `repo/index/school_index.pb` **逐字未变** + 父目录自动创建 |
| 本地已有索引时不再读内置资源 | 「存在即跳过」分支仍成立（计数验证只读 1 次） |
| 首次使用从内置资源引导并解析 | 用**真实 248 校生产索引**，结果与直接解析一致 |
| 内置资源缺失 / 索引损坏 | 安全降级为 null + 打日志，不抛给 UI |
| `getSchools` 过滤与排序 | 分类过滤 + `initial.uppercase()+name` 排序键不变 |

**⑤ 修复：长按课程卡片的浮层与落点错位**

根因是「节次顶部 Y」在项目里有**三套算法**，其中两套（手写公式）漏掉**特殊块挤占**：

| 位置 | 数据来源 | 含特殊块 |
|---|---|---|
| 卡片实际排版、空节次长按、落点高亮 | `grid.sectionTop` | ✅ |
| ~~课程卡长按浮层锚点~~ | 手写公式 | ❌ → 已改走 `grid.sectionTop` |
| ~~落点命中测试 `computeDropTarget`~~ | 手写公式 | ❌ → 已改 |

表现：有特殊块时长按浮层**整体偏上**（偏移量 = 该节次上方的特殊块总高，约半个卡片时
就是「偏上 50%」），且「高亮在哪格」与「实际落到哪格」错开。

修法：把权威的 `specialGrid.sectionTop` 接入 `ScheduleGridGeometry.sectionTopDp`，
浮层锚点、吸附落点、命中测试统一走它；拖动高度改用 `courseVisualHeightPx`（含分界缝与特殊块），
不再写死 `sectionCount × sectionHeight`。

> ⚠ 这套公式差异**只在存在特殊块时**才产生偏移。默认作息 `specialBlocks = null`
> （即默认没有），所以不是所有用户都会遇到。

**⑥ `ScriptRepository` 下沉 + HTTP 层补齐响应体上限**（提交 `7c88e5a`）

`ScriptRepository`（249 行）不只差 `Context`/`File`/OkHttp，还带两个 JVM 专有依赖
（`java.io.IOException`、`ByteArrayOutputStream`）。迁移时**逐字移植**了这些易错逻辑，
未做任何「顺手优化」：防盗链判定、签名链接提取、版本比较（**字符串比较**，不是数字提取）、
`remoteBase` 的 `.git` 后缀处理、`updateAll` 的三分支与全部 14 条日志文本。

**HTTP 层补齐 `maxBytes`（这一步不能省）**：原实现用 `byteStream()` 边读边计数、
超过 8MB 立即中止。而「一次性读入内存」的 `HttpService` 无法表达这件事 ——
直接迁移会把「读取中停手」退化成「读完再检查」，**OOM 已经发生**。因此把上限下推到接口：

```kotlin
suspend fun get(url, headers = emptyMap(), maxBytes: Long = -1): HttpResult
class HttpResult(code, bytes, truncated = false)   // 超限返回 truncated=true，不抛异常
```

顺手修掉：这两个仓库原本各自 `new OkHttpClient()`（第 11、12 套连接池），现在复用 `HttpService`。

### 🎯 UI 导航改造（阶段 5 前置）· 增量 1+2 已完成（2026-10-09）

> **增量 1**（`0ce3c8a`）：路由器基础设施 + AboutActivity 宿主化（4 条路由），删 3 个 Activity
> **增量 1.5**（`a1e7f7e`）：修复返回丢滚动状态（`SaveableStateHolder`）
> **增量 2**（`90b0754`）：宿主迁到 `MainActivity`，`AboutActivity` 删除。Manifest 23 → **19** → **14**（增量 3 迁 5 个设置类薄壳）→ **10**（增量 4 修闪退）→ **6**（增量 5 迁 AI导入/小部件/提醒/节假日/WebDAV）

**跨端的硬前提：CMP 在 iOS 上跑在 `UIViewController` 里，没有 Activity。**
更关键的是 —— **导航状态原本活在 Android 的 Activity back stack 里，iOS 拿不到**。
只要还用 Intent 跳 Activity，那些 Screen 就永远共享不了。
所以「单宿主 + 显式路由表」不是风格选择，是必要结构。

#### 现状实测（改造前）

| 项 | 数字 |
|---|---|
| 注册的 Activity | **23**（源文件 20；其中 **17 个是纯壳**：`enableEdgeToEdge` + `setContent { Theme { XxxScreen() } }`，屏幕本体已在隔壁 `*Screen.kt`、已是 Compose） |
| 主流程 | **已经是单 Activity**：`MainActivity.CourseScheduleApp()`（5903 行）+ 3 页 pager |
| Compose Navigation | **0**（没有 `NavHost`/`NavController`）→ 缺一层导航抽象，无论如何要新写 |
| 导航方式 | `Intent(context, XxxActivity::class.java)` + `startActivity`；`finish()` 22 处；**Intent extras 47 处** |

**真正的成本不在那 20 个壳，在平台专有面**：
`WindowInsets`/系统栏 **84** ｜ `Toast` 31 ｜ `LocalConfiguration` 15 ｜
`LocalActivity`/`LocalHapticFeedback` 15 ｜ `LocalContext` 12 ｜ 文件选择 4。

#### 增量 1 做了什么

新增 `ui/navigation/`：

| 文件 | 作用 |
|---|---|
| `AppRoute` | 路由表。**`id` 是持久化契约**（`rememberSaveable` 用），已发布的不要改 |
| `AppRouter` | 返回栈。`rememberSaveable` 保存**当前栈**（用初始栈会在旋转后弹回首页）；`backOrExit(onExit)` 给顶栏返回按钮用 —— 只调 `popBack` 在栈底会"失灵" |
| `AppNavHost` | 渲染当前路由 + 接管系统返回 + 前进/返回转场 |
| `AppRouteContent` | 路由 → 页面的映射表。**宿主不关心有哪些页面**；阶段 5 搬 UI 时这个文件整体搬走 |

新增 `ui/components/DocumentPageScaffold`：隐私政策 / 开源协议 / 更新日志三个 Activity 壳
**逐字相同**（各 96 行），抽成一个「折叠大标题顶栏 + 玻璃返回按钮 + 内容采样层」脚手架。

关于页宿主化：`AboutActivity` 里内联的 1200 行 `AboutScreen` 抽成独立文件，
`AboutActivity` 只剩 21 行宿主；**删掉 3 个 Activity**（Manifest 23 → **20**）。

#### ⚠️ 为什么**手写**路由表而不是引入 Navigation 库

1. `androidx.navigation:navigation-compose` 是 **Android-only**，阶段 5 还得换成 CMP 版 —— 白做一遍；
2. 而 CMP 那个 artifact（`org.jetbrains.androidx.navigation`）**本机离线缓存里没有**（只有它的
   `lifecycle` / `savedstate` / `navigationevent` 兄弟包），加不上；
3. 路由表本身几十行，而且**将来要整体搬进共享模块**，手写反而没有「换库」这一步。

> 这与「不引入 Ktor」是同一个判断：**不为了一点便利，把可控的东西换成会连带升级核心依赖的黑盒。**

#### ⚠️ 已知未做：预测性返回的**动画**

拆 Activity 时系统给的是**跨 Activity 的预测动画**；单宿主下系统不再提供（没有第二个 Activity
可供动画），需要 `PredictiveBackHandler` + 自己驱动进度。这一版先用 `BackHandler`
保证返回**功能**正确，**动画留到后续增量** —— 迁移后这 4 个页面的返回动画会变成
`AppNavHost` 里那个「前进右滑入 / 返回右滑出」的自绘转场，不是系统预测动画。

#### 📌 后续增量（按这个顺序）

1. **把宿主迁到 `MainActivity`**（终点）。它有 `handleBackNavigation` / `onNewIntent` /
   widget 深链等 Android 耦合，所以要单独一个增量做；`AppRoute` 与 `AppRouteContent` 原样搬过去。
2. 按「软柿子优先」把剩下的薄壳逐个变成路由（`ChangelogActivity` 这类已删，
   剩 `LicenseActivity` 同类；`WidgetIntroActivity` 155 行、`CourseReminderActivity` 155 行…）。
3. **平台专有面抽接口**：等真搬某个 screen 时再抽它用到的那几个，
   **别提前造全套**（与「别提前造 `Notifier`/`ScreenInfo`」同一条）。
4. **WebView / Activity 结果类页面先不动**：`EducationalImportActivity`（WebView +
   `@JavascriptInterface` 桥接）、`AiImportActivity` 先当「平台功能页」留着，用 route 跳过去再回来。

#### 增量 2 的关键设计：主界面走「常驻底座」而不是「路由条目」

`AppRouter` 的栈**从空开始**，只有 `navigate()` 之后才有子页叠加在主界面上。
主界面 `CourseScheduleApp` **永远保持组合、不参与路由的 save/restore** ——
这与拆 Activity 时代「跳到子页时主 Activity 只是 stop 没有销毁」的语义完全一致。
若把主界面也塞进 `AnimatedContent` + `SaveableStateHolder`，
`rememberSaveable` 能救回来，但 `remember { mutableStateOf }` 之类救不回来
—— `CourseScheduleApp` 里有大量这类状态（pager 位置 / 壁纸映射 / …），
丢了就是「切到设置再回来 pager 位置变了」。

`AppRouter` API 相应简化：
| 属性/方法 | 语义 |
|---|---|
| `hasOverlay` | 是否有子页叠加（false 时 `BackHandler` 不拦截，返回键交给 Android 宿主） |
| `current` | 当前叠加的路由（`AppRoute?`），没有子页时为 null |
| `navigate(route)` | 入栈 |
| `popBack()` | 出栈；栈空返回 false，返回键不拦截 |

删掉了 `AppRoute.Main` —— 主界面不是路由，是常驻底座。

新增 `LocalAppRouter`（CompositionLocal）：让 `SettingsScreen` / `TabletSettingsScreen`
这些深处的 Composable 能发起路由跳转，不经过 Activity 引用。
⚠ `LocalAppRouter.current` 是 `@Composable` 调用，**必须在组合层取**，
不能放进 `onClick` lambda 里。

`AppNavHost` 的 `BackHandler` 改为 `enabled = router.hasOverlay`：
- 有子页 → 路由器出栈
- 没子页 → 不拦截，**Android 宿主的预测性返回 /「退出即隐藏后台」照常生效**

#### 📌 后续增量

1. 按软柿子优先把剩下的薄壳逐个变成路由（`WidgetIntroActivity` 168 行、`CourseReminderActivity` 155 行、
   `PreferenceSettingsActivity` 104 行、`UpdateSettingsActivity` 100 行…）
2. **平台专有面抽接口**：等真搬某个 screen 时再抽它用到的那几个，
   **别提前造全套**（与「别提前造 `Notifier`/`ScreenInfo`」同一条）
3. **WebView / Activity 结果类页面先不动**：`EducationalImportActivity`（WebView +
   `@JavascriptInterface` 桥接）、`AiImportActivity` 先当「平台功能页」留着，用 route 跳过去再回来

> **判据**：某个 Screen「可以共享了」= 它的 Composable 不依赖任何 `android.*` /
> `LocalContext` / `LocalConfiguration` / Activity 回调。
>
> **两个坑**：① 别把「单 Activity」理解成「所有东西塞进一个 NavHost」；
> ② **widgets / 提醒也在 `startActivity(MainActivity)`** —— 宿主同时是「非 UI 入口」，
> 那 47 处 extras 里有一部分就是它。

### ✅ 已完成批次 · ⑬ `ScheduleBackup` 下沉 `:core` —— WebDAV 收敛到 `HttpService`（2026-10-09）

**`:androidApp/data` 只剩 `ScheduleAppearance`（含 Bitmap）与 `WallpaperTransform`（依赖 Compose `Offset`）
及两个按决定必须留在 `:androidApp` 的 Android actual。**

| 原来 | 现在 |
|---|---|
| `Context` + `getSharedPreferences("webdav_config", …)` | `AppStorage.store("webdav_config")`（文件名 + 4 个键名逐字未变） |
| **OkHttp**（`OkHttpClient` / `Request` / `Credentials.basic`） | **[HttpService]** —— `request()` 支持 PROPFIND / MKCOL / PUT / GET / DELETE，超时 10/30 与原来一致 |
| `SimpleDateFormat` ×4 | `DateExt` 的 `formatCompactStamp` / `formatDisplayDateTime` / `parseCompactStamp` |
| Gson（备份外层信封） | `JsonSupport` |
| `Thread.sleep` | `delay`（本来就在协程里） |
| `Dispatchers.IO` | `ioDispatcher`（Kotlin/Native 上没有 `Dispatchers.IO`） |
| `CourseReminderHelper.onHolidayDataChanged(context)` | 构造时注入的回调（`:core` 不能反向依赖 `:androidApp` 的提醒模块） |

新增 `:core` 的 `basicAuthHeader()`（stdlib `Base64`，等价 OkHttp 的 `Credentials.basic`）。

> **这一步的实际价值**：WebDAV 原先直接用 OkHttp，是「Native 上跑不了」的一处死角。
> 收敛到 `HttpService` 之后，**iOS 的 WebDAV 备份/恢复只等 `HttpService.native` 落地**，
> 不再有第二处网络实现要写。

#### ⑬-a 请求形状逐条照抄（这是唯一能防「编译全绿、真机才发现」的手段）

本地起不了 WebDAV 服务器，所以新增 `WebDavManagerContractTest`（12 用例，未入库）：
塞一个**记录请求的假 `HttpService`**（`http` 字段用 `internal var` —— 与 `StatsReporter` 同一套路），
把发出去的请求逐条钉死：

| 用例 | 断言 |
|---|---|
| `testConnection` | PROPFIND + `Depth: 0` + 正确 `Authorization`；401 / 404 分支文案 |
| `backupAllData` | 请求序列 `PROPFIND base → PROPFIND backups → PUT`；URL；`contentType`；body 信封形状（`version` / `backupTime` / `backupId` / `data`，id 形如 `backup_yyyyMMdd_HHmmss`） |
| 目录不存在 | 走 `MKCOL`；**405 视为已存在**（原实现就是这么判的） |
| `listBackups` | `Depth: 1`；只认 `backup_` 前缀的 `.json`（喂真实 PROPFIND XML） |
| `restoreLatestBackup` | 取**最新**那个（按 id 排序）、写进仓储、**并触发回调**；404 / 空列表分支 |
| `deleteBackup` | `DELETE`；404 也算成功 |
| `basicAuthHeader` | 与 `Credentials.basic` 的等价形式一致 |
| 时间戳格式 | 与 `SimpleDateFormat` 对拍 |

#### ⑬-b 两处**有意的**差异（都写进了代码注释与测试）

1. OkHttp 的 `response.message`（reason phrase）在 `HttpResult` 里没有对应物 →
   `testConnection` 的失败文案由「服务器返回: `<code> <message>`」变成「服务器返回: `<code>`」。
2. `SimpleDateFormat` 默认 **lenient**，会把不存在的日期滚过去（2026 非闰年，
   `20260229` → 3 月 1 日）；本实现**严格**，返回 null → `BackupInfo` 回退显示原始 id。
   备份 id 只会由 `formatCompactStamp` 从真实日期生成，格式坏只可能是文件被改名，
   **显示原始 id 比显示一个滚出来的假日期更诚实**。

#### ⑬-c 一个**不做**的决定：`WallpaperTransform` 留在 `:androidApp`

它只依赖 `androidx.compose.ui.geometry.Offset`（61 行纯几何数学）。曾试着搬进 `:core`，
但 `:core` 没有 Compose 依赖 —— 为 61 行数学把 Compose 拉进数据模块不划算
（而且 CMP 依赖可能顶掉协程版本，正是「不引入 Ktor」那条决定要防的事）。
**它的归属是阶段 5 的 `:ui-shared`，不是 `:core`。**
`ScheduleAppearance` 同理（Bitmap 解码/缩放/压缩 + LruCache），属 UI/媒体层。

`:core:jvmTest` **230 → 241 全绿**。

### ✅ 已完成批次 · ⑫ `CourseRepository` 下沉 `:core` —— **数据层全部完成**（2026-10-09）

**`:androidApp/data` 只剩 `ScheduleAppearance` / `ScheduleBackup` / `WallpaperTransform`
与两个按决定必须留在 `:androidApp` 的 Android actual（`SharedPreferencesStore` / `FileAppFile`）。**
`TimeConfigSnapshotParser.kt` 一并删除 —— 它的注释写明「等阶段 2 换成 kotlinx.serialization
之后应当移回 `TimeConfig.Companion`」，现在就是那个时候。

| 原来 | 现在 |
|---|---|
| `Context` + `SharedPreferences` | `AppStorage.store(PREFS_NAME)`（文件名 `course_schedule_prefs` 未变） |
| `getInstance(context)` / `invoke(context)` | 无参单例（**59 处调用点**去掉实参） |
| 单例初始化 `synchronized(this)` | 已有的 `synchronizedOn` |
| Gson（21 处） | `ScheduleCodec`（⑪ 备好的） |
| `System.currentTimeMillis()` ×12 | `Clock.System.now()` |
| `String.format(Locale.ROOT, "%04d/%02d/%02d", …)` ×3 | `DateExt.formatSlashDate()` |
| `java.util.Calendar` | `todayLocalDate()` |
| `java.util.UUID.randomUUID()` | `randomUuidV4()`（`PlatformInfo` 早就备好了） |
| `Build.BRAND` / `MANUFACTURER`（小米检测） | `currentDeviceInfo()` |
| `CopyOnWriteArrayList`（监听器多播） | `@Volatile` 不可变列表 + 写时复制（读走快照、写不阻塞读，语义一致） |
| `MutableMap.putIfAbsent` / `toSortedSet` | 等价展开（都是 JVM 专有） |
| `prefs.getString(k, null)` ×12 | `KeyValueStore.getStringOrNull(k)`（新增扩展） |
| `prefs.edit(commit = true)` ×8 | `KeyValueStore.edit {}`（都是提交即写盘） |

新增 `:core` **`AppearancePrefs`**：把外观的 prefs 文件名与键名下沉 ——
`CourseRepository.isCombinationBackupKey` 要用它们，而 `:core` 不能反向依赖
`:androidApp` 的 `ScheduleAppearance`。`:androidApp` 侧用 `const val` 转发，取值同源、不会抄错。

#### ★★ ⑫-a 顺带修掉两个**真实缺陷**（与迁移无关，被真实备份测出来的）★★

真实备份导入时**被自己的校验器拒绝**，报 `belongs to an unknown schedule`：

| # | 残留键 | 成因 |
|---|---|---|
| 1 | `schedule_folder_map` | **代码里已无任何引用的老版本遗留键**，但还在老用户 prefs 里，会被 `exportAllPreferences` 原样导出 → 导入必被拒 |
| 2 | `schedule_time_config_{已删除的课表名}` | 删课表清的是 `schedule_{名}_*`，而绑定键用的是**另一个前缀** `schedule_time_config_`，没被一起清掉。真实备份里有 **13 个**这样的残留名（`schedule_names` 只有 5 个） |

后果一样：**用户连自己的备份都恢复不了**（这份真实备份正好命中）。
已把校验器改成接受这两类残留 —— 恢复流程本来就会先删掉全部 `schedule_*` 前缀再按备份写回，
残留写回去与现状一致，无害。

#### ⑫-b 安全网：文档「阶段 2.3」要求的那条 round-trip 终于能测了

以前测不了 —— `exportAllPreferences` / `importAllPreferences` 在 `:androidApp`，而 `:androidApp` 没有测试源集。
搬进 `:core` 之后，**这是搬它最大的回报**。新增 `FullBackupRoundTripTest`（3 用例，未入库）：
把真实备份灌进 `InMemoryKeyValueStore` 后

1. `exportAllPreferences()` 能**还原出同一份备份**（键集合 + 逐键值；课程串是逐字透传）
2. **导出 → 导入 → 导出：二次、三次导出与首次完全一致** ← 文档要求的正是这条
3. 导入后课表名 / 23 门课 / 3 个时间配置 / 作息方案都读得出来

> 比较方式用「语义相等」：数字统一 Double、Set 转 List 后比 Map。
> 按 ⑪-a 的结论，字段顺序不是契约，写逐字断言只会被无意义的顺序差异绊住。
> ⚠ 测试里 `holiday_settings` 必须用**另一个** store —— 节假日数据在独立的 prefs 文件里，
> 混在一起会让 `exportAllPreferences` 多出 `entries_2026` 等原始键。

`:core:jvmTest` **227 → 230 全绿**。

### ✅ 已完成批次 · ⑪ R2 数据层：模型编解码的显式字段清单（2026-10-09）

**为什么先做这个而不是直接搬 `CourseRepository`**：`CourseRepository` 用 Gson 反射序列化
`Course` / `TimeConfig` / `ScheduleFolder`，而 Gson 在 Kotlin/Native 上不存在。
**不先把这几条序列化通道换掉，`CourseRepository` 根本搬不动**（在 `:core` 里它连
`gson.toJson(courses)` 都写不出来）。所以这是搬它的前置，不是绕路。

新增 `core/.../data/ScheduleCodec.kt`：

| 能力 | 说明 |
|---|---|
| `Course.toJsonMap()` / `courseFromJsonMap()` | 17 个字段的显式清单 |
| `ScheduleFolder.toJsonMap()` / `scheduleFolderFromJsonMap()` | 3 个字段 |
| `encodeCourses` / `decodeCourses` | 语义对齐 `gson.toJson` / `fromJson<List<Course>>` |
| `encodeScheduleFolders` / `decodeScheduleFolders` | 同上 |
| `encodeStringList` / `decodeStringList`、`encodeLongList` / `decodeLongList` | 课表名、time_config id |
| `toJsonElementWithoutNulls()` | 复刻 Gson 的 `serializeNulls = false`（null 字段省略） |

#### ⑪-a 为什么是**手写**而不是 `@Serializable`

本机（离线）的 Gradle 缓存里**没有 `kotlin-serialization` 编译器插件**
（`find ~/.gradle -iname "*kotlin-serialization*"` 查无此物），
加不上 `org.jetbrains.kotlin.plugin.serialization`。

手写字段清单正好就是文档对 R2 的要求（「需 `@Serializable` + **显式字段清单**」）里
那个「显式清单」，而且不引入编译期依赖。**将来若装上插件，可以平滑换成 `@Serializable`**
（字段名已经对齐，不需要 `@SerialName`）。

#### ⑪-b 与 Gson 的对齐点（逐条实测/推演）

| 行为 | Gson | ScheduleCodec |
|---|---|---|
| `null` 字段 | **省略**（`serializeNulls=false`） | 省略（`toJsonElementWithoutNulls`） |
| 默认值 | **照写**（只跳 null） | 照写（`encodeDefaults = true`） |
| 缺字段反序列化 | 非空字段变 **null**（`UnsafeAllocator` 绕开构造器） | 取**零值**（0/""/emptyList/null），由调用方的 `sanitizeCourses` 兜成同一结果 |
| 数字 | `Long` 原样、无小数点 | 同 |
| `<>&='` | 转义成 `\u003c` 等 | **不转义**（可接受差异） |

#### ⑪-c 安全网：把**真实 Gson** 拉进来当基准

`core/build.gradle.kts` 的 `jvmTest` 加了 `libs.gson`（**仅测试编译，不进产物、不影响 `:androidApp`**），
新增 `ScheduleCodecGsonParityTest`（10 用例），用**真实备份数据**做差分对拍：

| 用例 | 断言 |
|---|---|
| 真实 `courses` 解码 | 与 `gson.fromJson<List<Course>>` **逐字段相等** |
| 真实 `courses` 编码 | 与 `gson.toJson` **语义相等**（解析回 Map 比较，与键顺序无关） |
| 编解码往返 | 稳定；二次编码与一次编码语义相同 |
| `null` 字段省略 | 真实数据无 `customStartTime`；编码后不得出现 `null`；有自定义时间的课必须写出两个字段 |
| 边界输入 | `[]` / `null` / 非法 JSON / 非数组，与 Gson 行为对齐 |
| `schedule_folders` / `schedule_names` / `Long` 列表 | 同 Gson |
| `time_config_ids` | 证明它是逗号分隔、**不是 JSON** |

`:core:jvmTest` **210 → 220 全绿**。

> **本批不改任何 app 行为** —— 只是新增可用的编解码，切换留到搬 `CourseRepository` 时。
> 这样 R2 的风险被单独隔离在一批可验证的改动里，不会和「搬模块」混在一起。

#### ⑪-d `TimeConfig` 一族（第二批补完，提交 `c262f44`）

`TimeConfig` / `TimeRoutine` / `SpecialBlock` / `SpecialItem` 的编解码，以及
`encodeTimeConfig` / `decodeTimeConfig`。嵌套三型**直接复用现有的 `fromRaw(Map)`** ——
它们本来就是为「Gson 泛型丢失 + `UnsafeAllocator` 置 null」写的，且 `safeXxx` 走的也是它们，
复用等于与 `sanitize` 路径同源，避免两套归一逻辑打架。

> ⚠⚠ **本轮最重要的一条：Kotlin 默认值对 `TimeConfig` 是生效的。**
>
> 「Gson 绕开构造器」**只对没有无参构造器的类成立**，判据是**有没有必填参数**：
>
> | 类 | 有必填参数？ | 合成无参构造器？ | Gson 缺字段时给什么 |
> |---|---|---|---|
> | `Course` | ✅ 有（前 11 个无默认值） | ❌ | **零值 / null**（`UnsafeAllocator`） |
> | `TimeConfig` / `TimeRoutine` / `SpecialBlock` / `SpecialItem` | ❌ 全有默认值 | ✅ | **Kotlin 默认值** |
>
> 实测证据：`{}` 反序列化后 `shortBreak = 10`、`longBreakMorning = 20`、`morningStartHour = 8`
> —— 全是 Kotlin 默认值，不是 0。
>
> ⇒ 所以 `timeConfigFromJsonMap` 用 **`TimeConfig()` 作底 + `copy` 只覆盖出现过的字段**，
> 这正是「无参构造器 + 按需覆盖」的等价写法，也不用把 25 个默认值再抄一遍。
> ⇒ 反过来，`Course` 侧**必须取零值**（`courseFromJsonMap` 就是这么写的）。

> ⚠ **另一个坑**：`toJsonElementWithoutNulls` 必须**递归**。第一版只跳顶层 null，
> 结果嵌套的 `"items":null` 照样被写出去。数组元素里的 null 要**保留**
> （Gson 的 `serializeNulls` 只影响对象字段，集合元素照写 null）。

> 🔎 **发现一处既有缺陷，并有意改进**：`{"routines":null}` 这种 JSON 会让**现有 Gson 路径直接 NPE**
> （Gson 把 null 塞进非空字段 → `sanitize` 内部 `copy` 炸）。手写解码把「显式 null」与
> 「字段缺失」都归一成 `emptyList()`，能正常走完 `sanitize`。
> **这是更健壮、且不改变任何正常数据的语义**，已单独写用例钉住并标注为有意为之。

**新增 7 个用例**（`:core:jvmTest` 220 → **227**）：真实 `time_config` 解码 + `sanitize`
与 Gson 完全一致、编码语义一致、往返稳定、**20 种缺字段输入的恢复路径逐字对齐**、
`items` 为 null 时字段省略、非法输入、上面那处健壮性差异。

### 🔬 R2 前置调研 · 基于**真实用户备份**（2026-10-09）

拿到一份真实全量备份 `全部备份_20261009_155825.json`（166 个键 / 57343 字节 / 180 行），
做了三件事：**回归验证已完成批次、量出 R2 的硬性配置要求、把备份变成可复用的回归夹具**。

#### 🔬-a 已完成批次（⑨⑩）在真实数据上**逐字通过**

| 断言 | 结果 |
|---|---|
| 真实 `entries_2026`（530 字节 4 条）→ `load()` → `toJson()` → 写回 | **逐字相同** ✅ |
| 真实 `teaching_week_reorganizations`（`{"schema_version":1,"rules":[]}`）→ `decode` → `encode` | **逐字相同** ✅ |
| 整份备份 → `decodeBackupData` → `restoreBackupData` → `exportBackupData` → 再 decode | 等价 ✅ |
| 真实 `holiday_end/before_course_exclusion` | 解码正确 ✅ |

> 这是比单测字面量更强的证据：前面几批换的是**序列化实现**，这份是**真机跑出来的存量数据**。

#### 🔬-b `JsonSupport` 能逐字重放 Gson 的 pretty 输出（R2 的重大利好）

真实备份是 `GsonBuilder().setPrettyPrinting()` 的产物（2 空格缩进）。实测：

```kotlin
Json { prettyPrint = true; prettyPrintIndent = "  " }.encodeToString(JsonElement.serializer(), element)
```

**与原文逐字一致（57343 字符 / 180 行全等）** —— 也就是说「全量备份」这条通道换掉 Gson 之后
**文件字节完全不变**。这条结论直接消掉了 R2 里最大的一块不确定性。

#### ⚠️-1 【实测推翻】字段顺序**不是**兼容契约，别去对齐它

真实备份里 `_courses` 的字段是**字母序**（`classroom, colorRes, dayOfWeek, …`），
而 `Course` 的声明顺序是 `id, name, classroom, …`。用**真实 Gson 2.11.0** 跑探针实测：

```
Gson 输出: {"id":"c1","name":"…","classroom":"A101","teacher":"…","dayOfWeek":1,…}
Map 输出 : {"zebra":1,"apple":2,"mango":3}
```

⇒ **Gson 按声明顺序输出字段，且保留 Map 插入顺序，绝不排字母序。**
那个字母序是 **release 构建被 R8 重排字段**的产物，随构建而变。

⇒ **兼容性判据 = 字段集合 + 字段名 + 值的语义，不是逐字相等。**
JSON 对象按规范无序、两端都按名字取值，顺序怎么排都能互相读。
**做 R2 回归时用「解析回 Map 再比较」，不要写逐字断言**（否则会被无意义的顺序差异绊住）。

> 附带结论：全量备份是 **prefs 级透传**，导入时把原始字符串直接写回 prefs、不解析，
> 所以**整份文件**的逐字 round-trip 依然成立（已实测 ✅）；
> 但一旦 app 重新保存某个课表，那条 `_courses` 串就会按当前构建的字段顺序重写。

#### 🔴-c R2 的 kotlinx 配置必须这样写（漏一条就静默改格式）

真实数据里 `schedule_{名}_courses` 的每门课只有 **15 个字段**，而 `Course` 有 **17 个** ——
少的正是 `customStartTime` / `customEndTime`（`String?`，非自定义时间时为 null）。

原因是 **Gson 默认 `serializeNulls = false`（null 字段直接省略）**，
而 **kotlinx-serialization 默认 `explicitNulls = true`（会写出 `"customStartTime":null`）**。
同时真实数据里 `isCustomTime: false`、`selectedWeeks: []` 都**写出来了**，
说明 Gson **会写默认值**（它只跳 null），所以 kotlinx 侧也不能关掉默认值输出。

⇒ R2 用的 `Json` 实例必须是：

```kotlin
Json {
    encodeDefaults = true      // Gson 写默认值（isCustomTime:false / selectedWeeks:[] 都在真实数据里）
    explicitNulls = false      // Gson 省 null（customStartTime/customEndTime 在真实数据里缺失）
    ignoreUnknownKeys = true
    coerceInputValues = true
}
```

> ⚠ 现有 `JsonSupport` 里那个 `json` 实例**没有**设 `explicitNulls`（默认 true），
> 且 `toJsonElement(map)` 对 null 值会产出 `JsonNull`（即写出 `null`）。
> **R2 不能直接复用那个实例**，要单独建一个「Gson 兼容档」。
> 受影响的通道：单课表备份、分享码、教务导入（`Course` / `TimeConfig` 的对象级序列化）。

#### 🔴-d Gson 的 HTML 转义（已确认的「可接受差异」）

Gson 默认 `escapeHtmlChars = true`，会把 `<` `>` `&` `=` `'` 写成 `\u003c` 之类。
kotlinx 不转义。**两者都是合法 JSON，解析回来是同一个字符串**，所以只影响字节、不影响数据。
本份备份里这些字符**一个都没有**，所以没暴露；但别的用户的数据可能命中
（例如课表名里带 `&`）。**R2 上线时把它记为「可接受差异」，不要试图去模拟 Gson 的转义**
（模拟反而容易出错，而且会让新写的文件继续背着这个历史包袱）。

#### 🔬-e 从真实数据量出来的字段清单（R2 显式字段清单的底稿）

| 通道 | 真实形态 | 字段数 |
|---|---|---:|
| `time_config_{id}` | JSON 字符串 | **25**：`id/name/quickTimeEnabled/classDuration/shortBreak/longBreak{Enabled,Morning,Afternoon,Evening,MorningSection,AfternoonSection,EveningSection}/morningStart{Hour,Minute}/afternoonStart{Hour,Minute}/eveningStart{Hour,Minute}/morningSections/afternoonSections/eveningSections/sectionTimes/sectionNames/specialBlocks/routines` |
| `schedule_{名}_courses` | JSON 字符串 | **15**（见 🔴-c） |
| `schedule_{名}_section_times` | JSON 字符串 | `{"morning_1":"08:00-08:45",…}` |
| `schedule_{名}_teaching_week_reorganizations` | JSON 字符串 | `{"schema_version":1,"rules":[…]}` |
| `schedule_names` / `shift_selected_schedules` | JSON 字符串数组 | — |
| `schedule_folders` | JSON 字符串 | `[{id,name,schedules:[…]}]` |
| `schedule_folder_map` / `time_config_ids` | **不是 JSON** | `{}` / `1,4,8`（逗号分隔） |
| `holiday_entries` / `holiday_end_course_exclusion` / `holiday_before_course_exclusion` | 对象 | ✅ 已下沉 `:core`（⑩） |

#### 📌 R2 的回归夹具已就位

`RealBackupRoundTripTest`（未入库）直接读这份备份，覆盖 4 个断言。
**下一步 `CourseRepository` 下沉之后**，`importAllPreferences` / `exportAllPreferences`
就能在 `:core` 里被测到，届时把「导出→导入→导出，二次导出与首次逐字一致」补上
（文档「阶段 2.3 数据兼容红线」要求的正是这条）。

### ✅ 已完成批次 · ⑩ `HolidayManager` 存储层下沉 `:core`（2026-10-09）

**`HolidayManager` 移入 `core/src/commonMain`，节假日整条链（存储 + 调休改周 + 课程剔除 +
倒计时）现在完全跨平台。** `:androidApp/data` 只剩 `CourseRepository` / `ScheduleAppearance` /
`ScheduleBackup` / `TimeConfigSnapshotParser` / `WallpaperTransform` 与两个 Android actual。

#### ⑩-a 逐个替换掉的平台依赖

| 原来 | 现在 | 说明 |
|---|---|---|
| `Context` + `getSharedPreferences(PREFS, MODE_PRIVATE)` | `AppStorage.store(PREFS)` | 文件名 `holiday_settings` **逐字未变** |
| `SharedPreferences` 形参（8 个 internal 重载） | `KeyValueStore` 形参 | 取值/写入 API 一一对应 |
| `androidx.core.content.edit {}` | `KeyValueStore.edit {}` | 语义相同：提交即写盘 |
| `org.json.JSONObject/JSONArray` | `JsonSupport` | 见 ⑩-b |
| Gson `JsonParser`/`JsonElement`/`TypeToken` | `JsonSupport` | 见 ⑩-b |
| `@Synchronized` / `synchronized(this)` | **新增** `synchronizedOn(lock)` | 见 ⑩-c |
| `System.currentTimeMillis()` | `Clock.System.now()` | 同为 Unix 纪元毫秒 |
| `CourseRepository(context)` | 调用方注入的日期解析回调 | 见 ⑩-d |
| `entriesByYear.toSortedMap()` | `entries.entries.sortedBy { it.key }` | `toSortedMap` 是 JVM 专有（`TreeMap`） |

另：`BACKUP_KEY` / `BACKUP_EXCLUSION_KEY` / `BACKUP_BEFORE_EXCLUSION_KEY` 三个常量从
`internal` 提升为 **`public`** —— 原先 `internal` 在本模块内可见，跨模块后 `:androidApp` 的
`CourseRepository` 就看不见了（与 `TimeConfig.fromRaw` 是同一类问题）。

#### ⑩-b `org.json` / Gson → `JsonSupport`：靠一个 `optRaw()` 保住语义

`parseStoredEntry` 原实现满是 `item.opt("date") as? String` 这类**类型判定**。
如果换成 `optString` / `optInt` 会**改变语义**（`optString` 把数字 `1` 变成 `"1"`），
合法数据会被判非法而**整行丢弃** —— 用户那一年的假期就静默没了。

所以新增 `JsonObject.optRaw(key)` / `JsonArray.optRaw(index)`，返回**原始值**
（`String` / `Double` / `Boolean` / `Map` / `List` / null，与 Gson 的 `ObjectTypeAdapter` 同形状），
于是那些 `as? X` 判断**逐字保留**。

`parseBackupEntry`（Gson 侧）则用「是不是 `JsonPrimitive` + `isString`」区分字符串与字面量
—— 与 Gson 的 `isString` / `isNumber` / `isBoolean` 三分法等价
（`isString == false` 时 `content` 只可能是数字 / `true` / `false` / `null`）。

#### ⑩-c `@Synchronized` 是这一批唯一的设计决策

它保护的是「读 prefs → 算 → 写 prefs」的复合操作。真实场景：
`NexioApplication.onCreate` 起一个后台 `Thread` 跑旧调休映射迁移，同一时刻主线程可能正在保存设置。
**丢了这把锁会出现「迁移结果覆盖用户刚保存的编辑」这类编译全绿、只有并发才发生的问题。**

但 **Kotlin/Native 没有 `synchronized`**（实测 `Unresolved reference`）。所以新增
`expect fun <T> synchronizedOn(lock: Any, block: () -> T): T`：

| 平台 | 实现 | 效果 |
|---|---|---|
| Android / JVM | `synchronized(lock)` | **与原来的 `@Synchronized` 逐字等价 —— Android 行为零变化（红线）** |
| Kotlin/Native | 直通 | **暂时没有互斥**，KDoc 里明确写成「已知缺口」而不是「等价实现」 |

> ⚠ **iOS 接进来之前必须回看这里**：如果 iOS 侧也会「后台迁移 + 前台保存」并发，
> 要给 nativeMain 换真实现（`kotlinx.atomicfu` 的 `SynchronizedObject`，或把复合写收敛到单线程）。
> `LockTest` 用 8 线程 ×2000 次「读→算→写」证明 JVM/Android 的锁**真的互斥**，不是空壳。

#### ⑩-d `migrateLegacyFollowDates` 的反向依赖

原实现内部 `CourseRepository(context)` —— `:core` 不能依赖还在 `:androidApp` 的 `CourseRepository`。
改为传入 `resolveDateForTeachingWeekDay: (week, weekday) -> LocalDate?`。
调用方（`NexioApplication`）拿不到仓储时**根本不调用本函数**，
以保持原来「构造失败就整体跳过、连迁移标记都不写」的语义。

#### ⑩-e 调用点与一个隐藏的坑

**46 处**去掉 `context` 实参（含 `viewModel.getApplication<android.app.Application>()`
这种带泛型的嵌套实参 —— 脚本按括号深度找顶层逗号，不靠正则瞎猜）。

⚠ **`WatchPayload` 的 `toJson()` 必须包一层 `JSONObject(...)`**：`toJson()` 现在返回 kotlinx 的
`JsonObject`，直接 `JSONArray.put(...)` 会被 org.json 当成**未知类型转成一个带引号的字符串**，
手表侧就解析不出结构了。改成 `JSONObject(entry.toJson().toString())` 后载荷逐字不变。

#### ⑩-f 安全网（未入库）

| 测试 | 用例数 | 覆盖 |
|---|---:|---|
| `HolidayManagerStorageTest` | 23 | 落盘串与存量格式逐字一致 / 真实存量串 load·save round-trip / 缺字段与类型不符的行被丢弃而非整批失败 / 年份键规则 / 版本号单调递增 / 备份通道 / 数据源两种响应解析 / 旧调休映射迁移**两个分支**（信任旧映射 vs 回退建议值） |
| `LockTest` | 3 | 可重入 / 多线程不丢更新 / 异常穿透且不吞锁 |

`:core:jvmTest` **179 → 206 全绿**。

> ⚠ **诚实标注一条强度差异**：`TeachingWeekReorganization` 的 Gson 基准是**用真实 Gson 2.11.0
> 跑出来的**；而 `toJson()` 的 org.json 基准**是按已知语义手写的字面量** ——
> 离线环境里没有可运行的 org.json（Android 的 org.json 在 `android.jar` 里是 stub，调用即抛）。
> 已用「含中文名的字面量」顺带验证了非 ASCII 不转义、字段顺序与紧凑格式；
> 但**拿到真机数据后建议再跑一次 `entries_{年}` 的逐字对比**。

### ✅ 已完成批次 · ⑨ 节假日四个纯逻辑文件下沉 `:core`（2026-10-09）

**结果：`CourseScheduleDateBounds` / `TeachingWeekReorganization` / `HolidayCourseExclusion` /
`HolidayCountdown` 全部进入 `core/src/commonMain`，`:core:compileKotlinLinuxX64` 通过
—— 也就是说这四个文件**已经是平台中立的**（不是「Android 能编」）。**

| 文件 | 行数 | 搬动时改了什么 |
|---|---:|---|
| `CourseScheduleDateBounds.kt` | 314 | `Math.floorDiv` → stdlib `Long.floorDiv`；`HolidayManager.Entry/TYPE_WORKSWAP` → `HolidayEntry.*` |
| `TeachingWeekReorganization.kt` | 380 | **Gson → JsonSupport**（`encode`/`decode`，见下）；移除 `Gson`/`TypeToken` |
| `HolidayCourseExclusion.kt` | 236 | `HolidayManager.entriesForDate` → `HolidayEntries.entriesForDate`；类型改名 |
| `HolidayCountdown.kt` | 255 | `Math.floorMod` → stdlib `Long.mod`；类型改名 |

#### ⑨-a 结构性前置：`HolidayManager.Entry` 提成顶层 `HolidayEntry`

这四个文件全都依赖 `HolidayManager.Entry`，而它的宿主 `HolidayManager`（存储层）
还差 `Context` / `org.json` / Gson / `@Synchronized` / `CourseRepository` 反向依赖，短期搬不动。
所以**把类型单独提出来**：新增 `core/.../data/HolidayEntry.kt`
（`data class HolidayEntry` + `TYPE_HOLIDAY`/`TYPE_WORKSWAP` + `matches`/`followLocalDate`/
`hasFollowMapping` + `object HolidayEntries { entriesForDate }`）。

`:androidApp` 侧零调用点改动，靠两招：

| 手法 | 效果 |
|---|---|
| `const val TYPE_HOLIDAY = HolidayEntry.TYPE_HOLIDAY` | 39 处 `HolidayManager.TYPE_*` 照旧可用 |
| `fun entriesForDate(...) = HolidayEntries.entriesForDate(...)` | 11 处 `HolidayManager.entriesForDate` 照旧可用 |
| `HolidayManager.Entry` → `HolidayEntry`（30 处，机械改名 + 补 import） | 类型名显式化，跨模块可见 |

> ⚠ **`HolidayEntry.toJson()` 刻意留在 `:androidApp`**（改成扩展函数）。
> 它产出的串以 `entries_{年}` 为键**直接落盘**，是数据兼容红线；
> 等 `HolidayManager` 整体下沉时再一并换 `JsonSupport`，并配合真实用户数据 round-trip 回归。
> 副作用：`wearable/WatchPayload.kt` 多了一行 `import com.haooz.chedule.data.toJson`（唯一外部调用点）。

#### ⑨-b Gson → JsonSupport：`encode()` 的输出是持久化数据

`TeachingWeekReorganization.encode/decode` 原来走 Gson。换库 = 换持久化格式，所以：

1. 新增 `JsonSupport.jsonToPlainValue(element)`，**复刻 Gson 的 `Map<String, Any>` 形状**：
   数字一律 `Double`、只有 `isString` 的 primitive 才是 `String`、对象保持插入顺序、`JsonNull` → null。
   因此 `fromBackupValue` **一行都不用改**。
2. `encode` 改成 `toJsonElement(toBackupValue(rules)).toString()`。
3. **基准不是猜的**：用**真实 Gson 2.11.0** 跑 `toJson(toBackupValue(...))` 打出字面量
   （`java -cp gson-2.11.0.jar Probe.java`），抄进 `TeachingWeekReorganizationJsonTest`
   做逐字比对 —— 即「新旧实现在同一输入上输出完全一致」的机械证明。

实测基准：
```
{"schema_version":1,"rules":[{"firstOriginalWeek":4,"firstStartWeekday":1,"firstEndWeekday":3,"secondOriginalWeek":5,"secondStartWeekday":4,"secondEndWeekday":7}]}
{"schema_version":1,"rules":[]}
```
并确认 Gson 解析回 `Map<String,Any>` 时数字是 **`Double`**（`4` → `4.0`），与 `jsonToPlainValue` 一致。

#### ⑨-c 新增安全网（未入库）

| 测试 | 覆盖 |
|---|---|
| `TeachingWeekReorganizationJsonTest`（5 用例） | encode 与 Gson 基准逐字一致 / decode 能读 Gson 时代的旧串 / 往返一致 / 6 类非法输入同样失败 / `jsonToPlainValue` 形状 |
| `DateExtEquivalenceTest` 增补 2 用例 | `Long.floorDiv` ≡ `Math.floorDiv`；`Long.mod` ≡ `Math.floorMod`（正除数），含 `Long.MAX/MIN` |

`:core:jvmTest` **172 → 179 全绿**。

#### ⑨-d 现在的 `:androidApp/data` 剩余（下一步就盯这张表）

| 文件 | JVM/Android 专有点 | 备注 |
|---|---:|---|
| ~~`HolidayManager.kt`~~ | 73 | **已下沉 `:core`**（⑩）—— 换掉 Context/org.json/Gson/`@Synchronized`/`CourseRepository` 反向依赖 |
| `CourseRepository.kt` | 39 | 2819 行，`String.format` 等 |
| `ScheduleAppearance.kt` / `ScheduleBackup.kt` | 30 / 25 | Bitmap、文件 IO |
| `SharedPreferencesStore.kt` / `FileAppFile.kt` | 13 / 2 | **Android 侧 actual，按决定必须留在 `:androidApp`** |
| `TimeConfigSnapshotParser.kt` | 2 | Gson |
| `WallpaperTransform.kt` | 0 | 已中立，可直接搬 |

> **`HolidayManager` 已搬完（⑩）**，它曾是最大的一块（73 点）。现在 `:androidApp/data` 里最大的
> 是 `CourseRepository`（37 点 / 2819 行）—— 也是**数据层的最后一块**。
> 它的难点与 `HolidayManager` 不同：不是平台 API，而是**全局单例撞 Kotlin/Native 线程模型（R4）**，
> 要改成显式注入；另外还有 `String.format` 这类 JVM 专有点。

### ✅ 已完成批次 · ⑧ 阶段 2.1：`java.time` → kotlinx-datetime 全量换血（2026-10-09）

**`:androidApp` 的 22 个 `java.time` 文件全部换成 kotlinx-datetime，`:androidApp:assembleDebug` 通过。**
这一步是「节假日集群 + `CourseScheduleDateBounds` 进 `:core`」的唯一前置。

#### ⑧-a 为什么必须整体换，不能逐个文件换

`java.time.LocalDate` 与 kotlinx-datetime 的 `LocalDate` 是**两个不兼容的类型**。
只要任何一个对外 API 还带旧类型，搬到 `:core` 就会强迫所有 `:androidApp` 调用点做转换
（46 处 `TeachingWeekReorganization` + 14 处 `HolidayCourseExclusion`，散在 12 个文件）。
所以只能一次全换 —— 好处是**换不干净编译器立刻报错**，不会留半截。

#### ⑧-b 新增 `core/.../data/DateExt.kt`：kotlinx-datetime 补不齐的那部分

阶段 0 验证 2 已证明两者在 `parse` / `plus` / `daysUntil` / `dayOfWeek` 上语义等价，
但**有 8 类 API kotlinx-datetime 根本没有**，必须自己写（全部实测确认过，别凭印象改）：

| 缺什么 | 实测结论 | 补法 |
|---|---|---|
| `LocalDate.now()` / `LocalTime.now()` | 必须显式给时区 | `todayLocalDate()` / `nowLocalTime()` / `nowLocalDateTime()` |
| `LocalDate.MIN` / `MAX` | **有，但是 `internal`**（0.6.2 实测） | `LOCAL_DATE_MIN` / `LOCAL_DATE_MAX` = kotlinx 年份区间端点（±999_999），另给 `LOCAL_DATE_MIN/MAX_EPOCH_DAY` |
| `plusDays` / `minusDays` / `plusWeeks` / `minusWeeks` | **`plusDays` 是 `internal`，其余不存在**；只有 `plus(value, DateTimeUnit)`，且它是**顶层扩展**，必须 `import kotlinx.datetime.plus` | 包一层同名扩展 |
| `lengthOfMonth()` | 不存在 | 用「下月 1 号 − 本月 1 号」算 |
| `LocalDate.ofEpochDay(Long)` | kotlinx 是 `fromEpochDays(**Int**)`。⚠ **`.toInt()` 越界会静默回绕**，不抛异常 | `localDateFromEpochDays(Long): LocalDate?` 先判 Int 范围 |
| `java.lang.Math.addExact/subtractExact` | `java.lang.Math` 在 Native 上**不存在** | `addExactOrNull` / `subtractExactOrNull`（溢出 → null，与原来「抛异常被 runCatching 吃掉」等价） |
| `DateTimeFormatter` 固定 pattern | kotlinx 的 `Format { }` DSL 默认 padding 与 java pattern 不一致 | 手写 `formatHourMinute` / `formatMonthDay` / `formatMonthDayShort` / `formatChineseDate` / `formatSlashDate` / `parseHourMinute` |
| `Duration.between(LocalTime, LocalTime)` | kotlinx 没有 LocalTime 的 Duration 差 | `millisBetween`；LocalDateTime 的差在 `HolidayCountdown` 里手算秒差 |

> ⚠ **`Long.floorDiv(Long)` 在 Kotlin/Native 上是有的（stdlib）**，但 `Math.floorDiv` 没有。
> 所以 `CourseScheduleDateBounds.ceilDiv` 的 `-Math.floorDiv(-value, divisor)` 要改成 `-(-value).floorDiv(divisor)`。

#### ⑧-c 安全网：12 个差分等价用例（未入库）

新增 `core/src/jvmTest/.../DateExtEquivalenceTest.kt`，把上面每个手写替换**逐条与 `java.time` 对拍**：

| 用例 | 覆盖 |
|---|---|
| parse/toString/epochDay | 2026 全年逐日 + 1900/2000/2024/2100 各月首末 |
| dayOfWeek iso 编号 / lengthOfMonth | 同上全量对拍 + 闰年 2 月单独钉死 |
| plusDays/minusDays/plusWeeks/minusWeeks | ×9 个偏移量全量对拍 |
| daysUntil / weeksBetween | 与 `ChronoUnit` 对拍（含负数、跨年） |
| 4 个日期格式化 | 与 `DateTimeFormatter` **逐字**比对 |
| HH:mm 解析与格式化 | 24×7 组合 + 6 种非法输入 |
| millisBetween | 与 `Duration.between().toMillis()` 对拍 |
| localDateFromEpochDays | 与 `ofEpochDay` 对拍 + **Int 越界必须返回 null** |
| MIN/MAX 哨兵 | 钉死 epoch-day 常量（−365 961 662 / 364 522 971）防改库后静默偏移 |
| addExact/subtractExact | 10×10 组合（含 Long.MAX/MIN）与 `java.lang.Math` 对拍 |

`:core:jvmTest` 从 160 → **172 个用例，全绿**。

#### ⑧-d 机械替换的验证手段（可复用）

批量改写日期代码**必须机械核对**，因为编译器只能抓类型错、抓不到 `!A.isBefore(B)` 这类
语义反转。做法：从 `git show HEAD:<文件>` 取出**每一处** `isBefore` / `isAfter` /
`dayOfWeek.value` / `toEpochDay()` / `LocalDate.of(` 等调用点，按固定规则算出期望文本，
再检查新文件里**存在**这条期望文本（空白与括号归一化后比对）。
51 处 `isBefore/isAfter` + 113 处其余替换，逐条对完只剩 6 处「人工处理」的（都已单独复核）。

> ⚠ **踩到的坑**：`!A.isBefore(B)` 用正则改成 `!A < B` 后，Kotlin 会解析成 `(!A) < B`
> —— 必须再补一条 `!X < Y` → `X >= Y` 的规则。另有一条正则的 `\)?` 吃掉了 `if (…)` 的右括号，
> 造成 3 处语法错（编译器抓到了）。

#### ⑧-e 剩下的挡路石（下一步直接看这张表）

| 文件 | 剩余 JVM/Android 专有点 | 说明 |
|---|---:|---|
| ~~`HolidayCourseExclusion.kt`~~ | 0 | **已下沉**（⑨） |
| ~~`HolidayCountdown.kt`~~ | 1 | **已下沉**（⑨） |
| ~~`CourseScheduleDateBounds.kt`~~ | 1 | **已下沉**（⑨） |
| ~~`TeachingWeekReorganization.kt`~~ | 3 | **已下沉**（⑨） |
| ~~`HolidayManager.kt`~~ | 73 | **已下沉**（⑩） |
| `CourseRepository.kt` | 37 | **数据层最后一块**：全局单例撞 Native 线程模型（R4）、`String.format` |
| `ScheduleAppearance.kt` / `ScheduleBackup.kt` | 30 / 26 | Bitmap、文件 IO |

**结论（当时）：把 `CourseScheduleDateBounds` / `TeachingWeekReorganization` /
`HolidayCourseExclusion` / `HolidayCountdown` 搬进 `:core` 只差 5 处小改动 + 把
`HolidayManager.Entry` 从嵌套类提出来。** —— 后续 ⑨ 与 ⑩ 就是照这条做的，已全部完成。

### ✅ 已完成批次 · ⑦ 拆 `Holidays.kt` 类型集群 + 6 个中立类型下沉（2026-10-09）

**做法：先机械拆、再挑零成本的部分搬。** 两件事分开做，各有一条独立的验证手段。

**⑦-a 拆分（逐字搬运，零语义变更）**

`androidApp/.../data/Holidays.kt`（1782 行单文件全包）按类型集群拆成 4 个文件：

| 文件 | 行数 | 内容 | 挡路的东西 |
|---|---:|---|---|
| `HolidayManager.kt`（`git mv` 自 `Holidays.kt`） | 901 | 存储 + 备份 + 数据源 | `Context` / `SharedPreferences` / `org.json` / Gson / `@Synchronized` |
| `TeachingWeekReorganization.kt` | 380 | 调休改周规则（纯日期映射 + 严格编解码） | `java.time.LocalDate` / `ChronoUnit` / Gson |
| `HolidayCourseExclusion.kt` | 236 | 假期课程剔除与逐日裁决 | `java.time.LocalDate` / `LocalTime` |
| `HolidayCountdown.kt` | 255 | 假期倒计时（今日页） | `LocalDate/LocalTime/LocalDateTime/Duration` |

> **验证手段**（拆分这种"理应无变化"的改动必须机械验证，不能靠"编过了"）：
> 用 `sed -n` 从 HEAD 原文按行区间抽出四段，与新文件**去掉文件头后逐行 `diff`** ——
> 四段全部零差异。`:androidApp:assembleDebug` 通过。
> 保留 rename 历史：`git mv Holidays.kt HolidayManager.kt`。

**⑦-b 下沉 6 个平台中立类型 → `core/.../data/HolidayTypes.kt`**

`TeachingWeekReorganizationRule` / `TeachingWeekPosition` / `HolidayEndCourseExclusion` /
`HolidayBeforeCourseExclusion` / `HolidayDayCourseResolution` / `HolidayCourseDisplaySelection`

它们的共同点：**只由 Int / Long / Boolean / String / `Course` 构成**，不碰
`android.*`、`java.time`、`org.json`、Gson —— 所以**不需要等任何转换**就能进 commonMain。
包名保持 `com.haooz.chedule.data` 不变，**`:androidApp` 侧 import 一行没改**（同包直接可见）。

> ⚠ 刻意**没有**放进 `com.haooz.chedule.data.holiday` 之类子包：一旦分包，
> `:androidApp` 里同包的调用点就要逐个加 import，那是纯噪音 diff，会掩盖真正有意义的改动。
> 等整个集群搬完再考虑分包。

**⑦-c 剩余部分为什么不能一起搬（下次接手直接看这张表，别重新分析）**

| 剩余部分 | 挡路的东西 | 处理难度 |
|---|---|---|
| `HolidayManager` | `Context`（→ 已有 `AppStorage`）、`org.json`（→ 已有 `JsonSupport`）、Gson、`@Synchronized`、**`CourseRepository` 依赖**（`migrateLegacyFollowDates` 里构造它）、`System.currentTimeMillis()` | 中。`@Synchronized` 在 Kotlin/Native **不存在**，需另找方案（见下） |
| `TeachingWeekReorganization` object | `java.time` + Gson 的 `Map<String, Any>` 编解码 | 中。`encode()` 的输出是**持久化数据**，换 JSON 库必须逐字对齐 |
| `HolidayCourseExclusion` object | `LocalDate` / `LocalTime` | 低，但调用点传 `LocalDate` |
| `HolidayCountdown` | 4 个 `java.time` 类型 + `LocalDate.MIN/MAX`（kotlinx-datetime **没有** MIN/MAX 常量）+ `Math.addExact`（`java.lang.Math` 在 Native 上不存在） | 中高 |

> ⚠ **`LocalDate.MIN` / `LocalDate.MAX` / `Math.addExact`** 是这次盘点新发现的两个坑，
> 之前文档没记。`HolidayCountdown` 与 `CourseScheduleDateBounds` 都用 `LocalDate.MIN/MAX`
> 做边界哨兵，`TeachingWeekReorganization` 用 `Math.addExact` 做溢出保护（外面套 `runCatching`）。
> kotlinx-datetime 需要自建常量与溢出安全的加减helper。

### 🔍 独立核对结论（提交 `7c88e5a` 前做过，别再重复验证）

对最高风险的逐字移植部分做了**独立交叉核对**（另一个 agent 用 `git diff --no-index`
机械对照新旧实现 + 边界推演，不依赖注释）。结论：

- **无 BUG**。以下经机械 diff 确认逐字等价：防盗链判定 / 签名链接提取 / 版本比较 /
  `remoteBase` / 偏好文件名与键 / `updateAll` 三分支与日志 / 8MB 上限的四种组合 / 落盘路径。
- 提出 3 处并**已全部处理**：
  1. `IOException → Exception` 会吞掉 `CancellationException` → 两处 catch 显式透传取消。
     同时把「不完全等价」的两处可观测后果写进 `ScriptRepository` 的 KDoc
     （不再声称「行为等价」）：`ensureScript` 非 `IOException` 不再逃逸（三个调用点都没有
     try/catch，以前会崩）；`updateAll` 返回 -1 后调用方会写 `last_update_time`（抑制 7 天自动更新）。
  2. Android 与 JVM 在「声明长度超限」上不一致（前者空字节、后者部分字节）→ JVM 侧改为同样预检即拒。
  3. 清理两处因参数移除而变成死变量的 `LocalContext`。
- 已知**接受**的差异（返回值不变，仅记录）：非 2xx 时新实现会把响应体读进内存后才判状态码
  （旧实现读过之前就返回）；`readAtMost` 的 `maxBytes <= 0` 分支对生产代码是死代码。

> 关于「测试没覆盖真实实现」这条意见：已补
> `HttpServiceRealImplTest`（起本地 socket 服务器直打 JVM 真实实现），
> 覆盖声明长度已知 / chunked 未知长度 / 正好等于上限 / 错误响应四种情形。

### ⚠️ 仍需真机确认

1. **描边亮度** —— `setColorUniform` 改用 `copy(alpha = 1f)`（alpha 已由
   `GraphicsLayer.alpha` 单独控制，原来相乘会偏暗）。但默认色是 `White.copy(alpha = 0.5f)`，
   **需确认描边是否偏亮**。
2. **对话框的模糊 / 描边 / 跟手返回** —— 两个 bug 修完后需回归确认。
3. **长按浮层与拖放落点** —— 已修，需按「跨特殊块 / 跨午休晚修 / 调课日列」三种情形回归。

### 📌 剩余工作量（实测，2026-10-10 重数）

| 工作量 | 数量 |
|---|---|
| `android.*` import | **349 处 / 93 文件** |
| `androidx.*`（**不含** compose）| **99 处**（与上一行去重后共 **448 处 / 94 文件**）|
| `androidx.compose.*` | 2665 处 —— **跨平台，不算阻塞**，别拿它估工作量 |
| ~~`java.time`（阶段 2 日期迁移）~~ | ✅ **已完成**（2026-10-09）：22 文件全换，`java.time` 在 `:androidApp` 只剩注释（见 ⑧） |
| Gson 引用（**风险 R2**） | **22 处 / 10 文件**（⑫ 之后又降了；`TeachingWeekReorganization` 那份已换掉，见 ⑨-b）|
| `java.io` 引用 | 18 处（import）/ 11 文件 |

> ⚠ 早期写的「505 处 / 146 文件」把部分 androidx 混进来了，口径与该表不一致，**以本表为准**。
> 逐文件处置清单见 `docs/NAVIGATION_MIGRATION_HANDOFF.md` **第七节（Android 数据层迁移清单）**。

按阻塞类型分组：

- ~~**纯日期**：`CourseScheduleDateBounds`~~ ✅ **已下沉 `:core`**（⑨）
- ~~**节假日整条链**~~ ✅ **已下沉 `:core`**（⑨⑩，含存储层 `HolidayManager`）
- ~~**`CourseRepository`**~~ ✅ **已下沉 `:core`**（⑫，无参单例）；
  Kotlin/Native 线程模型（风险 **R4**）仍未最终验证 —— **要等真机**
- **Gson**：`ScheduleAppearance` 等 10 个文件（清单见交接文档 7.3）——
  需 `@Serializable` + **显式字段清单**，属**最高风险 R2**，必须有真实用户备份做 round-trip 回归
- **文件 IO**：`ScheduleBackup` 已下沉 ✅；其余 `java.io` 引用多为导入导出
- **Android 专有模块**（按约定留在 `:androidApp`）：`reminder/` / `widget/` / `shizuku/` / `wearable/` / `ui/web/`
  —— 但 `reminder/CourseReminderHelper` 的**提醒时刻算法**值得抠进 `:core`（iOS 本地通知要用）

**不要用正则批量改写日期代码**（曾破坏 lambda / when 分支 / `!` 优先级）。
`Holidays.kt` 的调休 `followDate` 推算是全项目最敏感的部分，改它要逐点对照。

---

**① `:core` KMP 模块已建立**（阶段 1 的第一批）
- `Course` / `ScheduleFolder` / `TimeConfig` / `CourseTimeResolver` / `PeriodTimeSource` 已下沉
- 包名保持 `com.haooz.chedule.data` 不变，所以 `:androidApp` 侧 import 一行没改
- `:core` 的 `LocalDate` 用 kotlinx-datetime 0.6.2；`api(...)` 而非 `implementation`（否则下游看不到类型）
- 踩过的坑写在 `.workbuddy/memory/`，重点：`api` vs `implementation`、AGP 9 的 KMP 插件写法

**② `backdrop` 已升级为 KMP 模块**（原本是放在 `:androidApp` 里的 Android-only fork）
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

1. **同名文件**：`:androidApp` 里两个 `DialogContentLayout`，搬错了那个 →
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
| OkHttp                | ✅                   | ❌ **没有 Native 版本** —— 见下方说明 |
| 文件 IO（`java.io.File`） | ✅                   | ❌ 不存在（`AppFile` 抽象已就位，实现待补） |
| 精确闹钟（AlarmManager）    | ✅ `setAlarmClock`   | ❌ **完全不存在**              |
| 后台执行代码                | ✅ BroadcastReceiver | ❌ **不存在**                |
| 控制系统免打扰               | ✅                   | ❌ **系统禁止**               |
| 桌面小部件                 | ✅ AppWidget         | ⚠️ WidgetKit，须 Swift 重写  |
| 系统壁纸                  | 未使用（仅 App 内背景）      | ✅ 不受影响                   |

> **关于网络层的现状（最容易误解的一条，务必看清）**
>
> OkHttp 在 iOS 上**不可用**（没有 Kotlin/Native 版本）。`:core` 已把网络收成自建的
> `HttpService` 接口：Android 侧继续用 OkHttp（行为零变化），
> **Native 侧目前是一个「调用即抛 `NotImplementedError`」的占位**。
> 所以「iOS 已可用」是不成立的 —— 这是接 iOS 前必须补的第一件事，见「交接清单 A1」。
>
> 本文档早期写的「Ktor 包一层，OkHttp 不删」**已作废**。原因不是技术不可行，
> 而是**代价**：任何现代 Ktor 都会把全项目的 `kotlinx-coroutines` 从 1.9.0
> 顶到 1.10.2 / 1.11.0，而提醒/闹钟/同步全压在协程上。
> 把「升级核心异步库」捆进迁移会让故障无法归因、也无法单独回退。
> 如果 iOS 侧确实要引入 Ktor，请**单独评估、单独提交**。

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
| 平板双栏布局                  | `LocalConfiguration` 判定          | `expect fun isTabletWidth()`（`:miuix` skikoMain 已有）            | ✅ 已改造      |

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

:androidApp                         Android 壳（保留 reminder / widget / shizuku / wearable）
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

**目标：** 建 `:core`，搬最干净的 5 个文件，`:androidApp` 行为零变化。

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

- `./gradlew :androidApp:assembleDebug` 通过
- 用同一份真实备份文件，迁移前后对比课程时间、周次、节假日判定，**逐条一致**
- Kotlin 版本保持 2.4.10 不动

**风险：极低。** 不触碰任何持久化格式。

---


### 阶段 2 · 时间与序列化换血（3~4 周）

**目标：** 让 `:core` 具备上 Kotlin/Native 的资格。全计划中技术决策最密集的一步。

**2.1 `java.time` → `kotlinx-datetime`（22 个文件）✅ 已完成（2026-10-09，见「当前进展 ⑧」）**

实测结论修正：**`SimpleDateFormat` 不在这一步里** —— 它全部用于「文件名时间戳 / 日志时间戳」，
操作的是 `java.util.Date`，与 `LocalDate` 无关，且所在文件（`ScheduleBackup` / `CrashLogHelper` /
`LocalBackupScreen` / `WebDavSettingsScreen` / `UpdateDialog` / `UpdateSettingsScreen`）
本就留在 `:androidApp`，不属于阶段 2.1。真正要手写替换的是 `DateTimeFormatter` 的 5 个固定 pattern。

**2.2 Gson 分层替换 —— 不要全局无脑替换**

> 📌 **前置调研已完成**（见「当前进展 · 🔬 R2 前置调研」）：真实备份已到手，
> `JsonSupport` 的 pretty 输出与 Gson **逐字一致**，且量出了 R2 必须的 kotlinx 配置
> （`encodeDefaults = true` + `explicitNulls = false`）。**动手前先读那一节。**

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

**3.1 平台能力抽象 —— 最小集合，够用就停**

原始设想（~~`expect class KeyValueStore` / `expect fun appFilesDir()`~~）已被实际实现取代：
存储与文件改用 **接口 + 启动注入**，而不是 expect/actual。
理由：接口能让 `:core` 的 commonMain 保持**零平台实现**（`linuxX64` 门禁才编得过），
Android 侧继续用原生 API（行为零变化），iOS 侧注入自己的实现即可。
**新增平台能力时请沿用这个模式**，不要为了「正统 KMP」改成 expect/actual。

| 能力 | 实际形态 | 状态 |
|---|---|---|
| 日志 | `expect fun platformLog(...)` | ✅ 三平台 actual 齐（Android / JVM / Native） |
| 存储 | `interface KeyValueStore` + `AppStorage.init { }` | ✅ 抽象完成；**iOS 侧待实现**（见交接清单 A2） |
| 文件 | `interface AppFile` + `AppFiles.init(...)` | ✅ 抽象完成；**iOS 侧待实现**（见 A3） |
| 网络 | `expect fun createHttpService(...)` | ⚠ Android/JVM 完成；**Native 是抛异常的占位**（见 A1） |
| 设备信息 | `expect fun currentDeviceInfo()` | ✅ 三平台 actual 齐；iOS 建议改用 `UIDevice` |
| 调度器 | `expect val ioDispatcher` | ✅ Native 用 `Dispatchers.Default`（`Dispatchers.IO` 在 Native 上是 `internal`） |
| 互斥 | `expect fun synchronizedOn(lock, block)` | ⚠ Android/JVM 是真锁（与 `@Synchronized` 等价）；**Native 暂时直通** —— iOS 接进来前要补（见 ⑩-c） |
| `Notifier` / `ScreenInfo` / `Haptics` / `showToast` | 尚未抽取 | ⬜ 等有文件真正要下沉时再定，**别提前造** |

Android actual **必须继续走 SharedPreferences**，否则老用户数据全丢。

**3.2 下沉 `data/` 剩余部分**

| 文件                                                   |    行数 | 难点                                                |
| ---------------------------------------------------- | ----: | ------------------------------------------------- |
| `CourseRepository.kt`                                | 2,810 | 全局单例 → KN 线程模型，需改显式注入                             |
| ~~`Holidays.kt`~~                                    | 1,781 | **已拆成 4 个文件**（⑦）；其中 3 个（`TeachingWeekReorganization` / `HolidayCourseExclusion` / `HolidayCountdown`）**已下沉 `:core`**（⑨）。只剩 `HolidayManager.kt`（73 点）在 `:androidApp` |
| ~~`CourseScheduleDateBounds.kt`~~                    |   314 | **已下沉 `:core`**（⑨）—— 两个前置（类型集群 ⑦、日期换血 ⑧）做完后它自己只剩 1 处 `Math.floorDiv` |
| `ScheduleAppearance.kt`                              |   610 | 9 处 android import，含 Bitmap 处理                    |
| `ScheduleBackup.kt`                                  |   450 | 文件 IO                                             |
| `SchoolIndex` / `ScriptRepository` / `StatsReporter` |   522 | OkHttp + 文件                                       |

**3.3 留在 `:androidApp` 不动**  
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
| `WebViewRequestInterceptor` | 桌面模式 POST 转发 | POST，纯 Android，留在 `:androidApp`                      |

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

`:androidApp` 的协程 1.9.0 来自 Compose 传递依赖，而提醒/闹钟/同步/小组件全压在协程上。
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

- 扫描 `core` / `backdrop` / `miuix` 的 `commonMain` + `skikoMain`（173 个文件）
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
| R2 | Gson 替换破坏 4 条序列化通道                         | 用户数据丢失         | 分层替换 + round-trip 回归 + 旧格式读取路径。**已用真实备份做前置调研**：pretty 输出逐字一致、配置要求已量出（见「🔬 R2 前置调研」） |
| R3 | iOS 本地通知 64 条上限                            | 提醒漏发           | 滚动预约策略，阶段 0 原型验证                        |
| R4 | Kotlin/Native 线程模型撞全局单例                    | 崩溃 / 状态错乱      | 阶段 3 改显式注入，阶段 4 专项验证                    |
| R5 | `SimpleDateFormat` → kotlinx-datetime 行为偏移 | 日期静默算错         | 逐点对照用例                                  |
| ~~R6~~ | ~~Miuix 是 fork 的 `-android` 变体~~ | — | **已排除**（2026-10-10，`ca95feb`）：Windows 上纯依赖解析验证，官方 miuix 5 个 artifact（squircle / icons / blur / preference / navigation3-ui）**都有 `-iosarm64` 变体**，且能连到 `:backdrop` 的 iosArm64 klib。命令与输出见 C 节第 3 条 |
| R6b | 本项目 fork 的 `miuix-ui` 在 iOS 侧缺 actual | iOS 编不过 | `skikoMain` 已接给 `iosMain`（`-Pnexio.ios=true` 时生效），且该接线**已在 wasmJs 上闭环验证过**（曾报 10 处 `no actual declaration`，已修）。仍需 macOS 首次编译确认 |
| R6c | wasmJs 门禁与 `check` 互斥 | 门禁漏跑 | 已知限制：`-Pnexio.wasm=true` 期间 Distributions 仓库冲突使 `check` 失败。**跑门禁必须分两次**（先默认态 check，再 `-Pnexio.wasm=true checkKmpWasmJs`）。见 D 节 |
| R6d | iOS 首启即崩：`Info.plist` 缺 `CADisableMinimumFrameDurationOnPhone` | 白屏 / crash | CMP 1.7.3 起强制检查。已写进 C 节第 5 条，建 Xcode 工程时一并补 |
| R7 | 云端分享白名单未同步                                 | 新字段静默丢弃        | 改客户端字段必须同改 `server/index.js` 并重新部署      |
| R8 | 61.7k 行 UI 迁移周期过长，拖垮主线开发                   | 项目停滞           | 严格按批次，每批次结束 `:androidApp` 仍可发版                 |
| R9 | ~~OkHttp 在 iOS 不可用~~                       | 低              | **改法已修订**：自建 `HttpService`，Android/JVM 复用 OkHttp；Native 侧未实现（见「网络层专项」） |

---

## 六、红线原则

1. **不打断 1.6.x 开发。** 每阶段结束时 `:androidApp` 必须能正常编译、打包、发布。任何阶段中途停下都能照常发版。
2. **数据兼容不可谈判。** 存量 SharedPreferences 数据在每步之后都必须正确读出。
3. **每步可回退。** `:core` 出问题就退回单模块。
4. **先验证再推广。** 阶段 0 三个验证不做完，不进入阶段 1 的全面投入。
5. **不要让 fork 变枷锁。** Miuix fork 解决了定制问题，但会阻断跨平台。

---

## 七、立即可做的第一步

这份文档的读者有两类，**该做的第一步不同**：

### 如果你是 Android 侧开发者（继续迁移）

1. **先跑门禁**（见「交接清单 E」），确认当前基线是绿的
2. 按「剩余工作量」那张表挑一个**阻塞簇**做，别挑单个文件：
   - 想降低最大风险 → **Gson 迁移**（R2），但**必须先有真实用户备份做 round-trip 回归**
   - ~~`java.time` → kotlinx-datetime~~ ✅ **已完成**（2026-10-10，见 ⑧），不再是候选
   - 想推进 UI → `:ui-shared` 模块 + 资源迁移（F 节）
3. 每批结束都要：`:androidApp:assembleDebug` 通过 + 门禁绿 + 真机过一遍受影响的界面

### 如果你是 iOS 侧开发者

1. **先读「交接清单 A」** —— A0 三项工程配置 + A1~A3 平台实现 + 3 个启动注入是硬门槛。
   其中 **A1（网络层）与 A2（UserDefaults）已实现且编译通过**（2026-10-10），
   A3（`AppFile` 的 `NSFileManager` 实现）仍待写。
2. iOS target **已声明**（`-Pnexio.ios=true` 开启），`:miuix` / `:backdrop` 的
   `skikoMain` 接线（`iosMain`）**已写好**；注意 **`binaries.framework` 还没声明（A0-1）**。
3. ⚠ 原先这里写「第一次编译大概率会撞 skikoMain 问题」—— **已过时**：
   该坑已于 2026-10-10 在 wasmJs 目标上提前撞完并修掉（3 个问题见 D 节专节）。
   且**编译可在 Windows 上做**（A1-bis），不必等 macOS —— 等 macOS 的是链接与运行。
4. 网络层已实现（A1）；引入 Ktor 仍是**不建议**（顶协程版本），若引入请单独评估提交。

> **红线（对两边都适用）**：不打断 1.6.x 正常发版。每阶段结束时 `:androidApp` 必须能正常编译、打包、发布。
