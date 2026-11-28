package com.haooz.chedule.data

import com.haooz.chedule.data.util.suspendRunCatching
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

class SuspendRunCatchingTest {
    @Test
    fun `挂起操作超时不包装成失败结果`() = runTest {
        assertFailsWith<TimeoutCancellationException> {
            suspendRunCatching { withTimeout(100L) { awaitCancellation() } }
        }
    }
}
