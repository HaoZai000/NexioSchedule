package com.haooz.chedule.calendar

import android.Manifest
import android.content.ContentUris
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
import com.haooz.chedule.data.repo.CourseRepository
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
 * 也不自建账户 —— 自建本地账户在多数国产 ROM 上会被日历应用隐藏。
 *
 * 提醒口径跟随「课程提醒 → 课前提醒」：开着的按同一提前量写 `Reminders`，
 * 关着就写静默事件（`HAS_ALARM = 0`），两边不会一个响一个不响。
 *
 * **每次同步是覆盖，不是追加**：先按 `CUSTOM_APP_PACKAGE` 标记清掉本包写过的全部事件，再全量重写。
 * 课表里删掉的课、改过的时间、改过的课名都不会留残影；代价是用户在日历里给这些事件手加的
 * 备注/自定义项会一起没掉（这正是「以课表为准」该有的语义）。
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
            val reminderMinutes = reminderLeadMinutes()
            val owner = context.packageName
            // 整批清掉本包写过的旧事件：课表里删掉的课、改过的时间都不该在日历里留残影。
            // 这一步失败不拦着 —— 下面按签名删还能兜住目标日历里的重复。
            runCatching {
                context.contentResolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ?",
                    arrayOf(owner),
                )
            }
            val written = runCatching {
                for (event in events) writeEvent(context, calendarId, owner, event, reminderMinutes)
            }
            if (written.isSuccess) CalendarSyncResult.Success(events.size) else CalendarSyncResult.Failed
        }
    }

    /**
     * 日历事件的提醒提前量，与「课程提醒 → 课前提醒」同一口径。
     *
     * 课前提醒关着就返回 null（写静默事件）—— 用户关掉的是「提醒」这件事本身，
     * 不该只因为写进了日历就又开始响。
     */
    private fun reminderLeadMinutes(): Int? {
        val repository = CourseRepository()
        if (!repository.getPreClassReminder()) return null
        return repository.getPreClassReminderMinutes().takeIf { it >= 0 }
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

    /**
     * 写一节课：先删掉「同一日历 + 同一开始时刻 + 同一标题」的旧行，再插新事件。
     *
     * 旧行理论上已被整批清理过了，这里的删是兜住**升级前写下的无标记事件**（那时还没有
     * `CUSTOM_APP_PACKAGE`），否则老用户重同步会看到两份。
     */
    private fun writeEvent(
        context: Context,
        calendarId: Long,
        owner: String,
        event: ScheduleCalendarEvent,
        reminderMinutes: Int?,
    ) {
        val startMillis = event.start.toEpochMillis()
        runCatching {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.CALENDAR_ID}=? AND " +
                    "${CalendarContract.Events.DTSTART}=? AND ${CalendarContract.Events.TITLE}=?",
                arrayOf(calendarId.toString(), startMillis.toString(), event.title),
            )
        }

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
            put(CalendarContract.Events.HAS_ALARM, if (reminderMinutes != null) 1 else 0)
            // 归属标记：下次同步靠它整批清掉，也方便用户在日历里看出这些事件是哪个应用写的
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, owner)
            put(CalendarContract.Events.CUSTOM_APP_URI, "nexio://class/${event.uid}")
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() } ?: return
        ensureReminder(context, eventId, reminderMinutes)
    }

    /**
     * 给事件补一条提醒；同一分钟已有就不重复写。
     *
     * 正常流程里事件都是新插的、提醒表必然是空的，这层判重是兜底（rowid 复用等边角）。
     * 单独 try 住：部分日历（只读订阅源、企业日历）拒写 `Reminders`，
     * 不能因为一条提醒失败就把整次同步判成 [CalendarSyncResult.Failed]。
     */
    private fun ensureReminder(context: Context, eventId: Long, minutes: Int?) {
        if (minutes == null) return
        runCatching {
            val exists = context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders._ID),
                "${CalendarContract.Reminders.EVENT_ID}=? AND ${CalendarContract.Reminders.MINUTES}=?",
                arrayOf(eventId.toString(), minutes.toString()),
                null,
            )?.use { it.count > 0 } ?: false
            if (exists) return@runCatching

            val inserted = context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, minutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                },
            ) ?: return@runCatching

            // 事件行上的 HAS_ALARM 不跟着置 1，部分日历应用不显示这条提醒
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                ContentValues().apply { put(CalendarContract.Events.HAS_ALARM, 1) },
                null,
                null,
            )
        }
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
