/** 超级岛通知助手 - 管理灵动岛/超级岛通知展示 */
package com.haooz.chedule.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import com.haooz.chedule.MainActivity
import com.haooz.chedule.R
import com.haooz.chedule.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

object IslandNotificationHelper {
    private const val TAG = "IslandNotificationHelper"
    private const val CHANNEL_ID = "course_reminder_island"
    private const val CHANNEL_NAME = "课程提醒超级岛"

    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        ShizukuManager.init(context)
    }

    fun isIslandSupported(context: Context): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod(
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(null, "persist.sys.feature.island", false) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return ShizukuManager.isShizukuRunning() && ShizukuManager.checkSelfPermission()
    }

    fun requestShizukuPermission(callback: (Boolean) -> Unit) {
        ShizukuManager.requestPermission(callback)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "课程提醒超级岛通知"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 构建超级岛通知参数 JSON
     */
    private fun buildIslandParamsJson(
        context: Context,
        title: String,
        content: String,
        subtitle: String? = null,
        courseName: String? = null,
        section: String? = null,
        startTime: String? = null,
        classroom: String? = null,
        minutesUntil: Int? = null,
        courseStartTimestamp: Long? = null
    ): String {
        val json = JSONObject()

        // param_v2 部分
        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("enableFloat", true)
            put("updatable", true)

            // 模板9：文本2 + 识别1 + 按钮2
            // 上半部分：文本组件2 baseInfo（type=2）
            val baseInfo = JSONObject().apply {
                put("type", 2) // 文本组件类型 2
                // 第一行：课程名称
                put("title", courseName ?: title)
                // 第二行：上课时间｜课程节次
                val contentText = buildString {
                    if (!startTime.isNullOrEmpty()) append(startTime)
                    if (!section.isNullOrEmpty()) {
                        if (isNotEmpty()) append("｜")
                        append(section)
                    }
                }
                put("content", contentText)
                // 其他字段留空
                put("subTitle", "")
                put("extraTitle", "")
                put("specialTitle", "")
                put("subContent", "")
                put("picFunction", "")
                // 分割线
                put("showDivider", true)
                put("showContentDivider", false)
                // 颜色
                put("colorTitle", "#111111")
                put("colorTitleDark", "#ffffff")
                put("colorContent", "#333333")
                put("colorContentDark", "#cccccc")
            }
            put("baseInfo", baseInfo)

            // 识别图形1：应用图标（不传pic自动隐藏）
            val picInfo = JSONObject().apply {
                put("type", 1)
                put("pic", "")
            }
            put("picInfo", picInfo)

            // 下半部分：按钮组件2 hintInfo（type=2）+ 动态倒计时
            val hintInfo = JSONObject().apply {
                put("type", 2) // 按钮组件类型 2
                // 前置文本1标签：倒计时进行中=即将上课，结束后=现在
                put("content", if (minutesUntil != null && minutesUntil > 0) "即将上课" else "现在")
                // 前置文本1：倒计时进行中为空，结束后显示"已上课"
                put("title", if (minutesUntil != null && minutesUntil > 0) "" else "已上课")
                // 动态倒计时 timerInfo
                val timerInfo = JSONObject().apply {
                    if (minutesUntil != null && minutesUntil > 0) {
                        put("timerType", -1) // -1 倒计时开始
                        val now = System.currentTimeMillis()
                        // 使用精确的课程开始时间戳
                        val courseStartTime = courseStartTimestamp ?: (now + (minutesUntil * 60 * 1000L))
                        put("timerWhen", courseStartTime)
                        put("timerTotal", 0L)
                        put("timerSystemCurrent", now)
                    } else {
                        put("timerType", 0) // 0 静态文本：已上课
                        put("timerWhen", 0)
                        put("timerTotal", 0)
                        put("timerSystemCurrent", 0)
                    }
                }
                put("timerInfo", timerInfo)
                // 前置文本2标签：地点
                put("subContent", "地点")
                // 前置文本2数值：教室名称
                put("subTitle", classroom ?: "")
                // 颜色
                put("colorContent", "#666666")
                put("colorContentDark", "#aaaaaa")
                put("colorTitle", "#222222")
                put("colorTitleDark", "#eeeeee")
                put("colorSubContent", "#666666")
                put("colorSubContentDark", "#aaaaaa")
                put("colorSubTitle", "#222222")
                put("colorSubTitleDark", "#eeeeee")
                // 可选：圆头操作按钮
                val actionInfo = JSONObject().apply {
                    put("actionTitle", "查看课表")
                    put("actionIntentType", 1) // url to activity
                    put("actionIntent", "intent:#Intent;component=${context.packageName}/.MainActivity;end")
                }
                put("actionInfo", actionInfo)
            }
            put("hintInfo", hintInfo)

            // 岛数据 - param_island
            val paramIsland = JSONObject().apply {
                put("islandProperty", 1) // 信息展示为主
                put("islandTimeout", 3600) // 1小时超时（大岛最大存活时长）

                // 大岛区域（模板2：A图文1 + B文本textInfo）
                val bigIsland = JSONObject().apply {
                    put("templateNo", 2) // 模板2：文本

                    // A区：课程名称（无图标）
                    val imageTextInfoLeft = JSONObject().apply {
                        put("type", 1)
                        val textInfo = JSONObject().apply {
                            put("title", courseName ?: title)
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        }
                        put("textInfo", textInfo)
                    }
                    put("imageTextInfoLeft", imageTextInfoLeft)

                    // B区：教室 或 已上课
                    val textInfo = JSONObject().apply {
                        put("frontTitle", "")
                        put("title", if (minutesUntil != null && minutesUntil > 0) {
                            classroom ?: ""
                        } else {
                            "已上课"
                        })
                        put("content", "")
                        put("showHighlightColor", false)
                        put("narrowFont", false)
                    }
                    put("textInfo", textInfo)
                }
                put("bigIslandArea", bigIsland)

                // 小岛区域
                val smallIslandArea = JSONObject().apply {
                    val picInfo = JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_small")
                        put("picDark", "miui.focus.pic_small_dark")
                    }
                    put("picInfo", picInfo)
                }
                put("smallIslandArea", smallIslandArea)
            }
            put("param_island", paramIsland)
        }

        json.put("param_v2", paramV2)
        return json.toString()
    }

    /**
     * 构建明日课程岛参数 JSON
     */
    private fun buildNextDayIslandParamsJson(
        title: String,
        content: String,
        courseCount: Int
    ): String {
        val json = JSONObject()

        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("enableFloat", true)

            // 焦点通知数据
            val baseInfo = JSONObject().apply {
                put("title", title)
                put("content", content)
                put("type", 2)
                put("colorTitle", "#FF6700")
            }
            put("baseInfo", baseInfo)

            // 岛数据
            val paramIsland = JSONObject().apply {
                put("islandProperty", 1)
                put("islandTimeout", 60) // 1分钟超时

                // 大岛区域
                val bigIslandArea = JSONObject().apply {
                    val imageTextInfoLeft = JSONObject().apply {
                        put("type", 1)
                        val picInfo = JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_tomorrow")
                        }
                        put("picInfo", picInfo)
                        val textInfo = JSONObject().apply {
                            put("frontTitle", "明日课程")
                            put("title", "${courseCount}节")
                            put("content", "点击查看")
                            put("useHighLight", true)
                        }
                        put("textInfo", textInfo)
                    }
                    put("imageTextInfoLeft", imageTextInfoLeft)
                }
                put("bigIslandArea", bigIslandArea)

                // 小岛区域
                val smallIslandArea = JSONObject().apply {
                    val picInfo = JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_small_tomorrow")
                    }
                    put("picInfo", picInfo)
                }
                put("smallIslandArea", smallIslandArea)
            }
            put("param_island", paramIsland)
        }

        json.put("param_v2", paramV2)
        return json.toString()
    }

    fun sendIslandNotification(
        context: Context,
        notificationId: Int,
        title: String,
        content: String,
        subtitle: String? = null,
        courseName: String? = null,
        section: String? = null,
        startTime: String? = null,
        classroom: String? = null,
        minutesUntil: Int? = null,
        courseStartTimestamp: Long? = null,
        useShizukuBypass: Boolean = true
    ) {
        if (!isIslandSupported(context)) return

        ensureChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 构建岛参数 JSON
        val islandParams = buildIslandParamsJson(
            context = context,
            title = title,
            content = content,
            subtitle = subtitle,
            courseName = courseName,
            section = section,
            startTime = startTime,
            classroom = classroom,
            minutesUntil = minutesUntil,
            courseStartTimestamp = courseStartTimestamp
        )

        // 添加图片 Bundle（大岛模板6：A图文1 + B等宽数字timer）
        val picsBundle = Bundle().apply {
            putParcelable("miui.focus.pic_app_icon", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_app_icon_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
        }
        builder.addExtras(Bundle().apply {
            putBundle("miui.focus.pics", picsBundle)
        })

        // 添加岛参数
        val notification = builder.build()
        notification.extras.putString("miui.focus.param", islandParams)

        if (useShizukuBypass && isShizukuAvailable()) {
            scope.launch {
                try {
                    val disabled = ShizukuManager.setXmsfNetworkingEnabled(context, false)
                    if (disabled) {
                        Log.d(TAG, "XMSF networking disabled, sending notification")
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(notificationId, notification)
                        delay(100)
                        ShizukuManager.setXmsfNetworkingEnabled(context, true)
                        Log.d(TAG, "XMSF networking restored")
                    } else {
                        Log.w(TAG, "Failed to disable XMSF networking, sending notification anyway")
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(notificationId, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send notification with Shizuku bypass", e)
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(notificationId, notification)
                }
            }
        } else if (useShizukuBypass && !isShizukuAvailable()) {
            // 超级岛开启但 Shizuku 不可用，提示用户
            Log.w(TAG, "Shizuku not available, showing toast and sending notification")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Shizuku 未授权，超级岛通知可能无法正常显示", android.widget.Toast.LENGTH_LONG).show()
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notification)
        } else {
            Log.d(TAG, "Shizuku bypass disabled, sending notification without bypass")
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notification)
        }
    }

    fun sendPreClassIslandNotification(
        context: Context,
        courseName: String,
        classroom: String,
        section: String,
        startTime: String,
        teacher: String,
        minutesUntil: Int? = null,
        notificationId: Int = 1001
    ) {
        val title = if (startTime.isNotEmpty()) "$courseName $startTime" else courseName
        val content = buildString {
            if (section.isNotEmpty()) append(section)
            if (classroom.isNotEmpty()) append("｜").append(classroom)
            if (teacher.isNotEmpty()) append("｜").append(teacher)
        }

        // 计算精确的课程开始时间戳
        val courseStartTimestamp = if (startTime.isNotEmpty()) {
            val parts = startTime.split(":")
            if (parts.size == 2) {
                val startHour = parts[0].toIntOrNull() ?: 0
                val startMinute = parts[1].toIntOrNull() ?: 0
                val courseTime = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, startHour)
                    set(java.util.Calendar.MINUTE, startMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                if (courseTime.timeInMillis <= System.currentTimeMillis()) {
                    courseTime.add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                courseTime.timeInMillis
            } else null
        } else null

        sendIslandNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            content = content,
            subtitle = "即将上课",
            courseName = courseName,
            section = section,
            startTime = startTime,
            classroom = classroom,
            minutesUntil = minutesUntil,
            courseStartTimestamp = courseStartTimestamp
        )
    }

    fun sendNextDayIslandNotification(
        context: Context,
        courses: List<com.haooz.chedule.data.Course>,
        repository: com.haooz.chedule.data.CourseRepository
    ) {
        if (courses.isEmpty()) {
            sendIslandNotification(
                context = context,
                notificationId = 1002,
                title = "明日无课",
                content = "明天没有课程安排"
            )
            return
        }

        val title = "明日课程（${courses.size}节）"
        val details = courses.joinToString("\n") { course ->
            val startTime = CourseReminderHelper.getCourseStartTime(course, repository)
            val endTime = CourseReminderHelper.getCourseEndTime(course, repository)
            val timeRange = if (startTime != null && endTime != null) {
                "$startTime-$endTime"
            } else ""
            buildString {
                if (timeRange.isNotEmpty()) append(timeRange).append(" ")
                append(course.name)
                if (course.classroom.isNotEmpty()) append(" @").append(course.classroom)
            }
        }

        if (!isIslandSupported(context)) return

        ensureChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1002,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(details.replace("\n", " "))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 构建明日课程岛参数
        val islandParams = buildNextDayIslandParamsJson(
            title = title,
            content = details,
            courseCount = courses.size
        )

        // 添加图片
        val picsBundle = Bundle().apply {
            putParcelable("miui.focus.pic_tomorrow", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small_tomorrow", Icon.createWithResource(context, R.mipmap.ic_launcher))
        }
        builder.addExtras(Bundle().apply {
            putBundle("miui.focus.pics", picsBundle)
        })

        val notification = builder.build()
        notification.extras.putString("miui.focus.param", islandParams)

        if (isShizukuAvailable()) {
            scope.launch {
                try {
                    val disabled = ShizukuManager.setXmsfNetworkingEnabled(context, false)
                    if (disabled) {
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(1002, notification)
                        delay(100)
                        ShizukuManager.setXmsfNetworkingEnabled(context, true)
                    } else {
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(1002, notification)
                    }
                } catch (e: Exception) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(1002, notification)
                }
            }
        } else {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(1002, notification)
        }
    }

    /**
     * 发送测试超级岛通知（实际课程）
     */
    fun sendTestIslandNotification(context: Context) {
        if (!isIslandSupported(context)) {
            Log.w(TAG, "Island not supported on this device")
            return
        }

        ensureChannel(context)

        // 获取下一节课
        val repository = com.haooz.chedule.data.CourseRepository(context)
        val nextCourse = CourseReminderHelper.findNextCourseToday(context)
        val startTime = nextCourse?.let { CourseReminderHelper.getCourseStartTime(it, repository) }
        val section = if (nextCourse != null) {
            "第${nextCourse.startSection}-${nextCourse.endSection}节"
        } else ""
        val classroom = nextCourse?.classroom ?: ""
        val courseName = nextCourse?.name ?: "课程"
        val teacher = nextCourse?.teacher ?: ""

        // 使用时间戳作为 Notification ID，避免缓存问题
        val testNotificationId = (System.currentTimeMillis() % 10000).toInt()

        // 计算精确的课程开始时间戳
        val courseStartTimestamp = if (startTime != null) {
            val parts = startTime.split(":")
            if (parts.size == 2) {
                val now = java.util.Calendar.getInstance()
                val startHour = parts[0].toIntOrNull() ?: 0
                val startMinute = parts[1].toIntOrNull() ?: 0
                
                // 设置课程开始时间
                val courseTime = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, startHour)
                    set(java.util.Calendar.MINUTE, startMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                
                // 如果课程时间已过，返回null表示已上课
                if (courseTime.timeInMillis <= now.timeInMillis) {
                    null
                } else {
                    courseTime.timeInMillis
                }
            } else null
        } else null
        
        // 计算剩余分钟数（用于显示）
        val minutesUntil = if (courseStartTimestamp != null) {
            val now = System.currentTimeMillis()
            val minutes = ((courseStartTimestamp - now) / (60 * 1000)).toInt()
            if (minutes > 0) minutes else 0  // 如果课程已过，返回0表示已上课
        } else 0

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            testNotificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构建标题和内容
        val title = if (startTime != null) "$courseName $startTime" else courseName
        val content = buildString {
            if (section.isNotEmpty()) append(section)
            if (classroom.isNotEmpty()) append("｜").append(classroom)
            if (teacher.isNotEmpty()) append("｜").append(teacher)
        }

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 构建岛参数
        val islandParams = buildIslandParamsJson(
            context = context,
            title = title,
            content = content,
            subtitle = "即将上课",
            courseName = courseName,
            section = section,
            startTime = startTime,
            classroom = classroom,
            minutesUntil = minutesUntil,
            courseStartTimestamp = courseStartTimestamp
        )

        // 添加图片
        val picsBundle = Bundle().apply {
            putParcelable("miui.focus.pic_app_icon", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_app_icon_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("miui.focus.pic_small_dark", Icon.createWithResource(context, R.mipmap.ic_launcher))
        }
        builder.addExtras(Bundle().apply {
            putBundle("miui.focus.pics", picsBundle)
        })

        val notification = builder.build()
        notification.extras.putString("miui.focus.param", islandParams)

        // 确保 Shizuku 可用后再发送
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Shizuku not available, requesting permission first")
            ShizukuManager.requestPermission { granted ->
                if (granted) {
                    Log.d(TAG, "Shizuku permission granted, sending notification")
                    scope.launch {
                        try {
                            val disabled = ShizukuManager.setXmsfNetworkingEnabled(context, false)
                            if (disabled) {
                                Log.d(TAG, "Sending test island notification with bypass")
                                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                manager.notify(testNotificationId, notification)
                                delay(100)
                                ShizukuManager.setXmsfNetworkingEnabled(context, true)
                            } else {
                                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                manager.notify(testNotificationId, notification)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send test notification", e)
                            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            manager.notify(testNotificationId, notification)
                        }
                    }
                } else {
                    Log.w(TAG, "Shizuku permission denied, sending without bypass")
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(testNotificationId, notification)
                }
            }
            return
        }

        // Shizuku 已可用，直接发送
        scope.launch {
            try {
                val disabled = ShizukuManager.setXmsfNetworkingEnabled(context, false)
                if (disabled) {
                    Log.d(TAG, "Sending test island notification with bypass")
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(testNotificationId, notification)
                    delay(100)
                    ShizukuManager.setXmsfNetworkingEnabled(context, true)
                } else {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(testNotificationId, notification)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send test notification", e)
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(testNotificationId, notification)
            }
        }

        // 测试模式：倒计时结束后触发展开态弹出
        if (minutesUntil != null && minutesUntil > 0) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val expandRunnable = Runnable {
                Log.d(TAG, "Test countdown finished, sending class started notification")
                sendClassStartedNotification(
                    context = context,
                    courseName = courseName,
                    classroom = classroom ?: "",
                    section = section ?: "",
                    startTime = startTime ?: "",
                    notificationId = testNotificationId
                )
            }
            handler.postDelayed(expandRunnable, minutesUntil * 60 * 1000L)
        }
    }

    /**
     * 发送"已上课"通知，触发展开态弹出
     * 通过 actionIntentType=2 的广播来主动弹出展开态
     */
    fun sendClassStartedNotification(
        context: Context,
        courseName: String,
        classroom: String,
        section: String,
        startTime: String,
        notificationId: Int = 1001
    ) {
        if (!isIslandSupported(context)) return

        // 直接调用 sendIslandNotification，传入 minutesUntil = 0
        // 系统会自动：
        // 1. 将展开态的 content 从"即将上课"变为"现在"
        // 2. 将 title 从""变为"已上课"
        // 3. 将 timerInfo 从倒计时模式变为静态文本模式
        // 4. 将大岛的 B 区从教室变为"已上课"
        
        val title = if (startTime.isNotEmpty()) "$courseName $startTime" else courseName
        val content = buildString {
            if (section.isNotEmpty()) append(section)
            if (classroom.isNotEmpty()) append("｜").append(classroom)
        }

        sendIslandNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            content = content,
            subtitle = "现在",
            courseName = courseName,
            section = section,
            startTime = startTime,
            classroom = classroom,
            minutesUntil = 0,  // 关键：传入 0 表示已上课
            useShizukuBypass = true
        )
        
        // 15秒后自动消除通知
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.cancel(notificationId)
            Log.d(TAG, "Notification cancelled after 15 seconds")
        }, 15_000L)
    }
}