# 交接文档：单宿主导航改造 + Android 数据层 KMP 收口（2026-10-10 收工快照）

> **给新对话的接手人**：这份文档管两件事 ——
> **①「Activity → 路由」改造**（一~六节，**已收工**）；
> **②Android 数据层的 KMP 收口**（七节，**D1/D2 已完成**，执行记录见 7.7）。
> 宏观 KMP 迁移计划见 `docs/KMP_IOS_MIGRATION_PLAN.md`（含 ⑦–⑬ 数据层批次记录），本文不重复。
>
> **代码基线**：`master` `057d6ab` + `ca95feb` + `b974cf2`（**工作区已干净，全部已提交**）。
> Android `assembleDebug` 通过 · `:core:jvmTest` **241 用例全绿（已入库）** ·
> `checkKmpPurity` 185 文件通过 · `:core:compileKotlinLinuxX64` 通过 ·
> `:backdrop` / `:miuix` `compileKotlinWasmJs` 通过（`-Pnexio.wasm=true`）·
> `:core:compileKotlinIosArm64` / `IosSimulatorArm64` **在 Windows 上编译通过**（`b974cf2`）。

## ⚡ 下一手要做的（按优先级）

> **2026-10-10 更新**：原排第 1 的「测试不入库」不一致已消除（`057d6ab`），
> `skikoMain` 编译盲区已收窄（`ca95feb`）。

1. **`:core` 声明 `binaries.framework`** —— iOS 侧 Swift 才能 `import Shared`。
   **不做则 `compileKotlinIosArm64` 绿了也没用**。代码片段见 KMP 计划文档 **A0-1**。
2. **iOS 运行时验证（需 macOS）** —— ✅ **编译部分已在 Windows 上完成**（`b974cf2`，
   `-Pnexio.ios=true :core:compileKotlinIosArm64` + `IosSimulatorArm64` 均通过，
   `UserDefaultsStore` / `HttpService.ios` 都已被编译器验证）。
   macOS 只剩**链接与运行**：跑 7.7 节末的三条运行时验证（NSNumber 装箱等）+ 真机联网。
   ⚠ iOS target 仍是默认关 —— 开启会让 `--offline` 门禁挂掉（原因见 7.7）。
3. **建 Xcode 工程时同时做两件事**（漏了会白跑）：
   - `Info.plist` 加 `CADisableMinimumFrameDurationOnPhone = true` —— 否则**一启动就崩**（CMP 1.7.3 强制）
   - 提交 `YourApp.xcodeproj/xcshareddata/xcschemes/` —— 否则 CI 上 `xcodebuild` 报 scheme not found
4. **接 iOS 启动注入**：iOS 侧对应 `NexioApplication.onCreate` 的三件事 ——
   `AppStorage.init { name -> UserDefaultsStore(name) }`、`AppFiles.init(root, readAsset)`、
   `ScheduleAppearance.init`。参考 KMP 计划文档「给 iOS 开发者的交接清单 A 节」。
5. ~~**`HttpService.native`**~~ ✅ **已完成**（`b974cf2`）：`iosMain/HttpService.ios.kt`
   （NSURLSession delegate 流式读，零第三方依赖，**未引入 Ktor**），双 iOS 目标编译通过；
   占位挪到 `linuxX64Main`。剩下的是**真机运行验证**（WebDAV PROPFIND / 8MB 截断 / 断网抛错）。
6. **剩余 46 处 `getSharedPreferences` 不要动**（类别 C，理由见 7.7 末）。要动就独立批次 + 真机回归。

### ✅ 已消除的阻塞（2026-10-10）

| 原阻塞 | 现状 |
|---|---|
| 测试不入库但 build 已提交依赖 → 新克隆跑不到测试 | ✅ **已消除**（`057d6ab`）：241 个用例 + `school_index.pb` 夹具全部入库 |
| `androidApp/` 整个 untracked，`git clean -fd` 会与 `app/` 变双份 | ✅ **已消除**（`057d6ab`）：212 个文件入库，git 全识别为 `R100` 改名 |
| `skikoMain` 从未针对非 JVM 目标编译过 | ✅ **已收窄**（`ca95feb`）：wasmJs 编译通过。Native 特有 API（cinterop 等）仍需 macOS |
| `skikoMain` 未接给 iOS 源集，首次编译必撞 | ✅ **已闭环**：接线代码已写（条件式），且在 wasmJs 上验证过（曾报 10 处 `no actual declaration`，已修） |
| R6：Miuix 是 fork 的 `-android` 变体会阻断跨平台 | ✅ **已排除**：官方 5 个 artifact 都有 `-iosarm64` 变体（Windows 上纯依赖解析验证） |
| `HttpService` Native 实现是「调用即抛」占位 → iOS 无法联网 | ✅ **已消除**（`b974cf2`）：NSURLSession delegate 实现，双 iOS 目标编译通过 |
| 「Windows 上编译不了 iOS」→ iOS 代码只能盲写 | ✅ **推翻一半**（`b974cf2` 实测）：klib **编译**在 Windows 可行（只有链接需 macOS），iOS 代码从此可本机迭代验证 |

### ⚠ 新增的已知限制：wasmJs 门禁与 `check` 互斥

开启 `-Pnexio.wasm=true` 期间，Kotlin/Wasm 插件会在**配置阶段**注册 Distributions 仓库，
与 `settings.gradle.kts` 的 `FAIL_ON_PROJECT_REPOS` 冲突，`check` 直接失败
（配置期错误，**联网也救不了**）。所以跑门禁必须分两次：

