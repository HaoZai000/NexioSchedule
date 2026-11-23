package com.haooz.chedule.ui.activities.today

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.util.Calendar
import java.util.TimeZone

@Composable
actual fun rememberSystemCalendarController(
    date: LocalDate,
    refreshKey: Int,
): SystemCalendarController {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var granted by remember {
        mutableStateOf(appContext.hasCalendarReadPermission())
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var snapshot by remember {
        mutableStateOf(
            SystemCalendarSnapshot(
                permission = if (granted) {
                    SystemCalendarPermission.GRANTED
                } else {
                    SystemCalendarPermission.DENIED
                },
                isLoading = granted,
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        reloadTick++
    }

    LaunchedEffect(date, refreshKey, reloadTick, granted) {
        val currentGrant = appContext.hasCalendarReadPermission()
        if (granted != currentGrant) granted = currentGrant
        if (!currentGrant) {
            snapshot = SystemCalendarSnapshot(
                permission = SystemCalendarPermission.DENIED,
            )
            return@LaunchedEffect
        }
        snapshot = snapshot.copy(
            permission = SystemCalendarPermission.GRANTED,
            isLoading = true,
            loadFailed = false,
        )
        val result = withContext(Dispatchers.IO) {
            runCatching { querySystemCalendar(appContext, date) }
        }
        snapshot = SystemCalendarSnapshot(
            events = result.getOrDefault(emptyList()),
            permission = if (granted) {
                SystemCalendarPermission.GRANTED
            } else {
                SystemCalendarPermission.DENIED
            },
            isLoading = false,
            loadFailed = result.isFailure,
        )
    }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reloadTick++
            }
        }
        appContext.contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,
            observer,
        )
        onDispose {
            appContext.contentResolver.unregisterContentObserver(observer)
        }
    }

    return SystemCalendarController(
        snapshot = snapshot,
        requestPermission = request@{
            if (appContext.hasCalendarReadPermission()) {
                granted = true
                reloadTick++
                return@request
            }
            val activity = context.findActivity()
            val shouldOpenSettings = permissionRequested &&
                activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) == false
            if (shouldOpenSettings) {
                runCatching {
                    activity?.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${appContext.packageName}"),
                        )
                    )
                }
            } else {
                permissionRequested = true
                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        },
    )
}

private fun Context.hasCalendarReadPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@SuppressLint("MissingPermission")
private fun querySystemCalendar(
    context: Context,
    date: LocalDate,
): List<SystemCalendarEvent> {
    val zone = TimeZone.getDefault()
    val dayStart = Calendar.getInstance(zone).apply {
        clear()
        set(date.year, date.monthNumber - 1, date.dayOfMonth, 0, 0, 0)
    }
    val startMillis = dayStart.timeInMillis
    val endMillis = (dayStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Instances.DISPLAY_COLOR,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.STATUS,
    )
    val events = mutableListOf<SystemCalendarEvent>()
    val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
        ContentUris.appendId(this, startMillis)
        ContentUris.appendId(this, endMillis)
    }.build()
    context.contentResolver.query(
        instancesUri,
        projection,
        null,
        null,
        "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
        val titleColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
        val locationColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
        val calendarColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
        val colorColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)
        val beginColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
        val endColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
        val allDayColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
        val statusColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.STATUS)

        while (cursor.moveToNext()) {
            if (cursor.getInt(statusColumn) == CalendarContract.Events.STATUS_CANCELED) continue
            val begin = cursor.getLong(beginColumn)
            val end = cursor.getLong(endColumn)
            if (end <= startMillis || begin >= endMillis || end <= begin) continue
            val allDay = cursor.getInt(allDayColumn) != 0
            val startMinute = if (allDay || begin <= startMillis) {
                0
            } else {
                minuteOfDay(begin, zone)
            }
            val endMinute = if (allDay || end >= endMillis) {
                MINUTES_PER_DAY
            } else {
                minuteOfDay(end, zone).let { if (it == 0) MINUTES_PER_DAY else it }
            }
            val eventId = cursor.getLong(idColumn)
            events += SystemCalendarEvent(
                id = "$eventId:$begin",
                title = cursor.getString(titleColumn)?.trim().orEmpty(),
                location = cursor.getString(locationColumn)?.trim().orEmpty(),
                calendarName = cursor.getString(calendarColumn)?.trim().orEmpty(),
                startMinute = startMinute,
                endMinute = endMinute,
                color = cursor.getLong(colorColumn).toLong() and 0xffffffffL,
                allDay = allDay,
            )
        }
    }
    return events.distinctBy { it.id }
}

private fun minuteOfDay(epochMillis: Long, zone: TimeZone): Int {
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = epochMillis }
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

private const val MINUTES_PER_DAY = 24 * 60
