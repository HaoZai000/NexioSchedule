/** 小组件刷新广播接收器 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haooz.chedule.widget.WidgetUpdateCache

class WidgetRefreshReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.haooz.chedule.ACTION_REFRESH_WIDGET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            try {
                // 跨日检测：日期变化时重新调度闹钟（覆盖次日课程无提醒的场景）
                CourseReminderHelper.checkAndRescheduleOnDayChange(context)
                // 兜底补发：若某门课闹钟未触发/丢失，在提醒窗口内立即补发（含超级岛通道）
                CourseReminderHelper.checkPendingPreClassReminders(context)
                // 桌面未放置任何小组件时跳过 RemoteViews/Canvas 重绘
                WidgetUpdateCache.updateInstalledWidgets(context)
                // 超级岛对账：闹钟丢失/Doze 延迟时兜底切换到"已上课"并按时收起
                CourseReminderHelper.reconcileIslandCountdown(context)
                CourseReminderHelper.updateActiveCountdown(context)
                // 上课勿扰对账：闹钟丢失/被系统清理时，靠刷新链兜底补上开关
                ClassDndHelper.applyCurrentState(context)
            } finally {
                // 链式调度下一次刷新放在 finally：即使上面任一步抛异常，
                // 也要保证跨日重设与兜底补发不中断
                CourseReminderHelper.scheduleNextWidgetRefresh(context, alarmManager)
            }
        }
    }
}