```powershell
# PowerShell 里 -P 参数要加引号，否则会被当成任务名
.\gradlew.bat :core:check :miuix:check :backdrop:check --offline      # 默认态（红线）
.\gradlew.bat '-Pnexio.wasm=true' checkKmpWasmJs                        # 非 JVM 编译门禁（需联网）
```

---

## 📜 历史：本轮工作区改动（**已于 `057d6ab` / `ca95feb` 提交**）

<details>
<summary>提交前的工作区状态快照（保留供追溯）</summary>

- **新增**：`core/src/androidMain/kotlin/com/haooz/chedule/data/SharedPreferencesStore.kt`
  （从 `:androidApp` 搬来，包名未变）、`core/src/iosMain/kotlin/com/haooz/chedule/data/UserDefaultsStore.kt`
- **删除**：`androidApp/src/main/java/com/haooz/chedule/data/SharedPreferencesStore.kt`（内容已搬到 `:core`）
- **修改**：`core/build.gradle.kts`（iOS target 改为 `-Pnexio.ios=true` 开关）；
  `:androidApp` 下 14 个文件的存储调用（`NexioApplication` / `Theme` / `ThemeUtils` / `MainActivity` /
  `PreferenceSettingsScreen` / `UpdateSettingsScreen` / `UpdateDialog` / `UpdateChecker` /
  `TodayAssistant` / `ClassEndEffects` / `AndroidBridge` / `EducationalImportActivity` /
  `CourseReminderScreen` / `AppMaterialSettings` / `data/ScheduleAppearance`）
- **改名**：模块目录 `app/` → `androidApp/`，Gradle 模块 `:app` → `:androidApp`
  （为将来的 `iosApp` / `desktopApp` 让路）。同步改了 `settings.gradle.kts`、根 `.gitignore`（3 条规则）、
  `gradle/libs.versions.toml` 注释、两份文档里的全部引用。
  ⚠ **包名 / applicationId 未动**（仍是 `com.haooz.chedule`）—— 存量用户数据与升级路径不受影响。
  ⚠ 改名前**必须关闭 Android Studio**：Windows 上 IDE 会锁住 `app/` **目录本身**，
  实测 `mv app androidApp` 和 PowerShell `Rename-Item` 都报 `Permission denied`；
  但**子项可以逐个移出**（`mv app/* androidApp/` + `mv app/.[!.]* androidApp/`），最后 `rmdir app` 成功。
- `core/src/commonTest/`、`core/src/jvmTest/` 曾为 untracked，**现已随 `057d6ab` 入库**

</details>
---

## 一、目标与现状

**目标**：把 23 个 Activity 收敛成 1 个宿主 + 显式路由表。
为什么必须做：CMP 在 iOS 上跑在 `UIViewController` 里，没有 Activity；
**导航状态原本活在 Android 的 Activity back stack 里，iOS 拿不到**。

**现状：Manifest 23 → 3 —— Activity 侧已收工**

| 剩余 Activity | 行数 | 状态 |
|---|---:|---|
| `MainActivity` | 5903 | ✅ **宿主**（3 页 pager 常驻底座 + `AppNavHost` 叠加层） |
| `EducationalImportActivity` | 671 | ⛔ **按决定保留**（WebView + `@JavascriptInterface` 平台页） |
| `ImportAlias` | — | Manifest alias（指向 MainActivity，保留） |

> **`SwitchScheduleActivity` 是死代码，已直接删除**（见坑 15）。它没有路由 —— 也不需要：
> 手机端在 `MainActivity` 里内联渲染 `SwitchScheduleScreen`（带卡片形变动画，与路由转场冲突），
> 平板走 `TabletSwitchSchedulePane`。**不是所有 Activity 都要变成路由。**

已迁 **19 条路由**（`AppRoute.kt` 实测 `data object` 数量，早期文档写的 16 条是旧数，以这条为准）：
About / Changelog / License / PrivacyPolicy / PreferenceSettings / UpdateSettings /
Communication / LocalBackup / AppreciateAuthor / ScheduleImport / ScheduleExport /
ScheduleBackup / AiImport / WidgetIntro / CourseReminder / HolidaySettings / WebDavSettings /
CourseTimeSettings / CourseManage。

（19 条 = 「About 系 4 + 设置系 4 + 数据管理 3 + 带额外元素 8」；课程管理/时间设置是带内部
二级页的两个大页，也走路由。**切换课表不走路由**，原因见下方坑 14。）

代码基线：`master` `6367c55`。导航侧最后一个提交是
`6367c55 fix(ui): 修复顶栏回弹不显现材质`（坑 16）。

---

## 二、架构（已定型，别推翻）

```
MainActivity.setContent
└─ CourseScheduleTheme
   ├─ CompositionLocalProvider(LocalAppRouter provides router)   ← 最外层 provide
   │  └─ Box
   │     ├─ CourseScheduleApp(...)        ← 【常驻底座】永远组合，不参与路由 save/restore
   │     ├─ AppNavHost(router) { AppRouteContent(router, it) }   ← 子页叠加层（栈空=不渲染）
   │     └─ if (!privacyAgreed) PrivacyConsentScreen(...)
   └─ LaunchedEffect(pendingRoute) { router.navigate(it) }       ← 消费 Intent 深链
```

**文件**（都在 `androidApp/src/main/java/com/haooz/chedule/ui/navigation/`）：

