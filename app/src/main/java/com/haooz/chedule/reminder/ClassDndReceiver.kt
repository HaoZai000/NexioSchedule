/**
 * 上课勿扰广播接收器
 *
 * 三类来源：
 * - [ACTION_TOGGLE]：实况通知 / 超级岛「上课勿扰」按钮
 * - [ACTION_CLASS_START]：上课时间点的精确闹钟
 * - [ACTION_CLASS_END]：下课时间点的精确闹钟
 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ClassDndReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ClassDndReceiver"

        /** 通知按钮：切换「上课自动开启勿扰」 */
        const val ACTION_TOGGLE = "com.haooz.chedule.ACTION_CLASS_DND_TOGGLE"
        /** 上课：进入勿扰 */
        const val ACTION_CLASS_START = "com.haooz.chedule.ACTION_CLASS_DND_START"
        /** 下课：退出勿扰 */
        const val ACTION_CLASS_END = "com.haooz.chedule.ACTION_CLASS_DND_END"
        /**
         * 测试通知专用：立即开关系统勿扰，不依赖课表与开关状态。
         * 仅由「测试小米超级岛」发出的通知携带，用于验证岛按钮到勿扰的链路是否可用。
         */
        const val ACTION_TEST_TOGGLE = "com.haooz.chedule.ACTION_CLASS_DND_TEST_TOGGLE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                Log.d(TAG, "Toggle class DND from notification")
                ClassDndHelper.toggleFromNotification(context)
            }
            ACTION_TEST_TOGGLE -> {
                Log.d(TAG, "Test notification: toggle DND immediately")
                ClassDndHelper.toggleDndNowForTest(context)
            }
            ACTION_CLASS_START, ACTION_CLASS_END -> {
                Log.d(TAG, "Class ${if (intent.action == ACTION_CLASS_START) "start" else "end"} alarm, syncing DND state")
                ClassDndHelper.applyCurrentState(context)
            }
        }
    }
}
