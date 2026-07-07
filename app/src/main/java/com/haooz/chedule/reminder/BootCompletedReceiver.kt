/** 开机完成接收器 - 重新注册课程提醒闹钟 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 重启后 AlarmManager 所有闹钟丢失，重新调度（含 widget refresh）
            // 后续跨日检测会通过 widget refresh 自动触发
            CourseReminderHelper.startReminderService(context)
        }
    }
}