| 文件 | 职责 |
|---|---|
| `AppRoute.kt` | 路由表。`id` 是持久化契约（`rememberSaveable` 用），**已发布的别改** |
| `AppRouter.kt` | 返回栈 + `LocalAppRouter`。栈**从空开始**（主界面是底座不是路由） |
| `AppNavHost.kt` | **单进度 `p` 驱动两层位移 + 黑幕** + `NavigationBackHandler`（含预测性返回）+ `SaveableStateHolder` |
| `NavTransitionState.kt` | 进度持有者（`value` / `animateTo` / `snapTo`）+ 主界面容器 `MainLayerTransition` |
| `AppRouteContent.kt` | **路由→页面映射表**，阶段 5 整体搬 `:ui-shared` |

⚠ **转场只有一个进度 `p`**，三层全部由它驱动（对齐 1da9c0a8 的
`SecondaryPageTransitionController`，它当初在 `ui/utils/ActivityTransitions.kt`）：

| 层 | 位移 | p=0 | p=1 |
|---|---|---|---|
| 栈顶页 | `(1 - p) × W` | 屏外右 | 完全覆盖 |
| 下一层 | `-0.24 × p × W` | 归位 | 左移 24% |
| 黑幕 | `alpha = 0.42 × p` | 不压暗 | 压暗 42% |

`push = 0→1`、`pop = 1→0`，**公式同一套、只是方向相反**。这是能同时支持
「打断续播」与「预测性返回跟手」的前提：任何时候把 p 落到一个新值，
整个层级都是自洽的，不需要重启动画。

- ⚠ **因此这里没有用 `AnimatedContent` 的 `EnterExitTransition`**：那是两条各自独立的
  动画，拿不到中间进度，既不能从半路续播也不能跟手。别再改回去。
- **主界面不在转场容器里**（坑 2），它的视差由 `mainTransition`（同一个 p，只在
  depth 跨越 0/1 时被驱动）推给 `MainLayerTransition`，在 MainActivity 层施加。
- **手势跟手/取消回弹**的剩余时长用 `settleOnGlobalCurve` 在整条曲线上反解，
  保证速度连续（原版 `animateEnter` 里「只有首次才 `snapTo(0)`」的等价实现）。

**关键 API**（`AppRouter`）：`hasOverlay`（false 时返回不拦截，Android 宿主的
预测性返回/「退出即隐藏后台」照常生效）、`current`、`underTop`、`lastPopped`、
`navigate` / `popBack`。

**`DocumentPageScaffold`**（`ui/components/`）—— 统一脚手架，折叠大标题 + 玻璃返回 +
采样层。新增两个可选参数：
- `endAction`：顶栏右侧按钮（签名同 `CollapsibleTopAppBar.endAction`）
- `overlay: @Composable BoxScope.(Backdrop) -> Unit`：**采样层之外**的内容（底部玻璃按钮、弹窗）

---

## 三、迁移套路（照这个 checklist 走，一个 Activity 约 15 分钟）

1. `AppRoute.kt` 加 `data object Xxx : AppRoute { override val id = "xxx" }` + `fromId` 分支
   （label 抄 Manifest 的 `android:label`，放进 KDoc）
2. `AppRouteContent.kt` 加分支：简单页用 `DocumentPageScaffold(title, onBack) { sb, glass -> XxxScreen(...) }`；
   有额外元素的用 `endAction` / `overlay`；Activity 级状态搬进 route 函数
3. 找到**所有** `XxxActivity::class.java` 启动点，改成 `LocalAppRouter.current.navigate(...)`
   （⚠ current 必须取在**组合层**，不能放进 onClick lambda）
4. 删 Activity 文件（`git rm`）+ 用 **Edit 工具**删 Manifest 块（见坑 ⑤）
5. 跑门禁：`:androidApp:assembleDebug` + 三模块 `check` + `checkKmpPurity` + `:core:jvmTest`

---

## 四、踩过的坑（每条都真实付过学费，新会话必读）

1. **能编译 ≠ 能用 —— CompositionLocal 未提供只在运行时崩**
   「课表备份页闪退」就是这么来的：`BackupAndMigrationScreen` 读 `LocalAppRouter`，
   但当时它还被残留的 `BackupAndMigrationActivity` 渲染，那个 Activity 没 provide，
   默认值是 `error(...)` → 一点开就抛。
   **教训**：给 Screen 加「读某个 CompositionLocal」前，排查**所有**渲染它的路径
   （路由 / 平板 pane / 残留 Activity / 预览）。迁移期「Screen 既在路由又在残留 Activity」最危险。

2. **主界面必须是常驻底座，不能进路由**
   若把 `CourseScheduleApp` 塞进 `AnimatedContent` + `SaveableStateHolder`，
   `rememberSaveable` 能救回来，但 `remember { mutableStateOf }` 救不回来
   （pager 位置 / 壁纸映射 /…），丢了就是「切到设置再回来 pager 位置变了」。

3. **返回上一页滚动状态丢失** → `AppNavHost` 里必须用 `SaveableStateHolder`：
   `stateHolder.SaveableStateProvider(route.id) { content(route) }`。
   已知取舍：被弹出的页面**不** `removeState`，重新打开会回到上次位置（文档页可接受）。

4. **转场别用 fadeIn/fadeOut**：子页全屏不透明，转场带透明度会让主界面透出来
   （用户报「动画那么奇怪」）。只滑不淡。

5. **改 Manifest 只用 Edit 工具，禁止脚本批量匹配**
   用 Python 逐行删 `<activity>` 块时把 receiver/permission 属性行也当孤儿删了（182 行），
   文件直接解析失败，靠 `git checkout` 救回。

