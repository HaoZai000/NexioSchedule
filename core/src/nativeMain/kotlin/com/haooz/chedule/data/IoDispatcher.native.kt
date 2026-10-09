package com.haooz.chedule.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Kotlin/Native（linuxX64 门禁目标；将来的 iosArm64 / iosSimulatorArm64 共用本文件）。
 *
 * Native 上 `Dispatchers.IO` 是 `internal`，改用 `Dispatchers.Default` ——
 * 它在 Native 背后是多线程工作池，不会阻塞主线程。
 */
internal actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.Default
