# 鸿蒙端（HarmonyOS）移植开工笔记

> 2026-10-31 起。目标平台 HarmonyOS 6 / API 26（DevEco Studio 26.0.0，本机 SDK：`/Applications/DevEco-Studio.app/Contents/sdk`）。
> 工程目录：`harmonyApp/`，模块 `entry`，bundleName `com.haooz.chedule`（与 Android 同一 package，方便商店与分享码识别）。

---

## 一、技术路线：ArkTS + ArkUI 原生重写

只有这一条能走通，另外两条都试过纸面推演后否掉：

| 方案 | 结论 | 原因 |
|---|---|---|
| KMP 编译到 OHOS 目标 | ❌ | Kotlin/Native 没有官方 OHOS target，社区 target 早已停更；只够跑非 UI 逻辑，且要自己搭 NAPI 桥 |
| Compose Multiplatform 渲染 | ❌ | CMP 无 OHOS 后端；转 wasm 塞进 ArkTS WebView 拿不到原生手势/性能，应用市场对纯 WebView 壳有审核风险 |
| ArkTS + ArkUI 原生 | ✅ | 声明式模型与 Compose 高度同构（`@Component`/`@Builder`/状态驱动），`relationalStore`、`preferences`、`backgroundBlurStyle` 都是一等公民 |

**复用策略：复用「数据契约」，不复用代码。** 这是整个移植的地基 —— 只要下面四份契约逐字对齐，鸿蒙端就能读 Android 的备份、把数据还回去：

1. **备份 JSON**（`core/data/backup/BackupCodec.kt`）：`schedule_name` / `courses[]` / `settings{}` / `time_config`；
2. **课程字段名**（`Course.kt`）：`day_of_week`、`start_section`、`week_type`、`selected_weeks`… 少一个字段就是静默丢数据（Android 端已经踩过「四处副本」的坑，鸿蒙端是第五处）；
3. **17 个 prefs 文件名**与 `pref(ns,k,type,...)` 表结构；
4. **算法口径**：周次以周一为首、节次时间按「上午→下午→晚上」拼接、同槽位选课规则。

---

## 二、今晚已经落地的（可编译、可跑）

```
harmonyApp/entry/src/main/ets/
├─ entryability/EntryAbility.ets   沉浸式 + 安全区换算 + 深浅色同步 AppStorage
├─ pages/Index.ets                 页面状态、派生数据（星期列/课程 lanes）、顶栏底栏、详情弹层
├─ model/Course.ets                课程模型 + 周次判定 + 同槽位选课 + 备注截断
├─ model/TimeConfig.ets            作息时间：节次骨架 → 各节起止时间（含长休息）
├─ model/Week.ets                  教学周日历（周一为首）
├─ data/ScheduleStore.ets          preferences 存整份 JSON，首启播种示例课表
├─ data/DemoData.ets               示例课表（起始日跟着今天走，恒落在第 9 周）
└─ ui/theme/Tokens.ets             色板/网格尺寸/主题/课程色派生（卡片底色与文字色）
   ui/components/CourseCardTile.ets 课程卡片
   ui/components/WeekGrid.ets       周网格（节次列 + 日列 + 午休/晚休分隔线）
   ui/components/CardMetrics.ets    卡片圆角/间隙（未来外观页的唯一来源）
```

已实现的交互：翻周（‹ ›）、回到本周（点标题或「本」）、点卡片看详情、本周不上的课降透明度、周末无课自动收成 5 列、深浅色跟随系统、状态栏图标跟随。

**产物**：`entry/build/default/outputs/default/entry-default-unsigned.hap`（未签名，只是编译门禁，装机需在 DevEco 里配自动签名）。

---

## 三、必须守住的移植口径（写错就是静默坏）