6. **玻璃采样层级**：`backdrop` 只包内容；玻璃按钮/弹窗必须在**层外**（`overlay`），
   否则自己采样自己循环崩溃。原代码里十几处注释都强调过这点。

7. **`AnimatedContent` 的默认 `sizeTransform` 不是 null** —— 退出会「缩小并弹出」
   （本项已随转场重写作废，留着防手滑改回 `AnimatedContent`）
   `ContentTransform` 的 `sizeTransform` 默认值是 `SizeTransform()`（非空！），栈空时
   `targetState == null`、内容 lambda 不组合任何东西 → 目标尺寸测得 0×0，容器从全屏
   spring 收缩到 0（还带 `clipToBounds`）；而 slide 的偏移量取自同一个收缩中的尺寸，
   滑出距离也一起缩水。表现就是「退出时页面缩小并弹出」。

8. **页面位移用 `Modifier.layout` 自己 place，别用 `offset` / `graphicsLayer`**
   - `offset` 会把位移写进 constraints（`constraints.offset()`），子节点每帧真正
     re-measure —— 转场时整页（列表 / pager / 玻璃采样）跟着重测，必掉帧。
   - `graphicsLayer { translationX }` 不重测，但给整页套一层离屏 layer，
     可能干扰页内的 `layerBackdrop` 采样。
   - `layout { measurable.measure(constraints) /* 原样 */ ... place(dx, 0) }`：
     constraints 不变 → 子节点命中 measure 缓存，只重新 place。

9. **主界面的视差进度只能在 layout/draw 阶段读**
   在组合期读 `NavTransitionState.value` 会让 `CourseScheduleApp` 整树跟着转场每帧重组
   （平板展开/缩回直接掉帧，同一条坑 `TabletNavSideState.expandProgress` 早就踩过）。

10. **黑幕的显隐不能挂在「转场是否进行中」上**
    预测性返回跟手期间**没有任何动画在跑**（纯逐帧落值），那种写法会让人手势全程没有压暗。
    常驻挂载，只在 draw 阶段按 `p` 决定画不画。

11. **push 的起始帧必须在组合期把 `p` 归零**
    `Animatable.snapTo` 是 suspend，赶不上当帧绘制；等 `LaunchedEffect` 去做的话，
    新页会先以 `p=1`（最终位置）出现一帧，观感是闪一下。同理 pop 结束时
    「清 `outgoing`」与「`p` 拉回 1」必须在同一帧做完，否则新栈顶会被摆到屏外。

7. **LocalAppRouter.current 是 @Composable 调用**，必须在组合层取
   （`val r = LocalAppRouter.current` 然后在 onClick 里用 `r`）。

8. **Activity 级深链**（提醒通知点进来）：`onCreate`/`onNewIntent` 里不能 `startActivity`，
   记成 `pendingRoute`（mutableState），由 `setContent` 的 `LaunchedEffect` 消费。

9. **`onMultiWindowModeChanged` 是 Activity 回调，子页拿不到**
   原来 `CourseTimeSettingsActivity` / `CourseManageActivity` 各维护一份
   `isInFreeformWindow`（用于小窗圆角与窗口尺寸）。单宿主后统一读**宿主**
   `MainActivity.isInFreeformWindow`（同一个回调驱动，`onCreate` 里已由
   `updateFreeformWindowState()` 初始化）→ `AppRouteContent` 里的 `isInFreeformWindow()`。
   窗口尺寸用 `ApiCompat.currentWindowSize(context)`，圆角用
   `LocalActivity.current?.window`。

10. **`registerForActivityResult` 的「返回即刷新」可以直接删**
    `SettingsScreen` 原来用 `rememberLauncherForActivityResult` 在从时间设置页回来时
    `refreshSettings()` + `reloadCourses()`。查过 repository：**所有写路径**
    （`saveRoutine` / `deleteRoutine` / `notifyTimeConfigChanged` / `copyTimeConfigFromSchedule`）
    都 `notifyCourseChanged("settings")`，而 `CourseViewModel` 与 `SettingsViewModel`
    都注册了该监听 → **刷新本来就会发生**，回调是冗余的。
    ⇒ 迁移时先确认写路径有没有发广播，**有就直接删回调**，不要再造路由结果机制。

11. **`DocumentPageScaffold` 的 `overlay` 在模糊层「内部」**
    课程管理页的快捷菜单（长按卡片弹出：遮罩 + 卡片快照 + `ShortcutMenu`）在原 Activity 里
    是**根 Box 的兄弟**、不跟着背景一起模糊/缩放。它不能塞进 `overlay`
    （`overlay` 在采样层里、且会被外层的 `Modifier.blur` 吃到），
    必须留在 route 函数的根 `Box` 里当外层 `Box` 的兄弟。
    判据很简单：**这块内容要不要跟着背景一起模糊** —— 要就用 `overlay`，不要就放根 Box。

12. **`DisposableEffect` 的清理时机从「Activity finish」变成「路由弹出」**
    课程管理页用它在离开时删掉「本次新建但没编辑过」的空课程。
    路由弹出 → 组合退出 → `onDispose` 触发，语义一致。
    ⚠ 但要注意：宿主重建（旋转、进程回收）也会走一次 dispose → 清理，
    这与原 Activity 行为相同，不是新增问题。

