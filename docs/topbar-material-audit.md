# 滚动后顶栏材质出现 —— 全站代码与阈值审计

> 审计范围：首页（今日 / 课表 / 设置）+ 所有二级、三级、四级路由页。
> 审计对象：**「滚动 → 顶栏材质（玻璃按钮底 / 渐进模糊带 / 表面色遮罩）出现」这一条链**的判据、阈值、动画、初值。
> 结论口径只针对「材质何时出现」，不含玻璃光学参数（那部分唯一来源是 `AppMaterialSettings` / `SoftGlassStyle`，已合规）。

---

## 〇、先对齐「首页 3 页」的口径

当前主 pager 的页数是 `CourseScheduleAppState.mainPagerPageCount`：手机普通 2 页（今日 / 课表）、排班 1 页、平板 rail 5 页（今日 / 课表 / **内嵌设置** / 课程管理 / 切换课表）。

- 若按「今日 / 课表 / 设置」三页理解：手机端设置已是二级路由（`AppRoute.Settings`），平板端设置仍是内嵌第 3 页，**两条路径的顶栏分别是 `SettingsTopBar` 与 `DocumentPageScaffold`，材质逻辑不同源**（见 2.1、2.2）。
- 今日 / 课表两页的顶栏都画在 `CourseScheduleAppMainContent` 的**共享顶栏槽**（`Scaffold(topBar)`），不在 pager 内 —— 这一点是下面 P0 的根因。

---

## 一、机制总览：材质出现由三层各自决定

| 层 | 谁决定 | 唯一来源 | 现状 |
|---|---|---|---|
| 判据（何时算「滚过阈值」） | `contentOffset < -threshold`，或「在顶部且向下回弹」 | ❌ **无唯一来源** | 至少 4 套写法 |
| 动画（怎么过渡） | `folmeSpring(damping=1.0, response=0.6/0.4)` | ❌ **无唯一来源** | 5 份副本 |
| 渲染（材质长什么样） | `LiquidTopBarButton(backdropAlpha, shadowAlpha)` / `ProgressiveBlurTopBar(maskAlpha)` | ✅ 已收口 | 合规 |

**关键结构事实**：`CollapsibleTopAppBar` 内部自己算了一遍判据 + 一套 Animatable（`showButtonShadow` → `backdropAlpha` / `shadowAlpha`），用户可见的按钮玻璃与阴影吃的是这一份。`TopBarMaterialAlpha.kt` 又把**同一个判据 + 同一套弹簧**抄了一份，专供 `ProgressiveBlurTopBar.maskAlpha`（无材质档的渐变遮罩）。所以同一个页面、同一次滚动，背后跑着**两套独立的 derivedStateOf + 两套 Animatable + 两条 LaunchedEffect**。

---

## 二、阈值清单（逐页）

「阈值」列 = 触发材质所需的滚动量；「初值」= 进入页面时 alpha 的起始值。

### 2.1 首页

| 页面 | 顶栏壳 | 判据来源 | 阈值 | 初值 | maskAlpha | 糊带高度 |
|---|---|---|---|---|---|---|
| 今日 | `TodayTopBar`（共享顶栏槽） | `CollapsibleTopAppBar` 内部 + `rememberTopBarMaterialAlpha` | 10dp | 取当前判定 | ✅ 已传 | 默认 `80+sb / 120` |
| 课表 | `ScheduleTopBar`（共享顶栏槽） | 同上 | 10dp | 取当前判定 | ✅ 已传 | **`120+sb / 160 / 112`**（自定义，含星期行） |
| 设置（手机=二级页 / 平板=第 3 页） | `DocumentPageScaffold` + `SettingsTopBar`（rail，`blurAlpha=0`） | `CollapsibleTopAppBar` 内部 | 10dp | 取当前判定 | ❌ 默认 1f | 默认 / rail 不糊 |
| 设置页搜索框 | `SettingsSearchHeader`（headerOverlay） | **页面自己写**：`listScrolled` | **2px** | 0 | — | — |

