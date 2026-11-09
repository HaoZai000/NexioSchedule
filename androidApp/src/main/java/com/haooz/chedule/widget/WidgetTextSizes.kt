/** 小组件文本尺寸工具：按设备像素密度（dp）重设文本与位图尺寸，
 *  使小组件在不同密度设备上等比缩放；文本用 DIP，不随系统字体缩放（fontScale）变化 */
package com.haooz.chedule.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.haooz.chedule.R
import com.haooz.chedule.data.storage.AppStorage
import java.util.Locale

object WidgetTextSizes {

    /** 返回设备当前像素密度（dp→px 换算系数） */
    fun deviceDensity(context: Context): Float = context.resources.displayMetrics.density

    /** 将 dp 按设备密度换算为 px */
    fun dpToPx(context: Context, dp: Float): Float = dp * deviceDensity(context)

    /** HyperOS / 小米系（小米、红米、POCO）：小部件外观按 HyperOS 规范走（如圆角 24dp） */
    val isXiaomi: Boolean by lazy {
        val brands = setOf("xiaomi", "redmi", "poco")
        Build.BRAND.lowercase(Locale.ROOT) in brands || Build.MANUFACTURER.lowercase(Locale.ROOT) in brands
    }

    /** ColorOS（OPPO/realme/OnePlus）：会按声明尺寸把小部件内容整体缩放，需按比例反补偿 */
    val isColorOs: Boolean by lazy {
        val brands = setOf("oppo", "realme", "oneplus")
        Build.BRAND.lowercase(Locale.ROOT) in brands || Build.MANUFACTURER.lowercase(Locale.ROOT) in brands
    }