13. 🔴 **薄壳迁移会「静默丢元素」，编译器抓不到 —— 必须逐个对账**
    WebDAV 页底部的「备份到云端 / 从云端恢复」两个按钮在 `ada9380` 里被漏掉了：
    状态变量 `backingUp` / `restoring` / `onBackup` / `onRestore` **全都在，只是没人渲染** ——
    Kotlin 认为「赋值也算使用」，不报未使用，页面照样 BUILD SUCCESSFUL，
    只有真机点进去才发现按钮没了（用户实测发现，已在 `WebDavSettingsRoute` 补回）。
    ⇒ **每个壳迁完，按「原 Activity 里的可交互元素」逐个对账**：
    `LiquidTopBarButton` / `LiquidGlassTextButton` / `OverlayDialog` / `TextButton` 的数量
    应与原 Activity 一致（顶栏返回那个会被 `DocumentPageScaffold` 吃掉）。
    已全量对账 7 个已删 Activity：只有 WebDAV 少了 2 个，其余一致。

14. 🔴 **迁之前先确认「这个 Activity 还有没有人启动」—— 死代码直接删，别硬造路由**
    `SwitchScheduleActivity`（1928 行，Manifest 里挂着）**全项目零启动点**：
    `git log -S "SwitchScheduleActivity::class.java"` 查不到任何一次 Intent 启动。
    真相是它早就退役了 —— 手机端在 `MainActivity` 里内联渲染 `SwitchScheduleScreen`
    （带卡片↔全屏形变动画：`showSwitchSchedule` / `switchAnimProgress`），平板走
    `TabletSwitchSchedulePane`。给它加路由反而会造出第二条互相打架的入口。
    ⇒ 处理：把 `SwitchScheduleScreen` + 4 个私有 Composable 抽成独立文件，Activity 直接删。
    ⇒ **判据**：某个 Screen 已在主界面/分栏里被内联渲染，**且**Activity 无启动点 → 删 Activity。
    反过来，若 Activity 有启动点但现在走的是另一条路（两条并存），那才是真的要合并。

15. **子页里的 `viewModel()` 现在拿到的是宿主的实例**
    原来每个 Activity 有独立 ViewModelStore，`CourseManageActivity` 里的 `CourseViewModel`
    是新实例；单宿主后与 `MainActivity` 共用同一个。好处是改完课返回主界面立刻是新的；
    ⚠ 若某页依赖「自己的 ViewModel 是干净的」，要显式传或自己 `viewModel(key=...)`。

16. 🔴 **app 里 fork 了 miuix 文件 = 两套互不相通的 CompositionLocal（顶栏回弹材质失效）**
    `CollapsibleTopAppBar` 搬进 `:miuix` 后，import 只能从 app 的 fork
    （`com.haooz.chedule.ui.utils.LocalOverScrollState`）改成 miuix 自带的
    （`top.yukonga.miuix.kmp.utils.LocalOverScrollState`）；而 32 个页面的
    `overScrollVertical()` 节点仍写 app fork 那套，`LocalOverScrollState` 从未被 provide
    → 顶栏读到的 `offset` 恒为 0 → `contentOffset >= 0f && overscrollOffset < 0f`
    「回弹也显现材质」整条分支变死代码。**编译器完全看不出来**（两边类型齐全、编译全绿）。
    ⇒ 修法：删掉 fork，全站统一 import miuix 那套（两份实现已逐行比对等价）；
    `DocumentPageScaffold` 再用 `CompositionLocalProvider` 给每页发一个独立实例，
    顶栏与内容共用同一实例，也避开 `compositionLocalOf` 默认值的全局单例。
    ⇒ **通则**：miuix 里已有的东西 app 不许再抄一份；查法
    `grep -rn "LocalXxx" androidApp/src miuix/src` 看是不是出现了两个同名 local。

---

## 五、顺带修掉的真实缺陷（与导航无关，记档）

- **全量备份校验器拒绝用户自己的备份**（两个残留键 `schedule_folder_map` /
  `schedule_time_config_{已删除课表}`），后果是**用户无法恢复自己的备份** —— 已放宽校验。
- 新增 `FullBackupRoundTripTest`（真实备份「导出→导入→导出逐字一致」，文档阶段 2.3 红线）。
- `ScheduleBackup` 下沉 `:core`，WebDAV 收敛到 `HttpService` —— **iOS 侧只剩
  `HttpService.native` 一处网络实现要写**。
- **顶栏「回弹也显现材质」失效**（`6367c55`）：`CollapsibleTopAppBar` 搬进 `:miuix` 后读
  miuix 那套 `LocalOverScrollState`，页面节点写的是 app fork 那套 → `offset` 恒 0，
  整条分支死代码。详见坑 16。

---

## 六、下一步（建议顺序）

1. ✅ **迁 `CourseTimeSettingsActivity`**（332 行）—— 已完成。
   它不是普通文档页：外层「背景缩放 + 模糊」动画 + 二级编辑页渲染在 `Scaffold` 外，
   迁移时整体搬进了 `CourseTimeSettingsRoute`，骨架套 `DocumentPageScaffold`。
2. ✅ **迁 `CourseManageActivity`**（571 行）—— 已完成。快捷菜单、删除确认、课程编辑
   全在 Activity 里，结构与时间设置页同构；多出来的三块叠加内容
   （`CourseEditScreen` / 快捷菜单遮罩+快照 / `ShortcutMenu`）留在根 `Box`（见坑 11）。
3. ✅ **`SwitchScheduleActivity` 已删除**（死代码，见坑 14）；`SwitchScheduleScreen`
   + 4 个私有 Composable 抽到 `ui/activities/SwitchScheduleScreen.kt`，逐行零改动。

### Activity 侧已收工，下一步转 KMP 本身

