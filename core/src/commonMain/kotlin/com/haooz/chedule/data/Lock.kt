package com.haooz.chedule.data

/**
 * 跨平台互斥执行。
 *
 * ## 为什么需要它
 *
 * `HolidayManager` 原实现用 **`@Synchronized`**（等价 `synchronized(this)`）保护
 * 「读 prefs → 算 → 写 prefs」的复合操作 —— 典型场景是启动时后台线程跑
 * `migrateLegacyFollowDates`，同时主线程可能在保存节假日设置。丢掉这把锁
 * 会出现「迁移结果覆盖用户刚保存的编辑」这类**编译全绿、只有并发时才会发生**的问题。
 *
 * 但 **Kotlin/Native 没有 `synchronized`**（实测：`Unresolved reference`），
 * `java.lang.Object` 的监视器也不存在。所以必须 expect/actual。
 *
 * ## 各平台实现（⚠ 差异是有意的，别改成「统一」）
 *
 * | 平台 | 实现 | 效果 |
 * |---|---|---|
 * | Android / JVM | `synchronized(lock)` | **与原来的 `@Synchronized` 逐字等价** —— Android 行为零变化（红线） |
 * | Kotlin/Native | 直通调用 | **暂时没有互斥**。Native 上没有可用原语，且 iOS 侧目前还没有并发写这条路径 |
 *
 * > ⚠ **iOS 接进来之前必须回看这里**：如果 iOS 侧也会「后台迁移 + 前台保存」并发，
 * > 需要给 nativeMain 换成真正的实现（例如引入 `kotlinx.atomicfu` 的 `SynchronizedObject`，
 * > 或把复合写操作收敛到单线程/单协程）。当前 nativeMain 的直通实现是**已知缺口**，
 * > 不是「等价实现」。
 *
 * 刻意用 **非 inline**：inline 的 expect/actual 跨模块有额外限制，而这里每处调用
 * 只在保存时发生一次，一次 lambda 分配可以忽略。
 */
expect fun <T> synchronizedOn(lock: Any, block: () -> T): T
