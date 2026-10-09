package com.haooz.chedule.data

/** Android 侧走真正的监视器锁 —— 与迁移前的 `@Synchronized` 逐字等价，行为零变化。 */
actual fun <T> synchronizedOn(lock: Any, block: () -> T): T = synchronized(lock) { block() }
