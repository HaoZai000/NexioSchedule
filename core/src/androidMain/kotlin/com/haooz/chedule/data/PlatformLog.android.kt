package com.haooz.chedule.data

import android.util.Log

/**
 * Android 实现：原样转发到 Logcat。
 *
 * 刻意**不**加任何过滤或级别映射 —— 迁移前 166 处调用看到的就是 Logcat，
 * 现在必须看到一模一样的级别，否则线上排查会失效（尤其是 `Log.e` 的堆栈）。
 */
actual fun platformLog(level: Int, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        LOG_LEVEL_VERBOSE -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
        LOG_LEVEL_DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
        LOG_LEVEL_INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
        LOG_LEVEL_WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        else -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
