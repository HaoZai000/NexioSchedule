/** 上课勿扰闹钟接收器：仅接收本应用排期的上课 / 下课广播。 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haooz.chedule.data.platform.NexioLog
class ClassDndReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ClassDndReceiver"

        const val ACTION_CLASS_START = "com.haooz.chedule.ACTION_CLASS_DND_START"
        const val ACTION_CLASS_END = "com.haooz.chedule.ACTION_CLASS_DND_END"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ClassDndToggleReceiver.ACTION_TOGGLE -> ClassDndHelper.toggleFromNotification(context)
            ACTION_CLASS_START, ACTION_CLASS_END -> {
                NexioLog.d(TAG, "Class ${if (intent.action == ACTION_CLASS_START) "start" else "end"} alarm, syncing DND state")
                ClassDndHelper.applyCurrentState(context)
            }
        }
    }
}
