package com.haooz.chedule.data

/**
 * Kotlin/Native 侧**暂时是直通**（没有互斥）。
 *
 * Native 没有 `synchronized`，也没有等价的 stdlib 原语。iOS 侧目前还没有
 * 「后台迁移 + 前台保存」这条并发路径，所以先直通并把它记成**已知缺口**
 * （见 [synchronizedOn] 的 KDoc）。真正需要时换成 `kotlinx.atomicfu` 的
 * `SynchronizedObject` 或把复合写操作收敛到单线程。
 */
@Suppress("UNUSED_PARAMETER")
actual fun <T> synchronizedOn(lock: Any, block: () -> T): T = block()
