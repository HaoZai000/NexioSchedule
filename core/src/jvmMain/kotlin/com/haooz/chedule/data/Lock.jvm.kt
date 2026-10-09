package com.haooz.chedule.data

/** JVM 侧同 Android：真锁。 */
actual fun <T> synchronizedOn(lock: Any, block: () -> T): T = synchronized(lock) { block() }
