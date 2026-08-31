/** 小组件文本尺寸工具：固定以 480dpi（density=3.0）换算 px 重设文本，
 *  使文本不随系统字体缩放（fontScale）和设备 dpi 变化 */
package com.haooz.chedule.widget

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.widget.RemoteViews
import com.haooz.chedule.R

object WidgetTextSizes {

    /** 参考密度：480dpi 对应的 density 值，所有尺寸统一按此换算 */
    const val REFERENCE_DENSITY = 3.0f

    // ---- 主题相关：小组件位图背景需随深浅色选择不透明底色，避免透明像素被桌面渲染成灰色/白色框 ----
    /** 课程卡片（非进行中）底色：浅色=白 / 深色=#262626（对应 widget_card_background 两套） */
    const val CARD_INACTIVE_BG_LIGHT: Int = 0xFFFFFFFF.toInt()
    const val CARD_INACTIVE_BG_DARK: Int = 0xFF262626.toInt()

    /** 进行中课程卡片(#1A2196F3)叠加在卡片底上的不透明等效色，用于色条位图背景 */
    const val CARD_ACTIVE_OPAQUE_BG_LIGHT: Int = 0xFFE1EDF7.toInt()
    const val CARD_ACTIVE_OPAQUE_BG_DARK: Int = 0xFF25313B.toInt()

    /** 今日课程卡片底色：浅色=白 / 深色=#1A1A1A（对应 widget_today_course_background 两套） */
    const val TODAY_BG_LIGHT: Int = 0xFFFFFFFF.toInt()
    const val TODAY_BG_DARK: Int = 0xFF1A1A1A.toInt()

    /**
     * 判断小组件当前是否处于深色模式：优先读取应用设置（theme_mode），
     * "system" 时跟随系统 uiMode，"dark"/"light" 分别取对应值。
     */
    fun isDark(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE)
        return when (prefs.getString("theme_mode", "system")) {
            "dark" -> true
            "light" -> false
            else -> {
                val night = context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun setTextSize(views: RemoteViews, id: Int, sp: Int) {
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, sp * REFERENCE_DENSITY)
    }

    /** 课程表小组件（widget_course_reminder_standard） */
    fun applyCourseReminder(views: RemoteViews) {
        setTextSize(views, R.id.widget_title, 14)
        setTextSize(views, R.id.widget_week, 14)
        setTextSize(views, R.id.widget_time_start1, 12)
        setTextSize(views, R.id.widget_time_end1, 12)
        setTextSize(views, R.id.widget_name1, 14)
        setTextSize(views, R.id.widget_info1, 12)
        setTextSize(views, R.id.widget_now1, 14)
        setTextSize(views, R.id.widget_time_start2, 12)
        setTextSize(views, R.id.widget_time_end2, 12)
        setTextSize(views, R.id.widget_name2, 14)
        setTextSize(views, R.id.widget_info2, 12)
        setTextSize(views, R.id.widget_now2, 14)
        setTextSize(views, R.id.widget_empty_text, 14)
    }

    /** 今日课程小组件（widget_today_course_standard） */
    fun applyTodayCourse(views: RemoteViews) {
        setTextSize(views, R.id.widget_title, 14)
        setTextSize(views, R.id.widget_week, 14)
        setTextSize(views, R.id.widget_course_name, 17)
        setTextSize(views, R.id.widget_course_time, 14)
        setTextSize(views, R.id.widget_course_location, 14)
        setTextSize(views, R.id.widget_remaining_text, 12)
        setTextSize(views, R.id.widget_empty_text, 14)
    }
}
