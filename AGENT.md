# AGENT.md — NexioSchedule 工程实现与开发约定

本文档记录 NexioSchedule 的模块架构、技术栈、核心实现细节、开发红线与验证命令，供开发者与 AI 代理协作时查阅。

---

## 1. 工程结构

4 个 Gradle 模块 + 1 个 Xcode 工程：

```text
NexioSchedule/
├── androidApp/     Android 宿主：Single Activity 路由装配、小组件、课程提醒、Shizuku、穿戴同步、本地 MCP 服务
├── iosApp/         iOS 宿主（Xcode 工程）+ WidgetKit 小部件
├── desktopApp/     桌面宿主（Compose Multiplatform Desktop / JVM）
├── ui-shared/      共享 UI 层：页面、组件、主题、动效、天气、AI 教务导入、i18n（android + jvm + ios）
└── core/           共享数据层：SQLDelight 实体表、仓库、备份/导入导出编解码、节假日、WebDAV、平台接口
```

> 历史 `:miuix` 本地模块已移除，UI 组件现统一来自独立库 `NexioUI`，仅保留 `miuix-blur-android` 供关于页纹理模糊使用。

### 本地复合构建替换（`settings.gradle.kts`）

`SoftGlass` 与 `NexioUI` 为独立仓库，默认通过 JitPack 拉取：
- 当同级目录存在 `../softglass/build.gradle.kts` 时，自动将 `com.github.HaoZai000.SoftGlass:softglass` 替换为本地源码构建。
- 当同级目录存在 `../nexioui/build.gradle.kts` 时，自动将 `com.github.HaoZai000.NexioUI:nexioui` 替换为本地源码构建。

---

## 2. 技术栈与关键依赖

版本来源：`gradle/libs.versions.toml` 与各模块 `build.gradle.kts`。

| 层 | 选型 | 当前版本 |
|---|---|---|
| 语言 / 构建 | Kotlin / AGP | `2.4.10` / `9.2.1` |
| 跨端 UI | Compose Multiplatform | `1.12.0` |
| Android Compose | Jetpack Compose BOM + Material3 | `2026.05.01` / `1.5.0-alpha19` |
| 控件库 | [NexioUI](https://github.com/HaoZai000/NexioUI)（JitPack） | `3fa6c2fb`（对应 `0.1.2`） |
| 柔光玻璃 | [SoftGlass](https://github.com/HaoZai000/SoftGlass)（JitPack） | `0.1.4` |
| 本地数据库 | SQLDelight（SQLite，单库 `nexio.db`） | `2.3.2` |
| 跨平台基础 | `kotlinx-datetime` / `kotlinx-coroutines` / `kotlinx-serialization-json` | `0.6.2` / `1.9.0` / `1.9.0` |
| 动画 / 配色 | Compottie (`compottie-lite`) / MaterialKolor | `2.3.3` / `4.1.1` |
| Android 专有 | OkHttp / Shizuku / `miuix-blur-android` | `4.12.0` / `13.1.5` / `0.9.4` |
| Android SDK | compile / target / min | `37` / `37` / `26` |

> **JitPack 缓存注意事项**：若上游强推移动了 release tag（例如 `NexioUI` `0.1.2` 曾从 `b18a2ca9` 移至 `3fa6c2fb`），JitPack 可能长期缓存旧的失败构建；此时应直接锁定目标 commit hash。

---

## 3. 核心架构与实现约定

### 3.1 KMP 平台中立性
- `:core` 与 `:ui-shared` 的 `commonMain` 严禁引用 `android.*`、`java.*` 等平台 API；平台差异统一通过 `expect / actual` 或注入接口（`ReminderController`、`BackupFileController`、`ScheduleFileController`、`WebHost`、`ScheduleEffects` 等）隔离。
- 每次修改共享层必须通过 `./gradlew checkKmpPurity` 静态扫描门禁。

### 3.2 本地存储与数据模型
- 所有业务数据统一落在单库 `nexio.db`（键值表 + SQLDelight 实体行表同库同事务）。
- **行表为唯一真相**：`CourseRepository` / `ScheduleCatalog` 的课程、作息（`time_config`）、课表设置与文件夹以 SQLite 行表为准；写失败必须向上抛出，禁止静默吞错后继续修改内存缓存。
- **作息大课间（`TimeRoutine` / `TimeConfig`）**：
  - 支持 `longBreakMorningEnabled`、`longBreakAfternoonEnabled`、`longBreakEveningEnabled` 三个时段独立开关，以及各自的大课间起始节次与时长。
  - 反序列化兼容旧版 JSON：缺省时段开关时自动继承旧的 `longBreakEnabled` 字段。

### 3.3 UI、动效与国际化
- **柔光玻璃与圆角**：页面级采样层配合 `SoftGlass` 提供模糊、折射与边缘高光；外观页开洞与课程卡片共用连续曲率圆角（Squircle）轮廓。
- **双语文案（`ui-shared/.../i18n/`）**：所有用户可见文本通过 `L("中文", "English")` 管理，中英文占位符（`%s` / `%d`）数量与类型必须严格一致；保持自然简洁的产品文案，避免开发者术语泄漏。

### 3.4 已知交互与组件约束
- **`NexioUI 0.1.2` 开关交互**：
  - 禁止手工用外层 `Row.clickable { checked = !checked }` 包裹 `Switch`（`Switch` 在 `PointerEventPass.Initial` 处理点击时未消费 `UP` 事件，会导致外层 `clickable` 同帧反转抵消）；列表开关一律使用 `SwitchPreference`。
  - 单击 `SwitchPreference` 滑块可能同帧触发两次 `onCheckedChange`；凡回调带有副作用（权限申请、服务启停、写盘、发广播等）必须加 `if (newValue == currentValue) return` 同值防重入守卫。

### 3.5 错误处理与协程并发红线
- **Fail-fast**：禁止在存储读写、快照采集、编解码核心路径使用静默 `runCatching { ... }.getOrDefault(...)` 或空 `catch (_: Exception) {}` 掩盖内部错误（尤其是 MCP 撤销快照、作息回填、脚本历史追加等可能导致覆写用户数据的路径）。
- **协程取消传播**：在 `suspend` 函数中需要捕获业务异常时，一律使用 `core/.../data/util/SuspendRunCatching.kt` 提供的 `suspendRunCatching`，或在 `try/catch` 中显式 `catch (e: CancellationException) { throw e }`，不得吞掉结构化并发取消信号。

---

## 4. 构建、测试与验收命令

本机若未在 `local.properties` 配置 `sdk.dir`，执行 Android 相关 Gradle 任务时需显式传入 `ANDROID_HOME`：

```bash
# 1. KMP 平台中立检查 + 共享层全部单测
ANDROID_HOME=/path/to/Android/Sdk ./gradlew checkKmpPurity :core:check :ui-shared:check

# 2. 强制重跑 :core JVM 单测（排查缓存/偶发问题）
ANDROID_HOME=/path/to/Android/Sdk ./gradlew :core:jvmTest --rerun-tasks

# 3. 编译桌面端
./gradlew :desktopApp:compileKotlinJvm

# 4. 构建 Android Debug APK
ANDROID_HOME=/path/to/Android/Sdk ./gradlew :androidApp:assembleDebug

# 5. 验证 Android Release R8 混淆与资源裁剪
ANDROID_HOME=/path/to/Android/Sdk ./gradlew :androidApp:minifyReleaseWithR8
```
