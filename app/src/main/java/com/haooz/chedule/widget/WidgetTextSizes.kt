/** 小组件文本尺寸工具：固定以 480dpi（density=3.0）换算 px 重设文本，
 *  使文本不随系统字体缩放（fontScale）和设备 dpi 变化 */
package com.haooz.chedule.widget

import android.util.TypedValue
import android.widget.RemoteViews
import com.haooz.chedule.R

object WidgetTextSizes {

    /** 参考密度：480dpi 对应的 density 值，所有尺寸统一按此换算 */
    const val REFERENCE_DENSITY = 3.0f

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
