@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.ui.activities.CalendarSyncResult
import com.haooz.chedule.ui.activities.buildScheduleCalendarEvents
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.coroutines.resume

/**
 * 「同步到日历」的 ArkTS 桥。
 *
 * 系统日历（`calendarManager`）只有 ArkTS 能写，方向与文件选择桥一致：
 * Kotlin 把展开好的逐节课事件发过去，ArkTS 申请权限、写日历、回传结果。
 */
private object CalendarSyncBridge {

    private var hostRequest: ((Int, String) -> Unit)? = null

    private val pending = mutableMapOf<Int, (Boolean, String) -> Unit>()
    private var nextId = 1

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(Int, CPointer<ByteVar>?) -> Unit>> = fn.reinterpret()
        hostRequest = { id, payload ->
            memScoped { cFunction.invoke(id, payload.cstr.ptr) }
        }
    }

    fun request(payload: String, onResult: (Boolean, String) -> Unit) {
        val host = hostRequest
        if (host == null) {
            onResult(false, "{\"status\":\"failed\"}")
            return
        }
        mainScope.launch {
            val id = nextId++
            pending[id] = onResult
            try {
                host(id, payload)
            } catch (t: Throwable) {
                pending.remove(id)
                onResult(false, "{\"status\":\"failed\"}")
            }
        }
    }

    fun onResult(requestId: Int, ok: Boolean, payload: String) {
        pending.remove(requestId)?.invoke(ok, payload)
    }
}

/** C++ 在模块注册时把发起入口交过来（见 `napi_init.cpp`）。 */
@CName("NexioOnCalendarHostReady")
fun NexioOnCalendarHostReady(fn: COpaquePointer?) {
    CalendarSyncBridge.onHostReady(fn)
}

/** ArkTS 回传：`setCalendarResult(requestId, ok, payload)`。 */
@CName("NexioOnCalendarResult")
fun NexioOnCalendarResult(requestId: Int, ok: Boolean, payload: CPointer<ByteVar>?) {
    CalendarSyncBridge.onResult(requestId, ok, payload?.toKString() ?: "")
}

/**
 * 记住一个「点了就同步」的动作（对应 Android `rememberCalendarSyncAction`）。
 *
 * 事件展开在共享层做（[buildScheduleCalendarEvents]），与 ICS 导出同一份时间口径；
 * 权限申请与写日历交给 ArkTS。
 */
@Composable
actual fun rememberOhosCalendarSyncAction(): suspend (String) -> CalendarSyncResult =
    remember { { scheduleName -> syncToCalendar(scheduleName) } }

private suspend fun syncToCalendar(scheduleName: String): CalendarSyncResult {
    val events = buildScheduleCalendarEvents(scheduleName) ?: return CalendarSyncResult.Failed
    val repository = CourseRepository()
    // 提醒口径跟随「课程提醒 → 课前提醒」：关着就写静默事件
    val reminderMinutes = if (repository.getPreClassReminder()) {
        repository.getPreClassReminderMinutes().takeIf { it >= 0 }
    } else {
        null
    }

    val payload = buildJsonObject {
        put("reminderMinutes", reminderMinutes ?: -1)
        putJsonArray("events") {
            for (event in events) {
                addJsonObject {
                    put("uid", event.uid)
                    put("title", event.title)
                    put("description", event.description)
                    put("location", event.location)
                    put("start", event.start.toJson())
                    put("end", event.end.toJson())
                }
            }
        }
    }.toString()

    val (ok, result) = suspendCancellableCoroutine { cont ->
        CalendarSyncBridge.request(payload) { ok, result ->
            if (cont.isActive) cont.resume(ok to result)
        }
    }
    val obj = runCatching { parseJsonObject(result) }.getOrNull()
    val status = obj?.optString("status") ?: ""
    return when {
        ok && status == "ok" -> CalendarSyncResult.Success(obj?.optInt("count", events.size) ?: events.size)
        status == "denied" -> CalendarSyncResult.PermissionDenied
        status == "noCalendar" -> CalendarSyncResult.NoWritableCalendar
        else -> CalendarSyncResult.Failed
    }
}

/** `{"y":2026,"mo":11,"d":13,"h":8,"mi":0}`：拆成字段而不是 ISO 串，ArkTS 侧直接 `new Date(...)`。 */
private fun LocalDateTime.toJson() = buildJsonObject {
    put("y", year)
    put("mo", monthNumber)
    put("d", dayOfMonth)
    put("h", hour)
    put("mi", minute)
}
