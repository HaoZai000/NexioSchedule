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
            com.haooz.chedule.widget.CourseWidgetProvider.updateAllWidgets(context)
            CourseReminderHelper.updateActiveCountdown(context)
        }
    }
}
