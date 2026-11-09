# Nexio 课程表 · 交接文档

> 现状快照、门禁命令、  
> 架构约定、里程碑与踩过的坑

> ## 📏 维护纪律（改这份文档前先读）
>
> | ✅ 能加                       | ❌ 不能加        |
> | -------------------------- | ------------ |
> | 数字刷新（规模表 / 门禁现状）           | 「当时怎么想的」过程叙述 |
> | 新发现的**可复用**坑（一句话 + commit） | 已完成批次的逐条拆解   |
>
> - **已完成的工作只占「里程碑表」一行**：`commit` + 一句话。要看细节 `git show <commit>`。
> - **踩坑细节**写 `.workbuddy/memory/YYYY-MM-DD.md`，不写这里。
> - 里程碑表**只留最近 ~15 条**，更老的删掉（信息在 commit 里）。
> - ⚠ 多人协作仓库，提交前先 `git status` 对齐，别把别人的半成品扫进去。

---

### 本机环境（macOS）

⚠ 没有全局 JDK，直接 `./gradlew` 会报 *Unable to locate a Java Runtime*  
（`/usr/bin/java` 只是 macOS 的安装 stub），需带：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew …
```

### 已定型、别推翻的决定

| 决定                                                      | 原因                                                                                                  |
| ------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 存储 / 文件用**接口 + 启动注入**，不用 expect/actual                  | 让 `:core` 的 commonMain 保持零平台实现，`linuxX64` 门禁才编得过                                                    |
| **不引入 Ktor**                                            | 任何现代 Ktor 都会把 `kotlinx-coroutines` 从 1.9.0 顶到 1.10.2+/1.11.0，而提醒·闹钟·同步·小组件全压在协程上。真要引入请**单独评估、单独提交** |
| 偏好文件名**不做集中常量表**                                        | 手抄错名字 = 老用户数据静默读不出来（曾把 `holiday_settings` 误写成 `holiday_prefs`）。项目共 **17 个**偏好文件名                    |
| Android actual 必须继续走 SharedPreferences / `java.io.File` | 换实现（如 DataStore）= 存量用户数据全读不出来。**评估过，不迁**                                                            |
| 不引入 `withHostTest {}`                                   | 实测会让 8 个测试因 `Build.MODEL` 为 null 而失败                                                                |
| `wearable/` 不动                                          | 不由用户维护                                                                                              |
| 版本号由用户自己管                                               | 审查时不提醒                                                                                              |

### 测试

`core/src/commonTest/`（11）+ `core/src/jvmTest/`（16）共 **291 个用例已入库**，0 失败。  
跑法 `./gradlew :core:jvmTest --rerun-tasks`。  
夹具 `jvmTest/resources/school_index.pb` 是**真实 248 校生产索引**（若仓库要公开需评估脱敏）。

---

## 2. 门禁（每次改完必跑）

```bash
# 默认态（不加任何 property）—— 红线
./gradlew :core:check :miuix:check --offline
./gradlew :androidApp:assembleDebug                  # Android 零回归，不许破

# 拆开跑（等价）
./gradlew :core:compileKotlinLinuxX64 :core:compileTestKotlinLinuxX64  # commonMain/Test 平台中立
./gradlew checkKmpPurity                             # 已知 JVM 专有 API 静态扫描
./gradlew :core:jvmTest --rerun-tasks                # 291 个用例

# 动了 commonMain / skikoMain 时额外跑（需联网 + PATH 有 Node）
./gradlew -Pnexio.wasm=true checkKmpWasmJs

# ── iOS（需 macOS + Xcode；首次会联网拉 iOS klib，**不能加 --offline**）──────
# ⚠ 与默认态 check **分开跑**：声明 iOS target 后 check 会去解析 iOS klib 变体，
#   离线门禁会失败（这也是 iOS target 默认关闭的原因）。
./gradlew -Pnexio.ios=true \
  :core:compileKotlinIosArm64 :miuix:compileKotlinIosArm64 \
  :backdrop:compileKotlinIosArm64 :ui-shared:compileKotlinIosArm64
