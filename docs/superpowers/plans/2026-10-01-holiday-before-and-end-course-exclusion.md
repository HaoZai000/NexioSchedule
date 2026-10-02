# 节假日首末课程排除实施计划

> 状态：功能实现与自动化验证完成；goal-verify 独立复审 PASS（1 项 MINOR：未实测 Android Cursor observer）。未提交或推送代码。

**Goal:** 保留假期末日选中课程恢复上课的原行为，新增假期前一天选中课程停课，并让保存、备份、显示、提醒与导出保持一致。

**Architecture:** 扩展现有 HolidayManager 与 HolidayCourseExclusion，不新增第二套日期解析系统。应上课程统一由 CourseReminderHelper 解析；主课表保留课程定义并单独灰显停课课程。两项设置全局独立，复用已有版本、修订信号与保存后的刷新入口。

**Tech Stack:** Kotlin、Jetpack Compose、SharedPreferences、现有 Android 提醒与组件体系、JUnit 4；不新增依赖。

## 约束与非目标

- 用户本轮明确要求先详细计划，不急着落地；本计划不代表已获实施授权。
- 不写死晚自习、固定节次、固定时刻或某个假期。
- 保留原末期数据键、匹配规则、范围选择和下方 UI，不重命名旧内部模型或迁移旧设置。
- 新设置默认关闭、起止为第 1–1 节；关闭开关保留范围，关闭时仍可预选范围。
- 两项设置沿用现有全局假期设置作用域，不改成逐课表或逐假期配置。
- 不修改课程定义，不拆分连堂，不扩大官方假期日期集合，不修改学期边界或教学周重组规则。
- 不做无关重构，不更新依赖、包名、Gradle、历史更新日志；保护已有未提交 tests、docs 和 Gradle 改动。
- 不读取或修改 local.properties；不启动、接管设备或通知进程来假装完成验证。
- 已有 MTZ authority/installed 列兼容问题与本功能分开处理，不擅自修复或重打包现有产物。

## 1. 界面与精确文案

位置不变：仍在「节假日与调休」页面原板块处。