⚠ 设置页的搜索框与它所在页面的顶栏按钮**不同口径**：列表滚 2px 搜索框就上玻璃，顶栏按钮要等 10dp。

### 2.2 路由页（二级 / 三级 / 四级）

一个路由的「深度」不影响材质逻辑 —— 深度只决定返回栈。真正决定实现的是**它用哪个壳**：

| 壳 | 覆盖路由（数量） | 阈值 | maskAlpha |
|---|---|---|---|
| `DocumentPageScaffold`（统一壳） | Settings、Feedback、FeedbackHistory、FeedbackRoadmap、Weather、PreferenceSettings、UpdateSettings、UpdatePreferences、LocalBackup、AiImport、HolidaySettings、WidgetIntro、CourseReminder、WebDavSettings、ImportExport、CourseTimeSettings、CourseManage、Changelog、License、PrivacyPolicy、Communication、ScheduleSettings（21+） | 10dp（`CollapsibleTopAppBar`） | ❌ **全部未传 → 恒 1f** |
| `EducationalImportScreen` 自带 Scaffold | EducationalImport | 10dp | ❌ 未传 |
| `SwitchScheduleScreen` 自带 Scaffold | 课表切换（更多菜单进） | 10dp | ❌ 未传 |
| `WebImportScreen` 自带 Scaffold | WebImport | 10dp（按钮） | ❌ 未传，且 `blurAlpha=0` + `showShadow=false` |
| `CourseEditScreen` / `TimeConfigEditScreen` | 课程编辑 / 作息编辑（页内全屏层） | 10dp | ❌ 未传 |
| `AboutScreen` 自带 Scaffold | About | **`scrollProgress >= 1f`**（= 滚过 340dp logo 占位） | ✅ 已传（用同一 `scrollProgress`） |
| 平板三栏 | TabletSettings / TabletSwitchSchedule / TabletCourseManage | **自写 `rememberPaneMaskAlpha` 10dp** | 设置栏 / 切换课表栏给右上角按钮；课程管理栏无按钮、无 alpha |

**混用形态**：`ImportExport` / `Feedback` / `HolidaySettings` / `FeedbackRoadmap` 在 `headerOverlay` 里挂 `CapsuleSegmentedControl`，后者**自己又算一遍** `contentOffset < -10dp`（同一个 10dp 的第五份副本），只是恰好与顶栏同源 `scrollBehavior`，所以肉眼基本对得上。

### 2.3 `10dp` 字面量副本：6 处

| 文件 | 行 | 用途 |
|---|---|---|
| `miuix/.../CollapsibleTopAppBar.kt` | 414 | 按钮底 + 阴影（**事实上的主实现**） |
| `ui-shared/.../TopBarMaterialAlpha.kt` | 16 | 无材质档遮罩 |
| `ui-shared/.../CapsuleSegmentedControl.kt` | 101 | 分段胶囊玻璃 + 阴影 |
| `ui-shared/.../TabletSettingsScreen.kt` | 305 | 平板栏遮罩 + 右上角按钮 |
| `ui-shared/.../CourseDetailScreen.kt` | 259 | 程序化滚动时手工注入 `contentOffset` |
| `ui-shared/.../SchoolSelectionScreen.kt` | 104 | 同上 |

另有 `2px` 一处（`SettingsScreen.kt:416`）、`scrollProgress >= 1f` 一处（`AboutScreen.kt:269/276`）。

### 2.4 弹簧副本：5 处，参数全同（`damping=1.0`，显 `response=0.6` / 隐 `0.4`）

`CollapsibleTopAppBar.kt:433-442`、`TopBarMaterialAlpha.kt:51-60`、`CapsuleSegmentedControl.kt:112-121`、`TabletSettingsScreen.kt:310-318`、`SettingsSearch.kt:96-104`。

参数一致，但**五份各自维护**：改一处手感，其余四处不会跟。

