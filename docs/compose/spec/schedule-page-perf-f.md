---
feature: schedule-page-perf-f
status: in-progress
updated: 2026-03-12
branch: schedule-perf-f
---

# 课程表性能 F/G/E + 假期/调休相关 bug 修复

## Report

## [S1] Problem

1. **F**：`filteredCoursesCache` 构建时对调休日做 `holidayEntries.firstOrNull` 线性扫；同日假期+调休挤在同一个 `holidayIndex` 互相覆盖；`HolidayManager.getVersion` 每次重组读 SP。
2. **Bug**：关闭「显示非本周课程」后，调休日仍显示被调星期的完整课表（含非本周）。
3. **Bug**：假期设置返回后课表网格立刻刷新，但标题星期行要切周再回来才更新。
4. **G**：`getAllCourses` 冷路径全量 `preWarmOccupiedWeeksCache`（7×节次对×课程），实际只有添加/编辑选周用到。
5. **E**：MainActivity 主内容 `.liquidGlassLayerBackdrop(liquidGlassBackdrop)` 无 recordKey，空闲时每帧全树 `recordLayer`。

约束：调休/假期显示与改前一致（除已修 bug）；假期编辑返回后标题与网格同步刷新；玻璃采样不得出现旧帧闪烁。

## [S2] Design

### F
1. `holidayVersion`：`remember(scheduleContext, dataVersion)` 记忆化。
2. 拆 `holidayIndex` / `workswapIndex`（共用 `expandEntryByDate`）。
3. 调休查找、智能周末活跃、`isWorkSwap` 均 O(1) 查 `workswapIndex`。
4. `filteredCoursesCache` key 增加 `workswapIndex`。

### Bug 修复
1. 调休日过滤改为始终跟随 `showNonCurrentWeek`，去掉 `swapConfigured` 绕过。
2. `MainActivity.dayRange` remember key 增加 `dataVersion`。

### G
1. 删除 `getAllCourses` 中 `preWarmOccupiedWeeksCache`；`getOccupiedWeeks` 按需算+缓存。

### E
1. `layerBackdrop` 增加 `mustRecord: (() -> Boolean)?`：draw 阶段强制录制谓词，按引用 equals。
2. 主内容 `recordKey`：结构指纹（tab/弹窗/数据版本/壁纸/搭配等），稳定 List。
3. 主内容 `mustRecord`：滚动（课表/今日/周次 pager/今日 pager/设置 scrollY 帧差）、开洞、切换课表、快捷/管理模糊、cutout/cover 动画、拖拽卡片。空闲且 key 未变时跳过 `recordLayer`。

## [S3] Out of Scope

- 方案 D（三 Tab 布局降级）
- 课程页 state 分片
- 相邻周 page 轻量预取

## Tasks

- [x] T1: holidayVersion 记忆化 (covers: S2-F1)
- [x] T2: holiday/workswap 双索引 + O(1) 查表 (covers: S2-F2,3,4)
- [x] T3: 调休日非本周过滤修复 (covers: S2-Bug1)
- [x] T4: 标题星期行假期刷新 (covers: S2-Bug2)
- [x] T5: occupiedWeeks 懒预热 (covers: S2-G1)
- [x] T6: liquidGlass recordKey + mustRecord 跳帧 (covers: S2-E1,2,3)
- [x] T7: 编译通过 — acceptance: `gradlew :app:compileDebugKotlin` 成功 (covers: S2)
