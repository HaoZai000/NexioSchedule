package com.haooz.chedule.mcp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.haooz.chedule.R
import com.haooz.chedule.data.mcp.McpServiceBridge

/**
 * MCP 服务保活。
 *
 * HyperOS 会冻结后台进程，若服务只挂在 [android.app.Application] 上，小爱发起请求时
 * 进程已被冻结，表现就是请求超时。开启 MCP 期间用常驻通知维持前台服务。
 */
class McpForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_calendar)
            .setContentTitle(getString(R.string.mcp_notification_title))
            .setContentText(getString(R.string.mcp_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        McpLocalServer.start()
        McpServiceBridge.running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        McpLocalServer.stop()
        McpServiceBridge.running = false
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.mcp_notification_channel), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "nexio_mcp_server"
        private const val NOTIFICATION_ID = 0x4D43

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, McpForegroundService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, McpForegroundService::class.java))
            }
        }
    }
}