---

## 三、发现的问题（按严重度）

### P0 —— 首页「顶部下拉回弹出材质」实际是死代码

`CollapsibleTopAppBar` 与 `TopBarMaterialAlpha` 的判据都含这一支：

```kotlin
contentOffset >= 0f && overScrollState.offset < 0f   // 在顶部且向下回弹
```

`overScrollState = LocalOverScrollState.current`。而：

- 首页顶栏在 `Scaffold(topBar = …)` 里（`CourseScheduleAppMainContent.kt:487-542`）；
- 页面自己的实例 provide 在**内容树深处**：`TodayScreen.kt:530`、`MainScheduleScreen.kt:882`；
- 全仓 `LocalOverScrollState provides` 只有 5 处，没有任何一处在顶栏槽外层。

⇒ 顶栏读的是 `compositionLocalOf { OverScrollState() }` 的**全局默认实例**，而 `overScrollVertical` 节点写在页面自己的实例上，两者不是同一个对象 ⇒ 该分支在首页恒为 `false`。
（`DocumentPageScaffold.kt:137` 把 `Scaffold` 整个包在 provider 内，所以**二级/三级页这一支是活的**。）

这是明确的行为不一致：**下拉回弹时，二级页顶栏会出材质，首页不会**。更糟的是，若将来出现一个「没 provide 却挂了 `overScrollVertical`」的页面，它会写全局实例，首页顶栏会在那个页面回弹时莫名其妙亮一下（跨页串味）。

验证方式：在 `ScheduleTopBar` 里临时打印 `LocalOverScrollState.current.offset`，在首页顶部下拉 —— 恒为 0；同样的打印放进 `DocumentPageScaffold` 的顶栏，数值会随回弹变化。

### P1 —— 三个阈值口径并存

| 口径 | 出现位置 | 观感 |
|---|---|---|
| 10dp | 顶栏按钮 / 遮罩 / 分段胶囊 / 平板栏 / 两个页面的手工注入 | 主口径 |
| 2px | 设置页搜索框 | 几乎「一动就上材质」 |
| `scrollProgress >= 1f` | 关于页（340dp） | 要滚过整块 logo 才上材质 |

注释还写着「口径与教务导入页一致」，但教务导入页实际是 10dp（`CollapsibleTopAppBar` 给 `onAlphaChanged` 的值），设置页是 2px —— **注释与实现已经不符**。

### P1 —— `maskAlpha` 只有 3 处传，其余默认 1f

无材质档（`useGradientMask`）下，渐变遮罩就是顶栏唯一的「材质」。它由 `maskAlpha` 控制：

- 传了（滚到顶隐藏）：首页今日、首页课表、关于页；
- 没传（**恒为 1f，滚到顶也一直显示**）：`DocumentPageScaffold`（21 个路由）、教务导入、课表切换、Web 导入、课程编辑、作息编辑。

⇒ 无材质档用户会在「首页滚到顶时顶栏干净」和「进任意子页后顶栏永远有一层遮罩」之间来回切。

### P1 —— Web 导入页顶栏按钮永远没有玻璃材质

`WebImportScreen.kt:201-205`：`blurAlpha = 0f` + `CollapsibleTopAppBar(showShadow = false)`。`showShadow = false` 会让 `showButtonShadow` 直接返回 `false`，于是 `backdropAlpha` 恒 0 → `LiquidTopBarButton` 的玻璃层 `alpha = 0`，只剩实底圆。
这一页的按钮在**任何滚动位置都不会出现玻璃材质**（可能是有意让位给网页自身的色条，但代码里没有一行注释说明，属于潜在回归点）。

### P2 —— 糊带高度两套口径

- 首页课表：`120dp + statusBar` / 无状态栏 `160dp` / 折叠态 `112dp`（`ScheduleTopBar.kt:154-158`，与 `scheduleContentTopPadding` 手工同源）；
- 其余全部：`80dp + statusBar` / 无状态栏 `120dp`（`ProgressiveBlurTopBar` 默认）。

