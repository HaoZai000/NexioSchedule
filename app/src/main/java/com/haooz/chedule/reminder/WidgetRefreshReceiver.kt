/** 小组件刷新广播接收器 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetRefreshReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.haooz.chedule.ACTION_REFRESH_WIDGET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH_WIDGET) {
            // 跨日检测：日期变化时重新调度闹钟（覆盖次日课程无提醒的场景）
            CourseReminderHelper.checkAndRescheduleOnDayChange(context)
            com.haooz.chedule.widget.CourseWidgetProvider.updateAllWidgets(context)
            CourseReminderHelper.updateActiveCountdown(context)
            // 链式调度下一次刷新：根据是否有课进行中决定下次刷新时间
            // 有课时每分钟刷新（保证倒计时及时更新），无课时延长间隔省电
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            CourseReminderHelper.scheduleNextWidgetRefresh(context, alarmManager)
        }
    }
}
