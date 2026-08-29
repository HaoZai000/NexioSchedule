/** 小组件文本尺寸工具：以设备密度换算 px 重设文本，使文本不受系统字体缩放（fontScale）影响 */
package com.haooz.chedule.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import com.haooz.chedule.R

object WidgetTextSizes {

    private fun setTextSize(context: Context, views: RemoteViews, id: Int, sp: Int) {
        val density = context.resources.displayMetrics.density
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, sp * density)
    }

    /** 课程表小组件（widget_course_reminder_standard） */
    fun applyCourseReminder(context: Context, views: RemoteViews) {
        setTextSize(context, views, R.id.widget_title, 14)
        setTextSize(context, views, R.id.widget_week, 14)
        setTextSize(context, views, R.id.widget_time_start1, 12)
        setTextSize(context, views, R.id.widget_time_end1, 12)
        setTextSize(context, views, R.id.widget_name1, 14)
        setTextSize(context, views, R.id.widget_info1, 12)
        setTextSize(context, views, R.id.widget_now1, 14)
        setTextSize(context, views, R.id.widget_time_start2, 12)
        setTextSize(context, views, R.id.widget_time_end2, 12)
        setTextSize(context, views, R.id.widget_name2, 14)
        setTextSize(context, views, R.id.widget_info2, 12)
        setTextSize(context, views, R.id.widget_now2, 14)
        setTextSize(context, views, R.id.widget_empty_text, 14)
    }

    /** 今日课程小组件（widget_today_course_standard） */
    fun applyTodayCourse(context: Context, views: RemoteViews) {
        setTextSize(context, views, R.id.widget_title, 14)
        setTextSize(context, views, R.id.widget_week, 14)
        setTextSize(context, views, R.id.widget_course_name, 17)
        setTextSize(context, views, R.id.widget_course_time, 14)
        setTextSize(context, views, R.id.widget_course_location, 14)
        setTextSize(context, views, R.id.widget_remaining_text, 12)
        setTextSize(context, views, R.id.widget_empty_text, 14)
    }
}