课表页多出的 40dp 是为了盖住星期行 —— 合理，但**没有任何注释说明这条口径只此一家**，切 tab 时糊带高度会跳 40dp。

### P2 —— 平板三栏不一致

三栏的 `TabletPaneTopChrome` 都只画**常驻**渐进模糊（压暗遮罩已全局移除），差别在「滚动是否驱动任何东西」：

| 栏 | 常驻模糊 | 滚动驱动的 alpha |
|---|---|---|
| `TabletSettingsScreen` | ✅ | ✅ `rememberPaneMaskAlpha` → 右上角按钮 |
| `TabletSwitchSchedulePane` | ✅ | ✅（借用同一函数，喂 `-leftScrollPx`） |
| `TabletCourseManagePane` | ✅ | ❌ **无**（该栏没有顶栏按钮，滚动时顶栏零变化） |

且 `rememberPaneMaskAlpha` 的 `Animatable(0f)` 是**写死初值**，不像手机端那样取「当前判定」；`remember` 生命周期被重建（切子页）时会从 0 重弹一次。

另：`TabletSettingsScreen.kt:296-300` 的 KDoc 仍写「本栏顶部表面色遮罩 / 右上角按钮共用的 alpha」，但函数体与 `TabletPaneTopChrome` 已不再画遮罩 —— 注释已过时。

### P2 —— `showSmallTitle` 阈值也分叉

`CollapsibleTopAppBar` 默认 `collapsedFraction >= 0.45f`；关于页显式传 `scrollProgress > 0.5f`。同一语义，两个数。

### P2 —— 无材质档下白跑动画

`maskAlpha` 只在 `useGradientMask` 分支被读，但有材质档时这两条 `LaunchedEffect` + 2 个 `Animatable` 依然每次滚动全跑（`TopBarMaterialAlpha.kt` 无条件创建）。属可省的每帧开销。

### P2 —— `CourseDetailScreen` 缺 `topInsetAdjustment`

`CourseDetailScreen.kt:212-215` 只传 `backdrop` + `noTopStatusBar`，没传 `topInsetAdjustment`（原生文档栏 inset）。iOS 原生导航栏接管时糊带高度会多算一截；同族页面（SwitchSchedule / CourseEdit / TimeConfigEdit / DocumentPageScaffold）都传了。

### 附：文档与实现脱节

`topics/glass.md` 记录「`ProgressiveBlurTopBar.tintIntensity`=0」，实际默认值是 `0.2f` 且**全站无一个调用点覆盖**。`edgeFadeStart`（默认 0.88）同样无覆盖。这两个值目前是事实上的全局常量，但写在组件默认参数里，改一个数字会影响全部 30+ 个顶栏。

---

## 四、建议的收口方案

1. **新建唯一来源** `ui/components/TopBarMaterial.kt`：
   ```
   val MaterialScrollThreshold = 10.dp
   val MaterialShowSpec = folmeSpring(damping = 1f, response = 0.6f)
   val MaterialHideSpec = folmeSpring(damping = 1f, response = 0.4f)
   fun rememberMaterialAlpha(scrollBehavior): () -> Float   // 含 overscroll 分支
   fun rememberMaterialAlpha(visible: Boolean): () -> Float
   ```
   `CollapsibleTopAppBar` 内部、`CapsuleSegmentedControl`、`TabletSettingsScreen`、两个 `snapshotFlow` 注入点全部改为引用它（miuix 不能依赖 ui-shared，所以阈值/弹簧需要 miuix 侧提一个 `internal` 常量再由 ui-shared 转发，或直接把判据整体下沉到 miuix）。

2. **修 P0**：把 `OverScrollState` 提升到「顶栏 + 内容共同作用域」。可行做法是每页一个实例提到 `CourseScheduleAppMainContent` 的顶栏槽层，顶栏按 `mainTabPosition.currentPage` 取当前页那个；删除 `TodayScreen` / `MainScheduleScreen` 内部的 `provides`，避免两处各持一份。

