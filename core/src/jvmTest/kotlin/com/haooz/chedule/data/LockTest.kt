package com.haooz.chedule.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [synchronizedOn] 的语义验证。
 *
 * ## 为什么必须有
 *
 * `HolidayManager` 原来用 `@Synchronized` 保护「读 prefs → 算 → 写 prefs」的复合操作
 * （真实场景：`NexioApplication.onCreate` 起后台 `Thread` 跑旧调休映射迁移，
 * 同时主线程可能正在保存节假日设置）。下沉 `:core` 时换成了 [synchronizedOn]。
 *
 * **Android 行为零变化是本项目的红线**，所以必须证明 JVM/Android 的 actual
 * 确实提供了「互斥 + 可重入」，而不是一个看起来像锁的空壳。
 * （Native 侧目前是直通，那是已知缺口，见 [synchronizedOn] 的 KDoc。）
 *
 * ⚠ 本文件不入库（用户长期要求）。
 */
class LockTest {

    @Test
    fun `同一把锁可重入`() {
        val lock = Any()
        val result = synchronizedOn(lock) {
            synchronizedOn(lock) {
                synchronizedOn(lock) { "ok" }
            }
        }
        assertEquals("ok", result)
    }

    @Test
    fun `多线程下复合操作不会丢更新`() {
        val lock = Any()
        var counter = 0
        val threads = 8
        val perThread = 2_000
        val start = CountDownLatch(1)
        val errors = AtomicInteger(0)

        val workers = (0 until threads).map {
            Thread {
                start.await()
                repeat(perThread) {
                    // 模拟「读 → 算 → 写」：没有锁时这里必然丢更新
                    synchronizedOn(lock) {
                        val current = counter
                        Thread.yield()
                        counter = current + 1
                    }
                }
            }.also { t -> t.setUncaughtExceptionHandler { _, _ -> errors.incrementAndGet() } }
        }
        workers.forEach(Thread::start)
        start.countDown()
        workers.forEach(Thread::join)

        assertEquals(0, errors.get())
        assertEquals(threads * perThread, counter, "有锁却丢了更新 → 锁没生效")
    }

    @Test
    fun `异常会穿透锁而不是被吞掉`() {
        val lock = Any()
        var reached = false
        val thrown = runCatching {
            synchronizedOn(lock) { error("boom") }
        }
        assertTrue(thrown.isFailure)
        // 锁必须已释放，否则下面这次会死锁
        synchronizedOn(lock) { reached = true }
        assertTrue(reached)
    }
}