```text
BEFORE
┌─────────────────────────────────────┐
│ 节假日末期课程排除               [!] │
│ ┌─────────────────────────────────┐ │
│ │ 原末期开关                 [○] │ │
│ ├─────────────────────────────────┤ │
│ │ 节次范围                  1–1 > │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘

AFTER
┌─────────────────────────────────────┐
│ 节假日首末课程排除               [!] │
│ ┌─────────────────────────────────┐ │
│ │ 开启假日前课程排除         [○] │ │
│ ├─────────────────────────────────┤ │
│ │ 节次范围                  1–1 > │ │
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ 开启假日末期课程排除       [○] │ │
│ ├─────────────────────────────────┤ │
│ │ 节次范围              原有范围 > │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

感叹号说明严格使用以下内容，不增加例子、不加末尾句号：

```text
• 假期前一天的某几节课可能不用上
• 假期最后一天的某几节课可能需要正常上课
• 可以将节假日最后一天或前一天的某几节课进行相应操作
```

新增卡片复用原尺寸、颜色、圆角、整行点击与节次选择器，不另设计一种样式。共享弹窗用编辑目标区分前日/末期，打开时加载对应范围，取消不保存，确认只保存对应配置。上限来自当前课表上午、下午、晚上节数之和；零节次不可确认；开始超过结束时沿用自动校正。多课表切换后，不因节数变少而静默覆写原配置。

## 2. 规则与接口

### 日期规则

| 日期 | 假日前规则 | 原末期规则 | 应上课程 |
|---|---|---|---|
| 普通日期 | 不生效 | 不生效 | 原候选 |
| 非假期且次日是假期 | 开启且合法时生效 | 不生效 | 原候选减去前日命中课程 |
| 假期中间日期 | 不生效 | 不生效 | 空 |
| 假期最后一天 | 不生效 | 开启且合法时生效 | 仅原末期命中课程 |

假期边界按所有有效假期记录的日期并集判断，不逐条对开始日减一天；因此相邻、重叠、跨年记录不会在假期内部制造前日例外。次日仅是调休日不触发。单日假期前一天可停课，当天可末期返课。LocalDate.MAX 没有可表示的次日，前日规则不生效。

### 候选与匹配

1. 先按真实日期、教学周重组、有效周次与调休映射得到候选，再执行前日/末期过滤。
2. 普通课程与包含端点的起止节次有交集即整门命中；不拆分连堂。这与原末期相交规则一致。
3. 自定义时间课程与所选节次各自有效上课时间严格相交即命中；仅碰端点或落在课间不算命中。
4. 匹配上限使用当前课表有效节次数。超出部分不生效，不映射成其他节次、不重写保存值。
5. 无效课程时间、无效范围或不可用节次时间映射不误命中。
6. 前日过滤保持剩余课程顺序；课程全部排除时，isHolidayDate 仍为 false。

### 最小接口扩展

- 新增 `HolidayBeforeCourseExclusion(enabled: Boolean = false, startSection: Int = 1, endSection: Int = 1)`，验证要求与旧模型相同。
- 保留 `HolidayEndCourseExclusion`、旧 `matchesCourse` 和旧调用语义。抽取内部范围匹配以供两种模型使用，不整套复制时间解析。
- 为 `HolidayCourseExclusion.resolveDayCourses` 增加默认关闭的 `beforeExclusion` 参数；调整时检查 Kotlin 默认参数和尾随 lambda 的调用兼容性。
- 在 `HolidayDayCourseResolution`、`CourseReminderHelper.DayScheduleResolution` 追加默认 false 的 `isHolidayBeforeCourseExclusionActive`，不改变官方假期标志。
- Context 入口加载双配置；内部可测试入口接收双配置。公开 `resolveDaySchedule(context, ...)` 的消费者无需另加过滤。

## 3. 保存、备份与恢复

### 本地配置

保存在原 `holiday_settings`：

- 新键：`before_course_exclusion_enabled`、`before_course_exclusion_start_section`、`before_course_exclusion_end_section`。
- 保留旧 `end_course_exclusion_*` 键。
- 新增 `HolidayManager.loadBeforeCourseExclusion` / `saveBeforeCourseExclusion`，复用已有校验、单调递增 version、dataRevision 发布机制。
- 单独保存一项不覆盖另一项、年度假期记录或调休映射；重进页面、进程重启、切换课表后保持一致。
- 保存成功调用 `CourseReminderHelper.onHolidayDataChanged`；失败不展示已保存状态，也不刷新假数据。

### 全量备份

- 保留 `holiday_entries` 与 `holiday_end_course_exclusion` 原协议。
- 新增顶层 `holiday_before_course_exclusion`，子结构为 `schema_version: 1`、`enabled`、`startSection`、`endSection`。
- 扩展 `HolidayManager.BackupData`、export/decode/restore；沿用已有整数校验。
- 旧安装升级没有新键：默认关闭且保留末期配置和全部假期记录。
- 旧全量备份缺新字段：恢复新项为关闭、1–1；不能残留恢复前的启用状态。旧末期字段继续按原规则处理。
- 新字段显式 null、错误类型、未知 schema、小数、溢出、非法范围：在任何课表或假期偏好写入前拒绝整个恢复。
- 假期 entries、双配置、version 在一次 holiday_settings 编辑中写入，随后发布一次 revision；不声称跨多个 preferences 文件具备事务原子性。
- `CourseRepository.restoreAllPreferences` 的特殊备份键跳过列表加入新键，避免写进课表 preferences。
- 本地预览解析阶段也提前校验假期数据；repository 仍保留最终写前门禁。
- 本地全量备份和 WebDAV 共用原 repository 入口；不另造云备份格式或升级外层 version。
- 单课表备份、JSON 导出、分享仍不携带或覆盖全局假期配置。

## 4. 各显示面和提醒联动

### 主课表

- 不从周视图删除课程；前日命中课程沿用停课灰显，并在卡片右上角复用假期期间的「假」角标样式；该视觉标记不改变官方假期日期状态，也不改成“非本周”。
- 增加独立停课样式标志与按页/星期计算的课程 ID 集合；正确教学周、调休日、跨时段片段和自定义时间均共用实际日期规则。
- 同槽应优先以仍应上的本周课程作代表；停课课程仍保留在多课程指示和详情里。全部停课时保留灰课可查看。
- 不改变原拖拽、长按、编辑资格；不复用详情转场用的 hiddenCourseIds。
- 普通课表智能周末的旁路，仅在前日排除实际生效的日期改用剩余应上课程判断，避免扩大到无关行为变更。
- 推荐验收口径：开启智能周末时，周末所有课程均被排除视为无课，可隐藏列并参与自动跳周；关闭智能周末时保留列和灰课。
- 保留教学周重组未来日期保护、用户手动浏览周不被强行拉回等既有规则。

### 今日、明日与假期倒计时

- 今日页、日期分页、明日预告使用共享解析后课程；计数、第一门课、最晚结束时间同时改变。
- 前日全部排除是普通无课空态，不是假期空态。末期零命中仍是官方假期空态。
- 倒计时保留官方假期区间，lastClassEndAt 使用过滤后课程：部分取消取剩余最晚结束，全部取消回退到更早最后有效课日，无有效课程沿用官方开始日零点兜底。
- 覆盖精确候选、7/14 天周期、兼容逐日三条倒计时路径；保留此前假期末日返课候选。
- 不为排除规则逐日展开多年日期，不重写 CourseScheduleDateBounds 搜索，也不混淆空周期列表和 null。

### 提醒、进行中通知与勿扰

- 已通过共享入口的课前闹钟、补发、次日摘要、下一课程、组件刷新时刻和自动勿扰不再各自复制规则。
- 保存/恢复后复用 onHolidayDataChanged 和现有 400ms 合并及尾部补跑；重排未来提醒和勿扰闹钟。
- 检查并撤销已被排除的真实倒计时/课中通知及超级岛状态；精确取消对应真实切态/展开闹钟，避免旧广播复活通知。
- 对真实快照切态/展开入口增加有效课程回查，仅对本规则必要路径做防护；测试通知和测试闹钟必须保留。
- 关闭排除恢复未来提醒，沿用已有发送去重，不清空历史导致重复推送；不因保存额外发送次日摘要。
- 排除当前课程后重新对账勿扰，尊重用户手动接管与既有恢复保护。
- 所有提醒关闭时仍刷新桌面数据。跨日、重启、调时区沿用原调度入口。

### 桌面组件与 ContentProvider

- 四种原生组件和 Provider 已走共享日课解析；确认课程、数量、圆点、今日结束转明日、首课、空态均随剩余课程变化。
- 原生组件已有渲染签名缓存，保存后更新已安装组件即可，不无条件清空缓存。
- MTZ 数据查询已共享，但原生 AppWidget 广播不能当作 MTZ 刷新证据。检查 Provider 变更通知/订阅能力；若接入现有 URI 的 notifyChange 与 Cursor notification URI，仍必须实机确认消费者实际订阅，不保证通知即刻刷新。
- 保留 MTZ resume/分钟刷新兜底；本功能不新增轮询、不自动修改 XML 或重打包 MTZ。
- 验证目标 authority 与安装包一致，已存在的 MTZ 接口兼容问题单独报告，不混入本功能。

### 缓存一致性

- 新设置保存和恢复推进同一 holidayVersion/dataRevision。
- 主课表停课集合、普通周末判断、今日分页/明日缓存、Activity 默认页和星期范围，补齐真实日期、假期版本、当前课表、教学周规则、sectionTimes 和总节数依赖。
- 尤其验证只修改节次时间而不改课程时，自定义时间匹配也会重新计算；不依赖“改时间一定 bumpDataVersion”的假设。
- 继续预计算周视图数据，不在翻页时反复全量解析所有周。

### 日历导出

- `icsEffectiveDatesForCourse` 与 `buildExportIcs` 显式接入前日配置，使用导出课表自己的时间/节数及真实事件日期过滤。
- 命中前日排除不生成该次完整 VEVENT；末日原允许课程仍输出。
- 保持调休、教学周重组、UID 和课程定义不变；不把 ICS 周次枚举引入倒计时。

## 5. 文件地图

路径基于仓库根目录 `/Users/yulimfish/Documents/NexioSchedule`。以下行号为规划时定位，执行以符号为准。

| 文件 | 目标 |
|---|---|
| `app/src/main/java/com/haooz/chedule/data/HolidayCourseExclusion.kt:6–124` | 双规则模型、前日判定、共享匹配与返回标志 |
| `app/src/main/java/com/haooz/chedule/data/HolidayManager.kt:16–34,104–222` | 新配置存储、版本、全量备份 decode/restore |
| `app/src/main/java/com/haooz/chedule/data/CourseRepository.kt:2499–2609,2777–2783` | 新特殊键隔离、写前完整校验 |
| `app/src/main/java/com/haooz/chedule/ui/activities/LocalBackupScreen.kt:247–329` | 全量备份预览时提前校验 |
| `app/src/main/java/com/haooz/chedule/ui/activities/HolidaySettingsScreen.kt:139–159,611–760,1510–1604` | 文案、前日卡片、弹窗编辑目标、保存刷新 |
| `app/src/main/java/com/haooz/chedule/reminder/CourseReminderHelper.kt:959–1197,1674–1841,1977–2058` | 双配置接入、快照撤销/回查与精准取消真实闹钟 |
| `app/src/main/java/com/haooz/chedule/reminder/IslandExpandReceiver.kt:28–69` | 排除后的旧展开广播不得复活真实通知 |
| `app/src/main/java/com/haooz/chedule/ui/screens/MainScheduleScreen.kt:451–559,908–920` | 停课 ID、灰显传递、同槽/详情和周末判断 |
| `app/src/main/java/com/haooz/chedule/ui/components/DayColumn.kt:333–484` | 停课状态、代表课程选择和缓存依赖 |
| `app/src/main/java/com/haooz/chedule/ui/components/CourseCard.kt:73–106,156–190,621–622` | 独立停课灰显，不伪造假期/非本周标志 |
| `app/src/main/java/com/haooz/chedule/ui/screens/TodayScreen.kt:612–740` | 应上列表/明日/倒计时与时间配置缓存 |
| `app/src/main/java/com/haooz/chedule/viewmodel/SettingsViewModel.kt:36–79` | 前日生效时普通周末与跳周旁路 |
| `app/src/main/java/com/haooz/chedule/ui/activities/MainActivity.kt:1667–1733` | 默认页、顶栏星期范围的依赖一致性 |
| `app/src/main/java/com/haooz/chedule/ui/activities/BackupAndMigrationScreen.kt:1045–1167` | ICS 实际日期过滤 |
| `app/src/main/java/com/haooz/chedule/provider/TodayCoursesProvider.kt:43–161` | 消费结果验收，必要时补现有 URI 变更通知 |

原则上仅验证不修改：`HolidayCountdown.kt`、`CourseScheduleDateBounds.kt`、`WebDavManager.kt`、四种原生 widget、`WidgetUpdateCache.kt`、`ClassDndHelper.kt`、`AlarmReceiver.kt`、`CourseStartReceiver.kt`、MTZ XML。若测试证实接入缺口，再增加精确必要改动。

## 6. 执行任务与测试门禁

### Task 1 — 核心规则

- [ ] 扩展 `HolidayCourseExclusionTest.kt`：先写前日过滤失败测试，再实现最小规则。
- [ ] 覆盖四种开关组合、单日/连续/相邻/重叠/跨年、次日仅调休、MIN/MAX。
- [ ] 覆盖普通跨节整门取消、自定义时间严格重叠/课间/端点/非补零、无效时间、节数缩减、关闭保留范围。
- [ ] 确认末期原测试不变通过，前日删空仍非官方假期。

### Task 2 — 保存和备份

- [ ] 扩展 `HolidayBackupTest.kt`、`HolidayManagerTest.kt`，先测试独立保存、版本递增、升级默认值与双配置往返。
- [ ] 实现新键和 load/save/export/decode/restore。
- [ ] repository 特殊键跳过、本地解析校验接入；保留 WebDAV 共用链路。
- [ ] 测本地裸 map、WebDAV data 包装、旧字段缺失、新字段 null/坏数/坏类型/坏版本、拒绝时恢复回调不执行和旧数据不变。
- [ ] 验证单课表备份/分享不改变全局配置。

### Task 3 — 共享解析、提醒和桌面数据

- [ ] 扩展 `CourseReminderHelperDayResolutionTest.kt`、`CourseReminderHelperTeachingWeekTest.kt`、`CourseReminderHelperTimeTest.kt`：先映射后过滤、保持顺序、普通空态标志、最晚结束时间。
- [ ] Context/纯解析入口传递双配置，复用原刷新入口。
- [ ] 对账真实进行中状态与对应真实闹钟；补必要快照回查，测试通知绕过。
- [ ] 源码确认四种 widget、Provider、勿扰、课前/次日/补发共享结果；Android 系统行为留给设备验收，不用纯测试冒充。
- [ ] 检查 MTZ 刷新/通知链路，既有兼容问题单列。

### Task 4 — 设置页与课表显示

- [ ] 用户确认本计划 ASCII 后再改 UI；手机/平板共用设置页按精确文案新增同款卡片。
- [ ] 主课表新增独立灰显、同槽优先应上课程、保留停课详情；末期灰显和角标不回归。
- [ ] 普通周末前日生效时共享剩余课程判断；扩展 `SettingsViewModelWeekendDecisionTest.kt`，保留重组未来日期保护。
- [ ] 补缓存依赖，检查切课表、修改节次时间、跨日和用户手动浏览页。

### Task 5 — 倒计时和 ICS

- [ ] 在 `HolidayCountdownTest.kt`、`HolidayCountdownEndExceptionRegressionTest.kt` 增加三路径回归：只取消最后课、全取消回退、此前末日允许课、稀疏/远期候选、跨年、无课兜底。
- [ ] 用访问候选日期/调用次数断言防止多年逐日扫描，不只靠 timeout。
- [ ] 在 `SingleScheduleBackupCompatibilityTest.kt` 扩展 ICS 前日/调休/重组/自定义时间/跨年用例，再传入双配置。
- [ ] 回归 `CourseScheduleDateBoundsTest.kt`、`TeachingWeekBackupCompatibilityTest.kt`，不改候选边界或 JSON/分享协议。

### Task 6 — 集成验证与独立核查

- [ ] 先定向测试，再完整 Debug 单测与构建；记录真实失败，不为本任务修改用户已有 Gradle 工作区。
- [ ] 可用且明确授权的 Android 设备上验收下节清单；无设备/权限/系统支持则明确标为未验证。
- [ ] 实施后由全新上下文 goal-verify 只读检查需求、逻辑、边界、质量、测试有效性、实际运行证据；修复阻断项再交付。
- [ ] 报告变更与验证，不自动 git add/commit/push，不自动连接真实 WebDAV 或触发提醒试验。

## 7. 验证命令

在仓库根目录执行，先使用现有离线缓存；环境失败只报告实际阻塞，不擅自下载安装或重建环境。

```bash
./gradlew :app:testDebugUnitTest --offline --tests 'com.haooz.chedule.data.Holiday*' --tests 'com.haooz.chedule.reminder.CourseReminderHelper*' --tests 'com.haooz.chedule.viewmodel.SettingsViewModelWeekendDecisionTest' --tests 'com.haooz.chedule.ui.activities.SingleScheduleBackupCompatibilityTest' --tests 'com.haooz.chedule.data.CourseScheduleDateBoundsTest' --tests 'com.haooz.chedule.data.TeachingWeekBackupCompatibilityTest'
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline
```

## 8. 实机验收矩阵

- 两项开关独立四种组合；范围取消/确认；重进页/重启；旧末期配置不变。
- 假日前日仅部分课程取消/全部取消；周视图灰显可查，今日/明日实际列表一致；不误标成假期。
- 原末日仅允许所选课程，假期中间仍不上课，零命中保留假期空态。
- 普通课程、自定义时间、连堂、同槽多门、调休、教学周重组、跨年、不同课表节数/时间。
- 智能周末开关、自动跳周、手动浏览周；只改时间配置后立即重新匹配。
- 四种原生组件与 MTZ 今日/明日、数量、圆点、首课、空态、今日结束切明日；MTZ 即时/返回桌面/跨分钟分别观察。
- 旧课前闹钟取消且到点不响；真实倒计时/课中/岛通知撤销、旧切态广播不复活；测试通知不受影响。
- 排除当前课程后勿扰正确还原且不覆盖用户手动修改；关闭排除恢复未来提醒，不重复已发提醒。
- 连续快速开关/改范围最终状态一致；所有提醒关闭时组件仍更新；跨午夜、重启、时区变化。
- 本地全量恢复、WebDAV 序列化包装、旧备份、坏字段拒绝；真实 WebDAV 联测仅在另有授权和可用账户时执行。
- ICS 不输出前日被取消事件，保留末日允许事件；单课表备份/JSON/分享不覆盖全局设置。

## 本轮状态

已实现前日独立配置、共享日期/课程过滤、备份恢复、设置页、课表灰显与右上角「假」角标、今日/明日/倒计时、提醒刷新、Provider 通知及 ICS 过滤，并补充规则、备份、课表选择、角标、倒计时和 Provider 通知回归测试。

验证：`./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` 成功；26 个测试 suite、204 个测试，0 failures/errors；`git diff --check` 通过。此前 goal-verify 独立复审 PASS；Provider 单测验证通知 URI 与生产 dispatcher 配对，但未实例化 Android `ContentProvider`/`Cursor`。

未进行 Android 设备/桌面小组件/MTZ 实测、Cursor observer 实测、真实提醒广播与勿扰实测或真实 WebDAV 联测。此次角标更新包尚未重新安装：重建后 `adb devices -l` 未发现已连接设备。代码尚未提交或推送。
