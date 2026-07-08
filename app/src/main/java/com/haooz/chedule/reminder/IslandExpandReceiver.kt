package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 超级岛展开态触发器
 * 倒计时结束后发送广播，触发展开态弹出
 */
class IslandExpandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IslandExpandReceiver"
        const val ACTION_ISLAND_EXPAND = "com.haooz.chedule.ACTION_ISLAND_EXPAND"
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_CLASSROOM = "classroom"
        const val EXTRA_SECTION = "section"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received island expand broadcast")

        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "课程"
        val classroom = intent.getStringExtra(EXTRA_CLASSROOM) ?: ""
        val section = intent.getStringExtra(EXTRA_SECTION) ?: ""
        val startTime = intent.getStringExtra(EXTRA_START_TIME) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)

        // 发送更新后的通知，触发展开态弹出
        IslandNotificationHelper.sendClassStartedNotification(
            context = context,
            courseName = courseName,
            classroom = classroom,
            section = section,
            startTime = startTime,
            notificationId = notificationId
        )
    }
}