3. **统一 `maskAlpha`**：`DocumentPageScaffold` 默认传 `rememberTopBarMaterialAlpha(scrollBehavior)`；其余自带 Scaffold 的页面同理。这是无材质档观感统一的最低成本改动。

4. **设置页搜索框改 10dp**（或明确注释「搜索框特意比顶栏早出现，因为它在标题区下方」——二者选一，别留模糊地带）。

5. `WebImportScreen` 的 `showShadow=false` 加注释说明是刻意，或按需恢复。

6. 平板：确认「课程管理栏滚动零反馈」是否刻意（若否，补与另两栏一致的按钮/遮罩）；`rememberPaneMaskAlpha` 初值改为「取当前判定」。

---

## 四·附：实施记录（收口 + 修 bug，**没有**改动遮罩的出现范围）

### 先对齐设计口径（这条是红线，别顺手"统一掉"）

| 档位 | 顶栏绘制 | 纯色渐变遮罩 |
|---|---|---|
| 玻璃（BEST / BALANCED） | 模糊带**常驻**，全站一致 | **不画** |
| 无材质（NONE） | 无模糊 | 今日 / 课表**淡入**；其余全部页面**常驻** |

两者是互斥的替代关系，不是叠加：`useGradientMask: Boolean = LocalMaterialNoneEnabled.current`
（`ProgressiveBlurTopBar.kt:162`）为真时走 `ProgressiveBlurGradientMask`，为假时走 `drawBackdrop`
（真渐进 / 假渐进 / Legacy 三种模糊）。所以玻璃档下 `maskAlpha` 传什么都不生效 —— 遮罩根本不会被调用。

关于页是第三种历史口径：按 `scrollProgress >= 1f`（滚完整块 ~340dp logo）才淡入，**刻意保留**。

### 唯一来源

`miuix/.../layout/TopBarMaterial.kt`（新增，miuix 侧，因为 ui-shared 依赖 miuix 而非反向）：

| 导出 | 作用 |
|---|---|
| `TopBarMaterialThreshold = 10.dp` | 材质出现阈值，全站唯一 |
| `TopBarMaterialShowSpec` / `HideSpec` | 显 / 隐弹簧（0.6 / 0.4），全站唯一 |
| `TopBarMaterialState` | 一页一个：`alpha: () -> Float` 绘制期读，初值取当前判定 |
| `rememberTopBarMaterialState(scrollBehavior)` | 标准判据（滚过 10dp / 顶部下拉回弹） |
| `rememberTopBarMaterialState(visible / provider)` | 页面自有信号时用（关于页、设置搜索框） |

`CollapsibleTopAppBar` 新增 `materialState: TopBarMaterialState? = null`：按钮玻璃 / 阴影与
`ProgressiveBlurTopBar.maskAlpha` 读**同一个** alpha，不再各跑一套 `derivedStateOf + Animatable`。
不传时内部自算，旧调用点行为不变。

`ui-shared/.../components/TopBarMaterialAlpha.kt` **已删除**（负责转发，留着就是第二个入口）。

### 接线点

**判据（出现时机）全站统一到 `TopBarMaterialState`；遮罩只有三个页面接。**