4. ✅ **Android 数据层 KMP 收口（D1/D2）** —— **已完成**（2026-10-10）：`SharedPreferencesStore`
   搬到 `core/src/androidMain`、新增 `core/src/iosMain/UserDefaultsStore`、`:androidApp` 直调 **88 → 46 处**。
   执行记录、踩坑清单与 iOS 待验证项见第七节 **7.7**。
5. `HttpService.native`（iOS 硬阻塞，WebDAV 只等这一处）/ 建 `:ui-shared`
   （`AppRouteContent`、`DocumentPageScaffold`、19 条路由对应的 Screen 的归宿）。
6. **别动**：`EducationalImportActivity`（WebView 平台页）；`wearable/`（不由本项目维护）。
7. 已知未做：路由的**预测性返回动画**（`PredictiveBackHandler`），目前只有 `BackHandler`。

---

## 七、Android 数据层迁移清单（2026-10-10 实测）

> 面向「Android 数据层后期也要迁」。数字都是 `grep` 实测，不是估的。

### 7.0 先看清 `:core` 已经有什么 —— **别再造轮子**

下沉前先查这张表，已有抽象直接接，不要新建第二套：

| `:core` 现成抽象 | 替代谁 | 备注 |
|---|---|---|
| `AppFile` / `AppFiles` | `java.io.File` | **注入式**（不是 expect/actual），`NexioApplication.onCreate` 里 `AppFiles.init(FileAppFile(filesDir), assets)` |
| `AppStorage` / `KeyValueStore` | `SharedPreferences` | 同样注入式；`:androidApp` 侧实现是 `SharedPreferencesStore` |
| `HttpService` | OkHttp（上层） | androidMain 已有 OkHttp 实现，**iOS 侧 `HttpService.native` 未写** |
| `JsonSupport` / `ScheduleCodec` | Gson / org.json | 有 `ScheduleCodecGsonParityTest` 对拍（jvmTest，未入库） |
| `Clock` | `System.currentTimeMillis()` | |
| `Lock` / `synchronizedOn` | `synchronized` | Kotlin/Native 线程模型（风险 R4）|
| `PlatformInfo` / `NexioLog` / `IoDispatcher` | `Build.*` / `Log.*` / 固定线程池 | 都是 expect/actual |

包名与迁移前**完全一致**（`com.haooz.chedule.data`），所以 `:androidApp` 侧 import 一行都不用改 ——
这是前面 ⑦~⑬ 批次能一次过的原因，**下沉时务必保持包名不变**。

### 7.1 三分类判定口径

| 类别 | 判据 | 处置 |
|---|---|---|
| **A 整体下沉** | 纯数据/纯算法，无平台 API | 直接搬 `core/src/commonMain` |
| **B 拆分下沉** | 逻辑跨平台 + 平台实现（Bitmap / 通知 / 文件）| 逻辑进 commonMain，平台实现进 `androidMain`（或走已有注入接口）|
| **C 永不迁** | 本身就是 Android 能力（AlarmManager / RemoteViews / Shizuku）| 留 `:androidApp`；**只把其中的纯算法抠出来** |

### 7.2 逐文件清单（`:androidApp` 非 UI 部分，31 个文件 / 9161 行）

**`data/`（4 个，795 行）—— 全部有明确归属，优先做**

| 文件 | 行 | Android 依赖 | 类别 | 处置与阻塞点 |
|---|---:|---|---|---|
| `WallpaperTransform.kt` | 61 | 仅 `androidx.compose.ui.geometry.Offset` | **A** | **零阻塞**，compose geometry 本就跨平台，可直接搬 |
| `FileAppFile.kt` | 32 | `java.io.File` | **B** | `AppFile` 的 Android 实现 → 搬 `core/src/androidMain`（接口已在 `:core`）|
| `SharedPreferencesStore.kt` | 94 | `android.content.SharedPreferences` | **B** | `KeyValueStore` 的 Android 实现 → 搬 `androidMain` |
| `ScheduleAppearance.kt` | 608 | `Bitmap` / `LruCache` / `SharedPreferences` / Gson×2 / `File` / `Build` | **B** | 拆：①样式 JSON → 走 `JsonSupport`（键常量 `AppearancePrefs` 已在 `:core`）；②壁纸 Bitmap 编解码 → `androidMain`（走 `AppFile`）；③`LruCache` → 自己写个 Map 或留平台侧 |

**`viewmodel/`（4 个，1076 行）—— 依赖只有 `AndroidViewModel` + `Application` + `viewModelScope`**

`CourseViewModel` / `SettingsViewModel` / `ScheduleViewModel` / `ShiftViewModel` 都是
「`AndroidViewModel(app)` 只为拿 `CourseRepository`」—— 而 **`CourseRepository` 已经是无参单例**
（⑫ 之后）。⇒ 逐个去掉 `Application` 依赖后即可下沉；`viewModelScope` 用
`CoroutineScope(SupervisorJob() + IoDispatcher)` 自管，或阶段 5 再统一。

**平台能力包（22 个，7166 行）+ 启动注入点 —— 类别 C，只抠纯算法**

