package com.haooz.chedule.i18n

/** 课程提醒通知 / 原生实况 / 超级岛文案（非 Composable 上下文，按调用时刻语言取值） */
object AndroidNotifStr {

    // ---- 通知渠道（名称与描述在系统设置中展示） ----
    val channelReminder = L("课程提醒通知", "Course reminders")
    val channelLiveName = L("课程提醒实况", "Course live updates")
    val channelLiveDesc = L("课程提醒实时进度", "Live course progress")
    val channelIslandName = L("课程提醒超级岛", "Super Island")
    val channelIslandDesc = L("课程提醒超级岛通知", "Super Island notifications")

    // ---- 通知动作按钮 ----
    val viewSchedule = L("查看课表", "Schedule")
    val dndAction = L("上课勿扰", "Mute")

    // ---- 课前倒计时 ----
    val classInN = L("%d分钟后上课", "Class in %d min")
    val classInNAt = L("%d分钟后上课 · %s", "Class in %d min · %s")
    val minutesShort = L("%d分钟", "%d min")

    // ---- 实况 / 课中 ----
    val endsAt = L("%s下课", "Ends at %s")
    val infoWithRemaining = L("%s · 还剩%d分钟", "%s · %d min left")
    val infoRemaining = L("距下课 · 还剩%d分钟", "Class ends in %d min")
    val classStartedNow = L("现在上课", "Class started")
    val classStarted = L("已上课", "Class started")
    val inClassNow = L("正在上课", "In class")
    val untilClassEnds = L("距离下课", "Until class ends")
    val classEnded = L("已下课", "Class ended")

    // ---- 超级岛展开态 ----
    val startsIn = L("上课", "Starts in")
    val endsIn = L("下课", "Ends in")
    val upcomingClass = L("即将上课", "Starting soon")
    val now = L("现在", "Now")
    val location = L("地点", "Location")

    // ---- 测试通知的固定示例课程 ----
    val testCourseName = L("大学英语Ⅱ", "College English II")
    val testClassroom = L("博A201", "Bo A201")
    val testSection = L("第3-4节", "Periods 3-4")

    // ---- 次日提醒 / 假期提示 ----
    val holidayStartsGeneric = L("明天开始放假", "Break starts tomorrow")
    val holidayStartsWithName = L("明天起%s放假", "%s break starts tomorrow")
    val noClassTomorrow = L("明天不用上课", "No classes tomorrow")
    val holidayResumeN = L("共%d天，%d月%d日恢复上课", "%d days total, classes resume on %d/%d")
    val tomorrowClassesN = L("明天共%d节课", "%d classes tomorrow")
    val tomorrowNoClasses = L("明天没有课程安排", "No classes scheduled tomorrow")
    val earlyClassTomorrow = L("明早有早%s，%s", "Early class at %s tomorrow: %s")
    val morningClassTomorrow = L("明早有课，%s %s", "Morning class tomorrow: %s %s")
    val afternoonClass = L("下午有课，%s %s", "Afternoon class: %s %s")
    val eveningClass = L("晚上有课，%s %s", "Evening class: %s %s")
    val defaultCourseName = L("课程", "Class")

    val shizukuNotAuthorized = L(
        "Shizuku 未授权，超级岛通知可能无法正常显示",
        "Shizuku not authorized. Super Island notifications may not show properly"
    )

    // ---- 强制执行（逐项结果摘要） ----
    val forceRescheduledN = L("已重排闹钟（课前 %d 个，勿扰 %d 个）", "Alarms rescheduled (pre-class %d, DND %d)")
    val forceRescheduleFailed = L("闹钟重排失败", "Failed to reschedule alarms")
    val forceDndResult = L("课堂状态：%s", "Class state: %s")
    val forceNoActiveClass = L("当前不在课堂，未改动静音/勿扰", "Not in class; silent/DND untouched")
    val forceReminderOff = L("课程提醒未开启，已清理旧闹钟", "Reminders off; stale alarms cleared")

    // ---- 上课勿扰(通知/超级岛按钮的 Toast 反馈) ----
    val dndNeedReminderOn = L("请先在「课程提醒」中开启课程提醒", "Turn on course reminders in Course reminders first")
    val dndDisabled = L("已关闭上课勿扰", "Class mute turned off")
    val dndNeedPermission = L("请先在「课程提醒」中授予勿扰权限", "Grant Do Not Disturb permission in Course reminders first")
    val dndEnableFailed = L("开启失败，请检查勿扰权限", "Failed to enable. Check Do Not Disturb permission")
    val dndModeSilent = L("静音", "Silent")
    val dndModePriority = L("勿扰模式", "DND")
    val dndModeNormal = L("勿扰", "Mute")
    val dndTailInClass = L("，下课自动恢复", ", auto-restore after class")
    val dndTailManual = L("，再次点击可关闭", ", tap again to turn off")
    val dndEnabledN = L("已开启%s%s", "Enabled: %s%s")
}
