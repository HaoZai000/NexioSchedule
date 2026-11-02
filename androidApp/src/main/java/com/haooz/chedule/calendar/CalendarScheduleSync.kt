package com.haooz.chedule.calendar

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.haooz.chedule.ui.activities.CalendarSyncResult
import com.haooz.chedule.ui.activities.ScheduleCalendarEvent
import com.haooz.chedule.ui.activities.buildScheduleCalendarEvents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import java.time.ZoneId

/**
 * 「同步到日历」：把课表的每一节课写进系统日历。
 *
 * 权限是运行时申请 —— 没有就现点现要，拒绝了返回 [CalendarSyncResult.PermissionDenied]，
 * 不做二次解释弹窗（用户再点一次系统还会问）。
 *
 * 只写进「可写且可见」的日历（优先主日历）；宁可报 [CalendarSyncResult.NoWritableCalendar]
 * 也不自建账户 —— 自建本地日历在多数国产 ROM 上会被日历应用隐藏。
 */
object CalendarScheduleSync {

    private val PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    fun hasPermission(context: Context): Boolean = missingPermissions(context).isEmpty()

    /** 还缺哪些权限；交给系统一次弹完。 */
    fun missingPermissions(context: Context): Array<String> =
        PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    /**
     * 事件生成留在调用线程做（[buildScheduleCalendarEvents] 要读课表库、空课表还会弹 toast），
     * 只有查日历和写事件这一段进 IO。
     */
    suspend fun sync(context: Context, scheduleName: String): CalendarSyncResult {
        if (!hasPermission(context)) return CalendarSyncResult.PermissionDenied
        val events = buildScheduleCalendarEvents(scheduleName) ?: return CalendarSyncResult.Failed
        return withContext(Dispatchers.IO) {
            val calendarId = writableCalendarId(context)
                ?: return@withContext CalendarSyncResult.NoWritableCalendar
            // 已存在的按「同一日历 + 同一开始时刻 + 同一标题」跳过，重复点不会堆重复日程
            val written = runCatching {
                for (event in events) insertIfAbsent(context, calendarId, event)
            }
            if (written.isSuccess) CalendarSyncResult.Success(events.size) else CalendarSyncResult.Failed
        }
    }

    private fun writableCalendarId(context: Context): Long? {
        val selection = "${CalendarContract.Calendars.VISIBLE}=1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val primary = CalendarContract.Calendars.IS_PRIMARY
        // IS_PRIMARY 在部分 ROM 上查不了，退化成只按 id 取第一个
        return runCatching {
            queryCalendarId(
                context,
                arrayOf(CalendarContract.Calendars._ID, primary),
                selection,
                args,
                "$primary DESC, ${CalendarContract.Calendars._ID} ASC",
            )
        }.getOrNull() ?: runCatching {
            queryCalendarId(
                context,
                arrayOf(CalendarContract.Calendars._ID),
                selection,
                args,
                "${CalendarContract.Calendars._ID} ASC",
            )
        }.getOrNull()
    }

    private fun queryCalendarId(
        context: Context,
        projection: Array<String>,
        selection: String,
        args: Array<String>,
        order: String,
    ): Long? = context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        selection,
        args,
        order,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun insertIfAbsent(context: Context, calendarId: Long, event: ScheduleCalendarEvent) {
        val startMillis = event.start.toEpochMillis()
        val exists = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID}=? AND " +
                "${CalendarContract.Events.DTSTART}=? AND ${CalendarContract.Events.TITLE}=?",
            arrayOf(calendarId.toString(), startMillis.toString(), event.title),
            null,
        )?.use { it.count > 0 } ?: false
        if (exists) return

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            if (event.location.isNotBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
            }
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, event.end.toEpochMillis())
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }
}

/** kotlinx-datetime 的 [LocalDateTime] → 本地时区的 epoch 毫秒（CalendarContract 只吃这个）。 */
private fun LocalDateTime.toEpochMillis(): Long =
    java.time.LocalDateTime.of(year, monthNumber, dayOfMonth, hour, minute, second)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

/**
 * 记住一个「点了就同步」的动作：有权限直接写，没权限先申请、拿到才写。
 *
 * 系统权限对话框是异步回调，用 [CompletableDeferred] 把它接回协程 —— 调用方的
 * `scope.launch { onSyncToCalendar(...) }` 因此能原地等结果，不用额外状态机。
 */
@Composable
fun rememberCalendarSyncAction(): suspend (String) -> CalendarSyncResult {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        pending.value?.complete(grants.values.all { it })
        pending.value = null
    }
    val action: suspend (String) -> CalendarSyncResult = remember(context) {
        action@{ scheduleName ->
            if (CalendarScheduleSync.hasPermission(context)) {
                return@action CalendarScheduleSync.sync(context, scheduleName)
            }
            val deferred = CompletableDeferred<Boolean>()
            pending.value = deferred
            launcher.launch(CalendarScheduleSync.missingPermissions(context))
            if (deferred.await()) {
                CalendarScheduleSync.sync(context, scheduleName)
            } else {
                CalendarSyncResult.PermissionDenied
            }
        }
    }
    return action
}