| 包 | 文件数 / 行 | 主要平台 API | 值得抠出来的纯逻辑 |
|---|---:|---|---|
| `reminder/` | 10 / ~4500 | `AlarmManager` / `NotificationManager` / HyperOS 超级岛 / `BroadcastReceiver`×6 | ⭐ **`CourseReminderHelper` 里「下一节课的提醒时刻怎么算」** —— 与 `:core` 的 `CourseTimeResolver` 同族，下沉后 iOS 本地通知可直接复用 |
| `widget/` | 7 / ~1500 | `RemoteViews` / `AppWidgetManager` / `Bitmap` | 无（小组件是 iOS 开发者的事，不做）|
| `provider/` | 1 / 409 | `ContentProvider` 系（今日课程数据供给）| 「取今天的课」可复用 `:core` |
| `shizuku/` | 2 / 353 | Shizuku Binder（免打扰/静音）| 无，Android 专属 |
| `wearable/` | 2 / 409 | Wearable DataClient | 无，**不由本项目维护，别动** |
| `NexioApplication.kt` | 124 | `Application` | 保留在 `:androidApp`：启动注入点（`AppStorage.init` → `AppFiles.init` → `ScheduleAppearance.init` → 穿戴同步）。iOS 侧对应「3 个启动注入」，见 KMP 计划交接清单 A 节 |

### 7.3 散落在 `ui/` 里的数据层（别漏）

Gson 在 `:androidApp` 还剩 **22 处 / 10 文件**，其中 9 个在 `ui/`（真正的「数据层」只剩 `data/ScheduleAppearance` 一个）：

`data/ScheduleAppearance`、`ui/activities/BackupAndMigrationScreen`、
`ui/activities/EducationalImportActivity`、`ui/activities/LocalBackupScreen`、
`ui/screens/SettingsScreen`、`ui/screens/TodayAssistant`、`ui/utils/ScheduleExport`、
`ui/utils/ScheduleImport`、`ui/utils/UpdateChecker`、`ui/web/AndroidBridge`。

⚠ **R2 最高风险**：换序列化必须有真实用户备份做 round-trip 回归
（`ScheduleCodec` 已与 Gson 对拍过，`ScheduleAppearance` 的 `CombinationStyle` 还没对拍）。

### 7.4 实测基线（改完可对照）

| 项 | 数量 | 说明 |
|---|---:|---|
| `android.*` import | **349 处 / 93 文件** | 平台专用，真阻塞 |
| `androidx.*`（非 compose）| **99 处 / 44 文件** | 真阻塞。Top：`navigationevent` 20 / `core.content` 18 / `activity.compose` 15 / `lifecycle` 15 / `core.graphics` 11 |
| `androidx.compose.*` | 2665 处 | **跨平台，不算阻塞**，别拿它估工作量 |
| `java.*` import | **80 处 / 38 文件** | `java.util` 50 / `java.io` 18 / `java.net` 6 / `java.text` 5。最多的是 `ui/utils/CrashLogHelper`（10 处）|
| `ui/` 规模 | 99 文件 / 58207 行 | 阶段 5 的主战场 |

> 口径说明：早期文档写的「505 处 / 146 文件」把部分 androidx 算进来了，口径不一致。
> 以后一律用上表三行分开记。

### 7.5 建议分批（每批独立可验）

- **D1**（半天，零风险）：`WallpaperTransform` + `FileAppFile` + `SharedPreferencesStore` 三个小文件归位到 `:core`。
- **D2**（1~2 天）：`viewmodel/` 去掉 `Application` 依赖 → 下沉。
- **D3**（2~3 天，含 R2）：`ScheduleAppearance` 拆分下沉 —— **先补 `CombinationStyle` 的 Gson 对拍测试再动手**。
- **D4**（可选，独立）：`CourseReminderHelper` 的提醒时刻算法抠进 `:core`（iOS 收益最大的一条）。

### 7.6 数据层版 checklist

1. 先查 7.0 表：有没有现成抽象可接？有就接，**包名保持 `com.haooz.chedule.data` 不变**。
2. 判定 A/B/C；B 类先画清「哪半边跨平台」再动手。
3. 涉及序列化的：**先写 round-trip 测试**（导出→导入→导出逐字一致），再换实现。
4. 搬完跑门禁（第八节）。⚠ **Android 编过不算数**，两条互补的闸门都要看：
   - `checkKmpPurity` 会扫 `java.*` / `java.io.*` / `synchronized` / `Dispatchers.IO` /
     `String.format` / `System.currentTimeMillis` / `::class.java` / `Thread` 等，
     但**只扫 `core` / `backdrop` / `miuix` 的 `commonMain` + `skikoMain`** ——
     `:androidApp` 里写多少 `java.io` 它都看不见。
   - `:core:compileKotlinLinuxX64`（Kotlin/Native 目标）才是真正的兜底：
     上面那些静态规则漏掉的 API（比如变量形式的 `s.toByteArray()`）在这里才会暴露。
5. 提交前**关掉 Android Studio**（已两次删过工作区文件）。

### 7.7 执行记录：KeyValueStore 三平台闭环（2026-10-10）

> 本次把「Android 数据层迁移」的第一步做完：**存储抽象收口到 `:core`，Android / iOS 各一个平台实现**。
> 验收：`:androidApp:assembleDebug` 通过 · `:core:jvmTest` **241 用例全绿** · `checkKmpPurity` 185 文件通过 ·
> `:core:compileKotlinLinuxX64` 通过。

**结论先说**：本项目**没有 SQL 数据库**，用户数据 = 17 个 SharedPreferences 文件（课程表是
数百 KB 的 JSON 字符串塞在 `course_schedule_prefs`）+ 少量文件。所以「迁移到 KMP 数据库」
**不需要搬任何存量数据** —— 抽象层早就解耦好了，缺的只是 iOS 侧的实现。**不要引入
SQLDelight / Room 去重写存储**：那需要写一次性 SP → SQLite 迁移，而本项目 `Holidays`
等处遍布 `runCatching` 兜底恰恰说明历史数据格式变更多次、类型错配真实存在，一处漏兜就是用户课表没了。

