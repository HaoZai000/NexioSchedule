/** 开机完成 / 应用更新 / 时区变化接收器 - 重新注册课程提醒闹钟 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 需要重新调度的场景：
        // - BOOT_COMPLETED       重启后 AlarmManager 所有闹钟全部丢失
        // - MY_PACKAGE_REPLACED  应用更新后系统会清掉本 app 的全部闹钟
        // - TIME_CHANGED         用户手动调时间：RTC_WAKEUP 闹钟按"绝对时间"已无意义
        // - TIMEZONE_CHANGED     换时区：同上，原"今天 HH:mm"不再准确
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // 任何上述情况都重置全部闹钟（含 widget refresh）。
                // 关闭分支也会在 startReminderService 内把 widget 刷新链挂上，
                // 覆盖"用小组件但不开提醒"的用户群体。
                CourseReminderHelper.startReminderService(context)
            }
        }
    }
}
