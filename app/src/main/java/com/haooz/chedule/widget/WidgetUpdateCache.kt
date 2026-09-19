/** 小组件刷新辅助：无实例时跳过，内容未变时跳过重绘 */
package com.haooz.chedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object WidgetUpdateCache {

    /** 存完整签名字符串，避免 hashCode 碰撞导致内容变化却被跳过 */
    private val lastSignatures = ConcurrentHashMap<String, String>()

    /** 内容签名相同则应跳过本次 updateAppWidget */
    fun shouldSkip(key: String, signature: String): Boolean {
        val prev = lastSignatures.put(key, signature)
        return prev != null && prev == signature
    }

    fun invalidate(keyPrefix: String) {
        lastSignatures.keys.filter { it.startsWith(keyPrefix) }.forEach { lastSignatures.remove(it) }
    }

    /** 组件被删除时清掉该 id 的签名，避免系统复用 id 后首帧被误跳过 */
    fun invalidateWidget(key: String) {
        lastSignatures.remove(key)
    }

    fun clear() = lastSignatures.clear()

    fun hasProviderWidgets(context: Context, provider: Class<*>): Boolean {
        return try {
            val am = AppWidgetManager.getInstance(context) ?: return true
            am.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
        } catch (_: Exception) {
            true
        }
    }

    /** 按桌面实际放置情况刷新；未放置的 provider 完全不广播 */
    fun updateInstalledWidgets(context: Context) {
        if (hasProviderWidgets(context, CourseWidgetProviderStandard::class.java)) {
            CourseWidgetProviderStandard.updateAllWidgets(context)
        }
        if (hasProviderWidgets(context, TodayCourseWidgetProviderStandard::class.java)) {
            TodayCourseWidgetProviderStandard.updateAllWidgets(context)
        }
        if (hasProviderWidgets(context, CourseWidgetProviderPad::class.java)) {
            CourseWidgetProviderPad.updateAllWidgets(context)
        }
        if (hasProviderWidgets(context, TodayCourseWidgetProviderPad::class.java)) {
            TodayCourseWidgetProviderPad.updateAllWidgets(context)
        }
    }
}