| # | 动作 | 结果 |
|---|---|---|
| D1 | `SharedPreferencesStore` 从 `:androidApp` 搬到 `core/src/androidMain` | 包名不变（`com.haooz.chedule.data`），`:androidApp` 侧 import 零改动 |
| D1+ | 新增 `core/src/iosMain/UserDefaultsStore.kt` | `KeyValueStore` 的 NSUserDefaults 实现，**待 macOS 首次编译验证** |
| D1+ | `core/build.gradle.kts` 加 iOS target（**默认关闭**，`-Pnexio.ios=true` 开启） | 原因见下 |
| D2 | `:androidApp` 直调 `getSharedPreferences`：**88 处 → 46 处** | 改掉的 42 处全在 UI / 数据层；剩余 46 处全属类别 C |

**关键陷阱（改的时候真实踩到，下次务必对照）**

1. **`KeyValueEditor` 不可链式**
   `SharedPreferences.Editor.putXxx()` 返回 Editor 自身，所以 `putString(a,b).putBoolean(c,d)`
   在旧代码里很常见；`KeyValueEditor.putXxx()` 返回 `Unit`，链式**直接编译失败**
   （`Unresolved reference 'putBoolean'`）。必须逐条写。
2. **`putString(k, null)` 的隐式移除语义没了**（最危险的一条）
   Android 的 `Editor.putString(key, null)` 等价 `remove(key)`；`AndroidBridge` 三处靠它表达
   「null = 导入到当前课表」写 `target_schedule_id`。`KeyValueEditor.putString` 只收非空值，
   必须显式 `if (v != null) putString(...) else remove(...)`。**漏改会写不进去且不报错。**
3. **`getString(k, null)` → `getStringOrNull(k)`**
   `KeyValueStore.getString` 刻意收非空默认值；要「键不存在返回 null」得用顶层扩展函数
   `getStringOrNull`（**需要 import**，不是成员，漏 import 报 `Unresolved reference`）。
4. **`all()` 的类型必须精确**
   `CourseRepository` 用 `when (value) { is Int -> putInt … is Long -> putLong … }` 复制课表、
   `exportAllPreferences()` 导出全量备份 —— 类型判错会**静默丢设置**。iOS 侧因此在
   `suiteName.__kvtypes` 独立域里记了类型标记（详见 `UserDefaultsStore` 类注释）。
5. **`batchSave` 的 Editor 复用要重写成「累积写入块」**
   `ScheduleAppearance.pendingEditor` 原来共享一个 `SharedPreferences.Editor` 跨调用累积、
   最后 `apply()`；`KeyValueStore.edit` 是一次性事务、没有可跨调用的 Editor，
   已改为累积 lambda + `batchSave` 结束时一次性提交（语义等价）。

**iOS target 为什么默认关（别改回去）**

声明 iOS target 会让 `:core:check` 去解析 iOS 的 klib 依赖
（`kotlinx-datetime/coroutines/serialization` 的 `-iosarm64/-iossimulatorarm64` 变体），
而第八节门禁是 `--offline` 跑的 → `:core:check` 直接失败，
实测报错 `Could not resolve all files for configuration ':core:iosSimulatorArm64CompileKlibraries'`，
**把「Android 零回归」这条红线弄脏**。故改为 `-Pnexio.ios=true` 显式开启；
Windows 上本来也编译不了 iOS，默认关不损失任何验证能力。
macOS 上首次接入：`./gradlew -Pnexio.ios=true :core:compileKotlinIosArm64`（**需联网**）。

**iOS 侧首次编译必须重点验证的三点**

`UserDefaultsStore.kt` 在 Windows 上无法编译，以下靠 mac 上第一次 `compileKotlinIosArm64` 确认：
1. Kotlin 的 `Int/Long/Float/Boolean` 传给 `setObject(_:forKey:)` 是否自动装箱成 `NSNumber`
2. `persistentDomainForName` 对自定义 suite 是否返回非空（`all()` 依赖它）
3. `raw is String` 对 plist 里的 `NSString` 是否成立

**剩余 46 处为什么没改（建议保持不动）**

`reminder/`（35）+ `ui/utils/CrashLogHelper`（8）+ `widget/`（1）+ `NexioApplication`（2，注入点本身）
—— 全部是 7.1 判定的**类别 C「永不迁」**：`AlarmManager` / `RemoteViews` / `Build` / `FileProvider` / logcat。
它们永远不进 `:core` 的 commonMain，所以「存储调用平台无关」对它们没有实际收益；
而 `CourseReminderHelper` 有 20+ 处链式 `edit()`（陷阱 1），逐条拆开的回归风险不小
（改错 = 用户提醒失效）。iOS 的提醒能力是 `UNUserNotificationCenter` 另写一套，不会复用这些代码。
**要做就作为独立批次 + 真机回归验证，别顺手改。**

---

## 八、门禁命令（每次收工必跑）

```bash
export JAVA_HOME="D:\\JDK"; unset JAVA_TOOL_OPTIONS
./gradlew :androidApp:assembleDebug :core:check :miuix:check :backdrop:check \
  :core:compileKotlinLinuxX64 :core:compileTestKotlinLinuxX64 checkKmpPurity :core:jvmTest --offline
```

全绿标准：`BUILD SUCCESSFUL` + `checkKmpPurity: 扫描 185 个跨平台源文件，未发现 JVM 专有 API ✅`
+ `:core:jvmTest` 241 用例无 FAILED。

测试文件**不入库**（用户长期要求）：`core/src/commonTest/`、`core/src/jvmTest/` 保持 untracked。
