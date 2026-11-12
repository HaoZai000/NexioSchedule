package com.haooz.chedule.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haooz.chedule.data.platform.NexioLog

class ClassDndToggleReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ClassDndToggleReceiver"
        const val ACTION_TOGGLE = "com.haooz.chedule.ACTION_CLASS_DND_TOGGLE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val hasLiveNotice = try {
            manager?.activeNotifications?.isNotEmpty() == true
        } catch (_: Exception) {
            true
        }
        if (!hasLiveNotice &&
            !IslandNotificationHelper.IslandState.isActiveAny(context) &&
            !ClassDndHelper.isInClass(context)
        ) return
        NexioLog.d(TAG, "Toggle class DND from notification")
        ClassDndHelper.toggleFromNotification(context)
    }
}
