package com.haooz.chedule.data

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 执行阻塞式 IO 的调度器。
 *
 * ## 为什么不能直接用 `Dispatchers.IO`
 *
 * `Dispatchers.IO` **不是跨平台 API**：
 * - JVM / Android：`public val Dispatchers.IO`
 * - Kotlin/Native（含 iOS）：**`internal`** —— 直接引用报
 *   `Cannot access 'val IO: CoroutineDispatcher': it is internal in 'kotlinx.coroutines.Dispatchers'`
 *
 * 这是 `:core` 加 `linuxX64` 编译门禁后立刻暴露的：此前只有 android + jvm 两个 JVM 目标，
 * commonMain 从没被平台中立的 stdlib 检查过，所以 3 个文件里的 5 处 `Dispatchers.IO`
 * 一路绿灯 —— 一旦真正编 iOS 就会全部失败。
 *
 * ## Native 侧用 `Dispatchers.Default` 的理由
 *
 * Kotlin/Native 的 `Dispatchers.Default` 背后是**多线程工作池**（不是单线程），
 * 把网络/JSON 这类阻塞操作放进去不会卡住主线程，语义上与 JVM 的 IO 池足够接近。
 * Native 平台本身不提供独立的 IO 池（coroutines 1.9.0 未公开）。
 */
internal expect val ioDispatcher: CoroutineDispatcher