    /**
     * 判断小组件当前是否处于深色模式：优先读取应用设置（theme_mode），
     * "system" 时跟随系统 uiMode，"dark"/"light" 分别取对应值。
     */
    fun isDark(context: Context): Boolean {
        return when (AppStorage.store("app_theme_prefs").getString("theme_mode", "system")) {
            "dark" -> true
            "light" -> false
            else -> {
                val night = context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun setTextSize(views: RemoteViews, id: Int, dp: Float) {
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, dp)
    }

    /** 课程表小组件各文本的基准字号（dp）。
     *  整行版（widget_course_reminder_standard）与今明版（widget_today_tomorrow_course）
     *  共用本表：布局里没有的 id，RemoteViews 找不到视图即静默跳过。 */
    private val COURSE_REMINDER_TEXTS = listOf(
        // 整行版表头
        R.id.widget_title to 14f,
        R.id.widget_week to 14f,
        // 今明版列表头（今天/明天）与列内空态文案
        R.id.widget_header_today to 14f,
        R.id.widget_header_tomorrow to 14f,
        R.id.widget_empty_today to 13f,
        R.id.widget_empty_tomorrow to 13f,
        // 卡位 1~5（整行版：时间列 + 课名/信息 + 倒计时）
        R.id.widget_time_start1 to 12f,
        R.id.widget_time_end1 to 12f,
        R.id.widget_name1 to 14f,
        R.id.widget_info1 to 12f,
        R.id.widget_now1 to 14f,
        R.id.widget_time_start2 to 12f,
        R.id.widget_time_end2 to 12f,
        R.id.widget_name2 to 14f,
        R.id.widget_info2 to 12f,
        R.id.widget_now2 to 14f,
        R.id.widget_time_start3 to 12f,
        R.id.widget_time_end3 to 12f,
        R.id.widget_name3 to 14f,
        R.id.widget_info3 to 12f,
        R.id.widget_now3 to 14f,
        R.id.widget_time_start4 to 12f,
        R.id.widget_time_end4 to 12f,
        R.id.widget_name4 to 14f,
        R.id.widget_info4 to 12f,
        R.id.widget_now4 to 14f,
        R.id.widget_time_start5 to 12f,
        R.id.widget_time_end5 to 12f,
        R.id.widget_name5 to 14f,
        R.id.widget_info5 to 12f,
        R.id.widget_now5 to 14f,
        // 卡位 6~10（仅今明版有）：课名 + 信息行
        R.id.widget_name6 to 14f,
        R.id.widget_info6 to 12f,
        R.id.widget_name7 to 14f,
        R.id.widget_info7 to 12f,
        R.id.widget_name8 to 14f,
        R.id.widget_info8 to 12f,
        R.id.widget_name9 to 14f,
        R.id.widget_info9 to 12f,
        R.id.widget_name10 to 14f,
        R.id.widget_info10 to 12f,
        // 今明版卡片第二行：课程时间（起-止）
        R.id.widget_course_time1 to 12f,
        R.id.widget_course_time2 to 12f,
        R.id.widget_course_time3 to 12f,
        R.id.widget_course_time4 to 12f,
        R.id.widget_course_time5 to 12f,
        R.id.widget_course_time6 to 12f,
        R.id.widget_course_time7 to 12f,
        R.id.widget_course_time8 to 12f,
        R.id.widget_course_time9 to 12f,
        R.id.widget_course_time10 to 12f,
        R.id.widget_empty_text to 14f,
    )

    /**
     * 课程表小组件（widget_course_reminder_standard / widget_today_tomorrow_course）。
     * @param scale 字号整体倍率，供 ColorOS 拉伸后反补偿用（默认 1 倍）
     */
    fun applyCourseReminder(views: RemoteViews, scale: Float = 1f) {
        COURSE_REMINDER_TEXTS.forEach { (id, dp) -> setTextSize(views, id, dp * scale) }
    }

    /** 今日课程小组件各文本的基准字号（dp） */
    private val TODAY_COURSE_TEXTS = listOf(
        R.id.widget_title to 14f,
        R.id.widget_week to 14f,
        R.id.widget_course_name to 17f,
        R.id.widget_course_time to 14f,
        R.id.widget_course_location to 14f,
        R.id.widget_remaining_text to 12f,
        R.id.widget_empty_text to 14f,
    )

    /**
     * 今日课程小组件（widget_today_course_standard）。
     * @param scale 字号整体倍率，供 ColorOS 体型偏大时的反补偿用（默认 1 倍）
     */
    fun applyTodayCourse(views: RemoteViews, scale: Float = 1f) {
        TODAY_COURSE_TEXTS.forEach { (id, dp) -> setTextSize(views, id, dp * scale) }
    }

    /**
     * 按**指定**深浅色重刷文字颜色（不跟系统）。
     *
     * 布局里的 textColor 是 `@color/...`，由 values-night 限定符决定，跟的是宿主系统的深色开关。
     * 小部件壁纸把深浅色按照片锁死之后，必须把这些颜色显式下发，否则会出现
     * 「深色照片 + 浅色系统」→ 深色字压在照片上读不出来。
     *
     * @param courseReminder true = 4×2 今日 / 今明那两套布局；false = 2×2
     */
    fun applyTextColors(
        views: RemoteViews,
        context: Context,
        dark: Boolean,
        courseReminder: Boolean,
        monet: WidgetMonetColors.Palette? = null,
    ) {
        val ids = if (courseReminder) COURSE_REMINDER_TEXTS else TODAY_COURSE_TEXTS
        val forced = if (monet == null) forcedNightContext(context, dark) else context
        ids.forEach { (id, _) ->
            val entry = context.resources.getResourceEntryName(id)
            val color = if (monet != null) {
                WidgetMonetColors.textColor(monet, entry)
            } else {
                ContextCompat.getColor(forced, colorResFor(entry))
            }
            views.setTextColor(id, color)
        }
    }

    /** 同一份资源在强制深浅配置下取色；一次更新只建一个 Context。 */
    private fun forcedNightContext(context: Context, dark: Boolean): Context {
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return context.createConfigurationContext(config)
    }

    /** 文本 id → 布局里用的那个颜色资源（按命名归类，新加卡位自动覆盖）。 */
    private fun colorResFor(entry: String): Int = when {
        entry == "widget_week" ||
            entry.startsWith("widget_title") ||
            entry.startsWith("widget_header") -> R.color.widget_title_color

        entry.startsWith("widget_now") -> R.color.widget_now_color
        entry.startsWith("widget_empty") -> R.color.widget_empty_color

        entry.startsWith("widget_time_start") ||
            entry.startsWith("widget_course_time") -> R.color.widget_time_start_color

        entry.startsWith("widget_time_end") ||
            entry.startsWith("widget_info") ||
            entry == "widget_course_location" ||
            entry == "widget_remaining_text" -> R.color.widget_time_end_color

        else -> R.color.widget_name_color
    }
}