- **周次不是数据，是页面状态。** 翻周只改 `viewWeek`，不写库；「本周」按 `classStart` 现算，不信上次落盘的值。
- **本周不上的课要画**（`opacity 0.42`），不能过滤掉 —— 用户会以为课被删了。判据用 `nextActiveWeekAfter`，不能用 `startWeek`（"第 1 周开始、第 10 周还有一次"会被判出局）。
- **节次时间三段拼接**：上午 1..m、下午 m+1..m+a、晚上再往后。`sectionTimes` 覆盖表在 `quickTimeEnabled=false` 时才生效，缺项回退推算值。
- **卡片文字色由课程色派生**（同色系加深/提亮），不写死黑白色 —— 否则紫卡上白字看不见。
- **备注截断不能切半个代理对**（emoji 变豆腐块）。
- **空课名统一显示「特殊课程」。**
- ArkUI 的坑：`@Builder` 参数是按值传递，**函数类型参数在重建后可能不更新**，所以按钮统一传「动作名」，在 `handleAction` 里分发；卡片定位用「外层撑满格子 + padding 留缝」，不用 `calc()` 算宽度（列宽是等分值，窄屏容易算出 0）。

---

## 四、还没做（建议顺序）

1. **存储换成 `relationalStore`**，表结构照抄 `core/src/commonMain/sqldelight/.../Schedule.sq`（`meta`/`pref`/`folder`/`time_config`/`schedule`/`course`/`course_week`/`course_week_note`）。`pref` 表保留，键值层继续走它。
2. **备份 JSON 导入 / 导出**（第一步的验收物：把 Android 的备份导进来，课表一模一样）。
3. **课程增删改**：详情弹层 → 编辑页；要同步改 `course_week` 与 `course_week_note`（这也是为什么第 1 步必须先做）。
4. **翻周手势**：ArkUI 用 `PanGesture` 或 `Swiper`；注意「横向翻周 / 纵向滚动」的手势争夺，Android 端为此专门做了 `pagerAxisTakeoverGestureState`。
5. **外观页**：壁纸 + `backgroundBlurStyle` + `mixColor` 采样；鸿蒙的模糊是系统级 `BlurStyle`，不能像 Android 那样自带折射 shader，观感要重新调。
6. **提醒**：`reminderAgentManager` + `notificationManager`；桌面卡片用 ArkTS 卡（`FormExtensionAbility`），对应 Android 的 5 个 AppWidget。
7. **教务导入**：脚本执行模型不同（ArkTS 不能用 Rhino/JsEngine 直接跑用户脚本），要么搬成纯 JSON 规则，要么走沙箱 `Worker`。

---

## 五、构建

```bash
export NODE_HOME=/Applications/DevEco-Studio.app/Contents/tools/node
export DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk
export PATH="$NODE_HOME/bin:$PATH"
cd harmonyApp && /Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw assembleHap --no-daemon
```

### 本机踩过的两个坑（换机照做）

1. **hvigor 会先 `npm install pnpm`，而本机 npm 走不通代理**（ECONNRESET）。解法：手工把 pnpm 解到缓存目录，并补 `.bin` 入口 —— hvigor 只认 `~/.hvigor/wrapper/tools/<版本>/node_modules/.bin/pnpm` 这个文件存在：

```bash
V=10.28.2; D=~/.hvigor/wrapper/tools/$V
curl -sSL -o /tmp/pnpm.tgz https://registry.npmmirror.com/pnpm/-/pnpm-$V.tgz
mkdir -p "$D/node_modules/pnpm" "$D/node_modules/.bin"
tar -xzf /tmp/pnpm.tgz -C "$D/node_modules/pnpm" --strip-components=1
ln -sf ../pnpm/bin/pnpm.cjs "$D/node_modules/.bin/pnpm"
```

2. **`compatibleSdkVersion` 不能写 `"26.0.0(26)"`**，只认 `<major>[.<minor>][.<patch>]`，写 `"26.0.0"`。报错信息只说 "api version parameter is illegal"，不告诉你该写什么。

> 视觉验收目前只能在 DevEco Studio 里开 Previewer / 模拟器（本机没配自动签名，`hdc install` 装不上未签名 HAP）。命令行只保证「编译门禁」通过。
