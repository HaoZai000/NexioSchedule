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
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_CLASSROOM = "classroom"
        const val EXTRA_SECTION = "section"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_END_TIME = "end_time"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_COURSE_START_MILLIS = "course_start_millis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received island expand broadcast")

        // 闹钟可能因为 Doze 严重延迟，等它到达时岛上已经是另一门课的倒计时了。
        // 这里校验开始时间是否仍与当前岛状态一致，不一致说明是迟到的旧闹钟，直接丢弃。
        // 测试岛与真实岛有独立 PREF，按 notificationId 选择对应的 state。
        val notificationId2 = intent.getIntExtra(EXTRA_NOTIFICATION_ID, IslandNotificationHelper.ISLAND_NOTIFICATION_ID)
        val expectedStart = intent.getLongExtra(EXTRA_COURSE_START_MILLIS, -1L)
        if (expectedStart > 0L) {
            val current = IslandNotificationHelper.IslandState.snapshotFor(context, notificationId2)
            if (current == null || current.startMillis != expectedStart) {
                Log.d(TAG, "Stale expand alarm (expected=$expectedStart current=${current?.startMillis}), ignored")
                return
            }
        }
        if (IslandNotificationHelper.IslandState.isSwitched(
                context,
                testMode = notificationId2 == IslandNotificationHelper.ISLAND_TEST_NOTIFICATION_ID
            )
        ) {
            Log.d(TAG, "Already switched, ignored")
            return
        }

        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "课程"
        val classroom = intent.getStringExtra(EXTRA_CLASSROOM) ?: ""
        val section = intent.getStringExtra(EXTRA_SECTION) ?: ""
        val startTime = intent.getStringExtra(EXTRA_START_TIME) ?: ""
        val endTime = intent.getStringExtra(EXTRA_END_TIME) ?: ""
        // 必须与课前倒计时共用同一个通知 ID，否则"已上课"会另起一个岛，与倒计时岛重叠出现
        val notificationId = intent.getIntExtra(
            EXTRA_NOTIFICATION_ID,
            IslandNotificationHelper.ISLAND_NOTIFICATION_ID
        )

        // 发送更新后的通知，触发展开态弹出
        IslandNotificationHelper.sendClassStartedNotification(
            context = context,
            courseName = courseName,
            classroom = classroom,
            section = section,
            startTime = startTime,
            endTime = endTime,
            notificationId = notificationId
        )
    }
}