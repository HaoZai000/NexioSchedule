# 交接文档：单宿主导航改造（2026-10-09 收工快照）

> **给新对话的接手人**：这份文档是「Activity → 路由」改造的完整交接。
> 宏观 KMP 迁移计划见 `docs/KMP_IOS_MIGRATION_PLAN.md`（含 ⑦–⑬ 数据层批次记录），本文不重复。
> 代码基线：`master` `fcb77c1`，Android `assembleDebug` 通过，`:core:jvmTest` 241 用例全绿。

---

## 一、目标与现状

**目标**：把 23 个 Activity 收敛成 1 个宿主 + 显式路由表。
为什么必须做：CMP 在 iOS 上跑在 `UIViewController` 里，没有 Activity；
**导航状态原本活在 Android 的 Activity back stack 里，iOS 拿不到**。

**现状：Manifest 23 → 6**

| 剩余 Activity | 行数 | 状态 |
|---|---:|---|
| `MainActivity` | 5903 | ✅ **宿主**（3 页 pager 常驻底座 + `AppNavHost` 叠加层） |
| `CourseTimeSettingsActivity` | 332 | ⬜ 待迁（作息编辑 + 快照捕获） |
| `CourseManageActivity` | 571 | ⬜ 待迁（快捷菜单 + 删除确认 + 课程编辑） |
| `SwitchScheduleActivity` | 1928 | ⬜ 待迁（**内联 5 个 Composable**，需先抽 `SwitchScheduleScreen`） |
| `EducationalImportActivity` | 671 | ⛔ **按决定保留**（WebView + `@JavascriptInterface` 平台页） |
| `ImportAlias` | — | Manifest alias（指向 MainActivity，保留） |

已迁 **14 条路由**：About / Changelog / License / PrivacyPolicy / PreferenceSettings /
UpdateSettings / Communication / LocalBackup / AppreciateAuthor / ScheduleImport /
ScheduleExport / ScheduleBackup / AiImport / WidgetIntro / CourseReminder / HolidaySettings / WebDavSettings。

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

**文件**（都在 `app/src/main/java/com/haooz/chedule/ui/navigation/`）：

| 文件 | 职责 |
|---|---|
| `AppRoute.kt` | 路由表。`id` 是持久化契约（`rememberSaveable` 用），**已发布的别改** |
| `AppRouter.kt` | 返回栈 + `LocalAppRouter`。栈**从空开始**（主界面是底座不是路由） |
| `AppNavHost.kt` | `AnimatedContent` 转场（只滑不淡）+ `BackHandler(enabled = hasOverlay)` + `SaveableStateHolder` |
| `AppRouteContent.kt` | **路由→页面映射表**，阶段 5 整体搬 `:ui-shared` |

**关键 API**（`AppRouter`）：`hasOverlay`（false 时 BackHandler 不拦截，Android 宿主的
预测性返回/「退出即隐藏后台」照常生效）、`current: AppRoute?`、`navigate` / `popBack`。

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
5. 跑门禁：`:app:assembleDebug` + 三模块 `check` + `checkKmpPurity` + `:core:jvmTest`

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

7. **LocalAppRouter.current 是 @Composable 调用**，必须在组合层取
   （`val r = LocalAppRouter.current` 然后在 onClick 里用 `r`）。

8. **Activity 级深链**（提醒通知点进来）：`onCreate`/`onNewIntent` 里不能 `startActivity`，
   记成 `pendingRoute`（mutableState），由 `setContent` 的 `LaunchedEffect` 消费。

---

## 五、顺带修掉的真实缺陷（与导航无关，记档）

- **全量备份校验器拒绝用户自己的备份**（两个残留键 `schedule_folder_map` /
  `schedule_time_config_{已删除课表}`），后果是**用户无法恢复自己的备份** —— 已放宽校验。
- 新增 `FullBackupRoundTripTest`（真实备份「导出→导入→导出逐字一致」，文档阶段 2.3 红线）。
- `ScheduleBackup` 下沉 `:core`，WebDAV 收敛到 `HttpService` —— **iOS 侧只剩
  `HttpService.native` 一处网络实现要写**。

---

## 六、下一步（建议顺序）

1. **迁 `CourseTimeSettingsActivity`**（332 行，三个复杂里最小）：读它的
   `onEditRoutine` 快照捕获逻辑，状态搬进 route 函数，`isInFreeformWindow`
   需要改成从 `LocalConfiguration`/MainActivity 取（Activity 回调没了）。
2. **迁 `CourseManageActivity`**（571 行）：快捷菜单、删除确认、课程编辑全在 Activity 里。
3. **迁 `SwitchScheduleActivity`**（1928 行）：先把内联的 `SwitchScheduleScreen`
   （5 个 Composable）抽成独立文件，再按套路走。
4. 之后：`HttpService.native`（iOS 硬阻塞）/ 建 `:ui-shared`（`AppRouteContent`、
   `DocumentPageScaffold`、14 个 Screen 的归宿）。
5. **别动**：`EducationalImportActivity`（WebView 平台页）；Gson 的 10 个文件全在
   `:app` UI 层，**现阶段不需要动**（阶段 5 搬 UI 时才必须清）。

---

## 七、门禁命令（每次收工必跑）

```bash
export JAVA_HOME="D:\\JDK"; unset JAVA_TOOL_OPTIONS
./gradlew :app:assembleDebug :core:check :miuix:check :backdrop:check \
  :core:compileKotlinLinuxX64 :core:compileTestKotlinLinuxX64 checkKmpPurity :core:jvmTest --offline
```

全绿标准：`BUILD SUCCESSFUL` + `checkKmpPurity: 扫描 185 个跨平台源文件，未发现 JVM 专有 API ✅`
+ `:core:jvmTest` 241 用例无 FAILED。

测试文件**不入库**（用户长期要求）：`core/src/commonTest/`、`core/src/jvmTest/` 保持 untracked。