# 模拟器目标（日常开发真正跑的）：把上面的 IosArm64 换成 IosSimulatorArm64
# 在 iOS 模拟器上真跑 :core 的跨平台测试（编译 + 链接 + 运行）
./gradlew -Pnexio.ios=true :core:iosSimulatorArm64Test
# 出 framework（compile 绿 ≠ 能 link，这一条必须单独跑）
./gradlew -Pnexio.ios=true :ui-shared:linkDebugFrameworkIosSimulatorArm64
```

---

## 3. 模块现状

### 模块现状

| 模块            |           文件 |        行数 | 源集                                                                                      | 状态                                                    |
| ------------- | -----------: | --------: | --------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| `:core`       | 116 (+28 测试) |     18.7k | common **56** / android 8 / ios 2 / jvm 8 / jvmShared 2 / native 6 / **linuxX64 1（门禁）** | **数据层 100%**；14 套跨平台能力 + R2 导入编解码 + ICS 解析            |
| `:backdrop`   |           64 |      6.3k | common 54 / android 5 / skiko 5                                                         | KMP 化；skikoMain 已过 **wasmJs + iOS 编译**                |
| `:miuix`      |      **129** |     31.9k | common **98** / android 10 / skiko 9                                                    | KMP 化；**完整 fork、自包含**（不再依赖官方 miuix-ui）；官方子库 **0.9.4** |
| `:ui-shared`  |      **129** | **57.4k** | common **108** / android 3 / jvm 3 / ios 5                                              | 路由 20/20 + **主界面 `CourseScheduleApp`** 全迁完（阶段 5 ✅）    |
| `:desktopApp` |            1 |      ~306 | jvm                                                                                     | 桌面客户端，能真跑并打包                                          |
| `:androidApp` |       **49** | **15.5k** | android                                                                                 | **无待迁**；全是留守（宿主装配 + 永不迁能力）；单 Activity（Manifest 23→2）  |

**`:core` 的 14 套跨平台能力**：`NexioLog` / `KeyValueStore`+`AppStorage` / `HttpService` /  
`JsonSupport` / `PlatformInfo` / `AppFile`+`AppFiles` / `ioDispatcher`（不用 `Dispatchers.IO`，  
它在 Native 上是 `internal`）/ `synchronizedOn`（Native 暂直通）/ `PlatformToast` /  
`FeatureLog`（⚠ 别加 `inline`，跨模块 JVM target 21 vs 17 不一致会炸）/ `compareByChineseCollator` /  
`DayScheduleResolver`（提醒时刻算法，第十五批）/ `ScheduleEffects`（课程变更的平台副作用出口，第十六批）/  
**`ScheduleFileController`**（SAF 选文件读 + 另存为，第二十三批）。

## 4. 架构约定（迁移已完成，新代码沿用）

**页面 UI 一律放 `:ui-shared` commonMain**；只有 Android 专有「能力」留 androidApp，  
通过注入（回调/槽位/接口）解耦。没有「页面留守」。真正只留 androidApp 的三类：

- **纯能力实现体**：`CrashLogHelper` + Android 包装（DiagnosticRecorder / UpdateController /  
  ReminderController / BackupFileController / ScheduleEffects / PlatformToast 等）、  
  `ApiCompat` / `ActivitySystemBars` / `MiuixVisualEffectsPolicy` / `UpdateChecker` /  
  `UpdateInstaller`、`ScheduleAppearance` 本体（Bitmap/LruCache）、`ClassEndEffects` 震动/烟花实现
- **宿主层**：`MainActivity` / `AppRouteContent` / `NexioApplication`
- **WebView 内容宿主**：webview 本体（iOS 要 WKWebView；页面外壳仍迁 + 槽位注入）

⚠ **两条文件 I/O 分工线别混**：

- 「**应用自己的备份目录**」→ `BackupFileController`（固定目录、列表扫描、读/写/删）
- 「**用户选定的任意位置**」→ `ScheduleFileController`（导入时选一个文件读、导出时另存为新文件）  
  两者都是文件 I/O，但生命周期与失败文案完全不同，**不要合并**。

---

## 9. 踩过的坑（机制值得记住）

**Gson → JsonSupport 换序列化**（R2）：4 条通道（单课表备份 / 分享码 / 教务导入 / 全量备份）  
必须逐条 round-trip 回归，旧格式读取路径不能丢。

**fork 边界**：官方 miuix 0.9.4 与 fork commonMain 重叠 85 文件（30 个有改动、约 4414 行），  
fork 另有 21 个官方没有的文件。两条纪律：

1. **从上游同步 miuix-ui 时必须把新文件一起搬进来** —— 自包含后缺文件会当场报  
   `Unresolved reference`（这是好事，别用「加回官方 artifact」来消错）。
2. **不要在 fork 里改「别人会调的内部签名」**（这次是 `DialogContentLayout` 少了三个参数）——  
   改了必须连带适配调用方。

**kotlinx-datetime 版本混用**：CMP material3 在 iOS 变体上依赖 0.7.x，会顶掉项目锁定的版本；  
两版的 `LocalDate` 是不同类，链接期必炸。升级必须全项目一起升（详见 root `build.gradle.kts`）。

**`Info.plist` 缺键**：CMP 1.7.3 起强制 `CADisableMinimumFrameDurationOnPhone`，漏了**一启动就崩**。

**wasmJs 门禁与 `--offline` 互斥**：只要 wasmJs 目标存在于构建中，插件就在配置阶段注册  
Distributions 仓库，与 `FAIL_ON_PROJECT_REPOS` 冲突 —— 所以该目标默认关闭、门禁独立跑。

**Java 名义上跨平台、实则 JVM 专有**：`String.format` 会按 Locale 输出数码字形（阿拉伯语 locale 下  
`"08:00"` 变非 ASCII），`System.currentTimeMillis` 在 Native 上不存在。`checkKmpPurity` 拦这些。

**同包同名 fork 的静默失败**：Android dex 按类名去重，版本错配不报错、只在走某条路径时炸  
（`NoSuchMethodError`）；Kotlin/Native 才是链接期硬错误。往 `:ui-shared` 搬文件前必须手工查撞名。

## 10. 红线原则

**数据兼容不可谈判。** 存量 SharedPreferences 数据在每步之后都必须正确读出。  
**每步可回退。** `:core` 出问题就退回单模块。  
**先验证再推广。**  
**不要让 fork 变枷锁。** Miuix fork 解决了定制问题，但会阻断跨平台。

> 安全网：`master` 上有永久标签 `backup/pre-merge-20261009`（合并前状态）。  
> `git branch <名字> backup/pre-merge-20261009` 即可恢复 —— 标签不过期，比 reflog（90 天）可靠。
