package com.haooz.chedule.wearable

import com.haooz.chedule.data.HolidayEntry
import com.haooz.chedule.data.toJson

import android.content.Context
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.HolidayManager
import org.json.JSONArray
import org.json.JSONObject
import com.haooz.chedule.data.NexioLog

/**
 * 手机端 → 手表端 课表 JSON 组装，协议 version=4 整表推送。
 *
 * 一次下发完整学期，手表端自行推算任意日期，不再依赖「当前周快照 + 按日期窗口」。
 * 协议与手表 `src/common/sync.js` 的整表分支对齐，跨端字段改动必须两边同步：
 * - 顶层：protocol=nexio.schedule / version=4 / action=replace / sentAt / schedule_name
 * - settings：class_start_time 开学日、current_week 实时教学周（手表据此校准调休合并周
 *   偏移）、total_weeks、morning|afternoon|evening_sections
 * - times：相对节次号 -> "HH:mm-HH:mm"（getPeriodTimes 原格式）
 * - courses：整学期全部课程，不过滤周次；dayOfWeek 1=周一…7=周日；
 *   startWeek/endWeek 为原始值（0=未设置），weekType 0全周/1单周/2双周，
 *   selectedWeeks 非空时优先于上面三者
 * - holidays：HolidayEntry[]，缺失则手表无法显示调休日
 *
 * 手表端周次 = floorDiv(开学日到当天天数 + (开学日星期-1), 7) + 1，再用 current_week
 * 校准偏移（±2 内吸收）；周次过滤与 Course.isActiveInWeek 严格一致。
 */
object WatchPayload {

    private const val PROTOCOL = "nexio.schedule"
    private const val VERSION = 4

    /** 整表推送 JSON。课程取 Course 原始值，周次过滤与星期坐标系都交给手表端。 */
    // USELESS_ELVIS / ELVIS_ALWAYS_NULL：Course 字段声明为非空 String，但 Gson UnsafeAllocator
    // 会给旧数据塞 null，这里的 ?: 是推送兜底，不能删。与 CourseRepository.sanitizeCourses 同一处理。
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
    fun buildFullJson(
        repository: CourseRepository,
        context: Context,
        scheduleId: String = ""
    ): String {
        val sid = scheduleId.ifEmpty { repository.getCurrentScheduleId() }
        val all = if (sid == repository.getCurrentScheduleId()) {
            repository.getAllCourses()
        } else {
            repository.getCoursesForSchedule(sid)
        }

        val courseArr = JSONArray()
        for (course in all) {
            if (course.dayOfWeek !in 1..7) continue
            courseArr.put(
                JSONObject()
                    .put("id", course.id)
                    .put("name", course.name)
                    .put("dayOfWeek", course.dayOfWeek)
                    .put("startSection", course.startSection)
                    .put("endSection", course.endSection)
                    .put("startWeek", course.startWeek)
                    .put("endWeek", course.endWeek)
                    .put("weekType", course.weekType)
                    .put("selectedWeeks", JSONArray(course.selectedWeeks))
                    .put("isCustomTime", course.isCustomTime)
                    .put("customStartTime", course.customStartTime ?: "")
                    .put("customEndTime", course.customEndTime ?: "")
                    .put("location", course.classroom ?: "")
                    .put("teacher", course.teacher ?: "")
            )
        }

        val settings = JSONObject()
            .put("class_start_time", repository.getClassStartTime(sid))
            .put("current_week", repository.getLiveTeachingWeek(scheduleId = sid))
            .put("total_weeks", repository.getTotalWeeks(sid))
            .put("morning_sections", repository.getMorningSections(sid))
            .put("afternoon_sections", repository.getAfternoonSections(sid))
            .put("evening_sections", repository.getEveningSections(sid))

        val times = JSONObject()
            .put("morning", periodTimesJson(repository, "morning", sid))
            .put("afternoon", periodTimesJson(repository, "afternoon", sid))
            .put("evening", periodTimesJson(repository, "evening", sid))

        val holidaysArr = buildHolidaysJson(context)
        NexioLog.i(
            "WatchPayload",
            "buildFull sid=$sid total=${all.size} packed=${courseArr.length()} " +
                "week=${settings.optInt("current_week")}/${settings.optInt("total_weeks")} " +
                "holidays=${holidaysArr.length()}"
        )

        return JSONObject()
            .put("protocol", PROTOCOL)
            .put("version", VERSION)
            .put("action", "replace")
            .put("sentAt", System.currentTimeMillis())
            .put("schedule_name", sid)
            .put("settings", settings)
            .put("times", times)
            .put("courses", courseArr)
            .put("holidays", holidaysArr)
            .toString()
    }

    /** HolidayEntry → 手表 holidays 数组（字段与 HolidayEntry.toJson 一致） */
    private fun buildHolidaysJson(context: Context): JSONArray {
        val arr = JSONArray()
        val entries = HolidayManager.loadAllByYear().values.flatten()
        for (entry in entries) {
            // ⚠ 必须包一层 JSONObject(...)：entry.toJson() 现在返回 kotlinx 的 JsonObject
            // （不是 org.json 的类型），直接 put 进去会被 org.json 当成未知对象转成
            // 一个**带引号的字符串**，手表侧就解析不出结构了。转成 JSON 文本再解析回来
            // 得到的还是同一个对象，载荷逐字不变。
            arr.put(JSONObject(entry.toJson().toString()))
        }
        return arr
    }

    /** 节次时间 → 手表格式 {相对节次号: 'HH:mm-HH:mm'}（getPeriodTimes 本身即此格式） */
    // 同 buildFullJson：Map 声明为 Map<Int,String>，Gson 泛型丢失后 value 仍可能是 null
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
    private fun periodTimesJson(
        repository: CourseRepository,
        period: String,
        scheduleId: String
    ): JSONObject {
        val obj = JSONObject()
        for ((index, time) in repository.getPeriodTimes(period, scheduleId)) {
            obj.put(index.toString(), time ?: "")
        }
        return obj
    }
}