| 位置 | 遮罩 `maskAlpha` | 按钮 `materialState` |
|---|---|---|
| 今日（`CourseScheduleChrome:393-410`） | ✅ 淡入 | ✅ |
| 课表（`ScheduleTopBar:169/188`） | ✅ 淡入 | ✅ |
| 关于（`AboutScreen:261`） | ✅ 淡入（判据也是 10dp） | ✅ |
| `DocumentPageScaffold`（21+ 路由） | ❌ 常驻默认 `1f` | ✅ |
| 首页设置顶栏 / 教务导入 / 课表切换 / 课程编辑 / 作息编辑 | ❌ 常驻 | ✅ |
| 课程详情 | ❌ 常驻 | ✅（`contentOffset` 同步保留，只把阈值换成常量） |
| 平板设置右栏 / 切换课表左栏 | ❌ 常驻（`TabletPaneTopChrome` 不传） | ✅（删本地 `rememberPaneMaskAlpha`，改用共享状态） |
| 平板课程管理栏 | ❌ 常驻 | 无按钮，`scrollBehavior = null` 保持原样 |
| `CapsuleSegmentedControl` | — | ✅（删自己的判据 + 两个 Animatable） |
| 设置页搜索框 | — | ✅（`listScrolled` 上报阈值从 **2px 改为 10dp**） |
| 院校选择 | — | 阈值改用常量（保留「塞 −阈值−1」同步写法） |
| 底栏弹窗（两张 sheet） | — | 弹簧改用共享 spec（阈值仍「任意滚动」，sheet 列表短） |
| `WebImportScreen` | **刻意例外**：顶栏是网页自己的色条，`blurAlpha = 0` + `showShadow = false`（已加注释，别照抄） |

### 判据里的一处真 bug：累计量被越界位移污染

`isTopBarMaterialVisible` 把 `contentOffset` 先 `coerceAtMost(0f)` 再比阈值。
回弹时 `OverScrollNode` 把 `available` 返给父级，`SharedScrollBehavior.onPostScroll` 照样累加，
顶部下拉一次就多出一截**正值**，之后正常下滑要先「还清」才够得到 10dp（表现为「滚了也不出材质」）；
反向污染则让回到顶部仍留负值（表现为「材质不消失」）。正数在真实滚动里不可能出现，
夹到 0 即可自愈 —— 这就是「有时候不出现 / 出现不消失」的机制级答案，
不用逐页抄底栏弹窗那套 `proxyConnection`。

### 首页「下拉回弹不出材质」的根因与修法

`CourseScheduleAppState.mainOverScrollState` 一份实例，由 `CourseScheduleAppMainContent`
在 `Scaffold` **外层** `LocalOverScrollState provides`；`TodayScreen` / `MainScheduleScreen`
改成接收注入（默认自建），不再各自 `remember { OverScrollState() }`。
顶栏槽与内容槽因此读到同一个对象，判据第二支活了。


| 关注点 | 位置 |
|---|---|
| 主判据 + 主动画 | `miuix/.../layout/CollapsibleTopAppBar.kt:401-445` |
| 遮罩判据（已删，唯一来源见上） | `miuix/.../layout/TopBarMaterial.kt` |
| 统一壳 | `ui-shared/.../components/DocumentPageScaffold.kt:117-226` |
| 首页共享顶栏槽 | `ui-shared/.../main/CourseScheduleAppMainContent.kt:487-720` |
| 首页两页顶栏 | `.../main/CourseScheduleChrome.kt:301-465`、`.../classschedule/ScheduleTopBar.kt:105-267` |
| 页面级 OverScroll 实例 | `.../today/TodayScreen.kt:530`、`.../classschedule/MainScheduleScreen.kt:882` |
| 搜索框 2px 口径 | `.../screens/SettingsScreen.kt:414-418` + `.../screens/SettingsSearch.kt:91-104` |
| 分段胶囊 | `.../components/CapsuleSegmentedControl.kt:100-121` |
| 平板三栏 | `.../screens/TabletSettingsScreen.kt:296-320`、`TabletSwitchSchedulePane.kt:160`、`TabletCourseManagePane.kt:321-344` |
| 手工注入 contentOffset | `.../screens/CourseDetailScreen.kt:255-280`、`.../screens/SchoolSelectionScreen.kt:100-126` |
| 关于页 scrollProgress | `.../activities/AboutScreen.kt:210-294` |
| 渲染层（合规，供对照） | `miuix/.../layout/ProgressiveBlurTopBar.kt`、`.../basic/LiquidTopBarButton.kt` |
