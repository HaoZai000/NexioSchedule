package com.haooz.chedule.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 超级岛"已上课"自动收起触发器。
 *
 * 之前收起只由每分钟的刷新链对账完成，最坏要等一整分钟才消失（用户反馈"已上课过好久才消失"）；
 * 且进程被杀后刷新链若未重启就永远收不起来。这里改用精确闹钟，展示时长一到立即收起。
 */
class IslandDismissReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IslandDismissReceiver"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_COURSE_START_MILLIS = "course_start_millis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(
            EXTRA_NOTIFICATION_ID,
            IslandNotificationHelper.ISLAND_NOTIFICATION_ID
        )
        val expectedStart = intent.getLongExtra(EXTRA_COURSE_START_MILLIS, -1L)

        // 测试岛与真实岛有独立 PREF，必须按 notificationId 选对应的 state，否则
        // 测试 dismiss 闹钟会清掉真实课的 state（反之亦然）。
        val state = IslandNotificationHelper.IslandState.snapshotFor(context, notificationId)

        if (expectedStart > 0L) {
            // 传了 startMillis：必须与当前 state 一致才执行 dismiss，否则就是迟到的旧闹钟
            if (state == null || state.startMillis != expectedStart) {
                Log.d(TAG, "Dismiss ignored: state moved on (now=${state?.startMillis}, expected=$expectedStart)")
                return
            }
        } else {
            // 没传 startMillis（legacy 闹钟，或调用方异常）：
            // 保守地不动当前活跃的 state，避免误收刚发出来的新一轮倒计时岛。
            if (state != null) {
                Log.d(TAG, "Dismiss without startMillis but state active, ignored")
                return
            }
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
        // 走到这里说明 state 必然是 null 或与 expectedStart 一致，清掉不会有副作用
        IslandNotificationHelper.IslandState.clear(
            context,
            testMode = notificationId == IslandNotificationHelper.ISLAND_TEST_NOTIFICATION_ID
        )
        Log.d(TAG, "Island dismissed id=$notificationId")
    }
}
