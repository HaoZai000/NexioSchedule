/** 开机完成 / 应用更新 / 时区变化接收器 - 重新注册课程提醒闹钟 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 需重调度的场景：BOOT_COMPLETED 重启丢全部闹钟；MY_PACKAGE_REPLACED 更新丢全部闹钟；
        // TIME_CHANGED/TIMEZONE_CHANGED 绝对时间闹钟失真。
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                ClassDndHelper.reconcileAfterSystemEvent(context)
                // 重置全部闹钟（含 widget 刷新链），覆盖「只用小组件、不开提醒」的用户。
                CourseReminderHelper.startReminderService(context)
            }
        }
    }
}
