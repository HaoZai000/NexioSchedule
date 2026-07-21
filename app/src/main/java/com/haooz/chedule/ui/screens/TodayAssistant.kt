/** 今日助手 - 智能课程状态、天气提醒、时段提示 */
package com.haooz.chedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.haooz.chedule.data.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

// ===================== 天气工具 =====================

private fun getWeatherEmoji(code: Int): String = when (code) {
    0 -> "\u2600\uFE0F"           // 晴
    1, 2, 3 -> "\u26C5\uFE0F"    // 多云
    45, 48 -> "\uD83C\uDF2B\uFE0F"  // 雾
    51, 53, 55 -> "\uD83C\uDF26\uFE0F"  // 毛毛雨
    61, 63, 65 -> "\uD83C\uDF27\uFE0F"  // 小到大雨
    71, 73, 75 -> "\uD83C\uDF28\uFE0F"  // 雪
    80, 81, 82 -> "\uD83C\uDF29\uFE0F"  // 阵雨
    95, 96, 99 -> "\u26C8\uFE0F"  // 雷暴
    else -> "\uD83C\uDF21\uFE0F"
}

private fun isRainy(code: Int): Boolean = code in 51..67 || code in 80..82

private fun getWeatherCondition(code: Int): String = when (code) {
    0 -> "晴天"
    1, 2, 3 -> "多云"
    45, 48 -> "雾"
    51, 53, 55 -> "小雨"
    61, 63, 65 -> "中到大雨"
    71, 73, 75 -> "雪"
    80, 81, 82 -> "阵雨"
    95, 96, 99 -> "雷暴"
    else -> "未知"
}

private fun getWeatherAdvice(temp: Float, weatherCode: Int): String = when {
    isRainy(weatherCode) -> "记得带伞"
    temp < 10f -> "注意保暖"
    temp in 10f..20f -> "适当添衣"
    temp in 28f..35f -> "注意防暑"
    temp > 35f -> "极端高温"
    else -> "适合出行"
}

private fun parseTime(timeStr: String): LocalTime? {
    return try {
        LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        null
    }
}

// ===================== OkHttpClient 单例 =====================

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

// ===================== 天气数据 =====================

private data class WeatherData(
    val temperature: Float = Float.NaN,
    val weatherCode: Int = -1,
    val loaded: Boolean = false
)

@Composable
private fun rememberWeather(): WeatherData {
    var weather by remember { mutableStateOf(WeatherData()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=23.585&longitude=116.459" +
                    "&current=temperature_2m,weathercode"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    val body = resp.body?.string() ?: return@use
                    @Suppress("UNCHECKED_CAST")
                    val json = Gson().fromJson(body, Map::class.java) as? Map<String, Any> ?: return@use
                    @Suppress("UNCHECKED_CAST")
                    val current = json["current"] as? Map<String, Any> ?: return@use
                    val temp = (current["temperature_2m"] as? Number)?.toFloat() ?: Float.NaN
                    val code = (current["weathercode"] as? Number)?.toInt() ?: -1
                    weather = WeatherData(temp, code, true)
                }
            } catch (_: Exception) {
                weather = WeatherData(loaded = true)
            }
        }
    }
    return weather
}

// ===================== 课程状态（每秒更新） =====================

private data class CourseStatus(
    val currentCourse: Course? = null,
    val nextCourse: Course? = null,
    val timeMessage: String = ""
)

@Composable
private fun rememberCourseStatus(
    courses: List<Course>,
    sectionTimes: Map<Int, String>
): CourseStatus {
    var status by remember { mutableStateOf(CourseStatus()) }
    LaunchedEffect(courses, sectionTimes) {
        while (true) {
            val now = LocalTime.now()
            val current = courses.find { course ->
                val startStr = sectionTimes[course.startSection]?.split("-")?.firstOrNull() ?: return@find false
                val endStr = sectionTimes[course.endSection]?.split("-")?.lastOrNull() ?: return@find false
                val start = parseTime(startStr) ?: return@find false
                val end = parseTime(endStr) ?: return@find false
                !now.isBefore(start) && !now.isAfter(end)
            }
            val next = courses.find { course ->
                val startStr = sectionTimes[course.startSection]?.split("-")?.firstOrNull() ?: return@find false
                val start = parseTime(startStr) ?: return@find false
                now.isBefore(start)
            }
            val message = when {
                current != null -> {
                    val endStr = sectionTimes[current.endSection]?.split("-")?.lastOrNull() ?: ""
                    val end = parseTime(endStr)
                    if (end != null) {
                        val minutes = java.time.Duration.between(now, end).toMinutes()
                        "还剩 ${minutes +1} 分钟"
                    } else ""
                }
                next != null -> {
                    val startStr = sectionTimes[next.startSection]?.split("-")?.firstOrNull() ?: ""
                    val start = parseTime(startStr)
                    if (start != null) {
                        val minutes = java.time.Duration.between(now, start).toMinutes()
                        "${minutes +1} 分钟后"
                    } else ""
                }
                courses.isEmpty() -> ""
                else -> ""
            }
            status = CourseStatus(current, next, message)
            delay(1000L.milliseconds)
        }
    }
    return status
}

// ===================== 智能提示生成 =====================

private data class CourseTimeRange(
    val course: Course,
    val start: LocalTime,
    val end: LocalTime
)

private fun buildCourseTimeRanges(
    courses: List<Course>,
    sectionTimes: Map<Int, String>
): List<CourseTimeRange> {
    return courses.mapNotNull { course ->
        val startStr = sectionTimes[course.startSection]?.split("-")?.firstOrNull() ?: return@mapNotNull null
        val endStr = sectionTimes[course.endSection]?.split("-")?.lastOrNull() ?: return@mapNotNull null
        val start = parseTime(startStr) ?: return@mapNotNull null
        val end = parseTime(endStr) ?: return@mapNotNull null
        CourseTimeRange(course, start, end)
    }.sortedBy { it.start }
}

private fun generateSmartTip(
    courses: List<Course>,
    sectionTimes: Map<Int, String>,
    morningSections: Int,
    afternoonSections: Int
): String? {
    val now = LocalTime.now()
    val ranges = buildCourseTimeRanges(courses, sectionTimes)
    if (ranges.isEmpty()) return null

    val ongoing = ranges.find { now in it.start..it.end }
    val next = ranges.find { now.isBefore(it.start) }
    val prev = ranges.lastOrNull { now.isAfter(it.end) }

    val eveningCount = courses.count { it.startSection > morningSections + afternoonSections }
    val totalCount = courses.size

    return when {
        ongoing != null -> {
            val remaining = java.time.Duration.between(now, ongoing.end).toMinutes()
            val nextAfter = ranges.find { it.start > ongoing.end }
            val gap = nextAfter?.let { java.time.Duration.between(ongoing.end, it.start).toMinutes() }
            when {
                remaining <= 5 -> "快下课了"
                remaining <= 15 -> "还有 $remaining 分钟下课"
                gap != null && gap in 1..15 -> "下课后只有 $gap 分钟休息"
                else -> "正在上课中"
            }
        }

        prev != null && next != null -> {
            val breakMinutes = java.time.Duration.between(prev.end, next.start).toMinutes()
            when {
                breakMinutes > 60 -> "距下一节课还有 $breakMinutes 分钟"
                breakMinutes <= 15 -> "课间休息中"
                else -> "休息中"
            }
        }

        prev != null && next == null -> {
            val hour = now.hour
            when {
                hour >= 22 -> "明天还有 $totalCount 节课，早点休息"
                hour in 18..21 -> "今天辛苦了，好好休息"
                eveningCount > 0 -> "晚上还有 $eveningCount 节课"
                else -> "今天课程已结束"
            }
        }

        next != null -> {
            val minutes = java.time.Duration.between(now, next.start).toMinutes()
            when {
                minutes > 120 -> "今天 $totalCount 节课"
                minutes in 61..120 -> "还有 $minutes 分钟上课"
                minutes in 2..60 -> "快准备好出发"
                minutes in 0..1 -> "马上要上课了"
                else -> "等待上课中"
            }
        }

        else -> "美好的一天开始了"
    }
}

// ===================== 主组件（紧凑排版） =====================

@Composable
fun TodayAssistantCard(
    courses: List<Course>,
    sectionTimes: Map<Int, String>,
    morningSections: Int,
    afternoonSections: Int
) {
    val weather = rememberWeather()
    val courseStatus = rememberCourseStatus(courses, sectionTimes)
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L.milliseconds)
            tick = System.currentTimeMillis()
        }
    }
    val smartTip = remember(courses, weather, sectionTimes, morningSections, afternoonSections, tick) {
        generateSmartTip(courses, sectionTimes, morningSections, afternoonSections) ?: ""
    }

    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val currentCourse = courseStatus.currentCourse
            val nextCourse = courseStatus.nextCourse

            // 标签 + 课程名 + 时间（同一行）
            val label = when {
                currentCourse != null -> "正在上课"
                nextCourse != null -> "下节课"
                courses.isEmpty() -> ""
                else -> ""
            }
            val name = when {
                currentCourse != null -> currentCourse.name
                nextCourse != null -> nextCourse.name
                courses.isEmpty() -> "今天没有课程，好好休息吧！"
                else -> "当前没有课程"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (label.isNotBlank()) {
                        Text(
                            text = label,
                            style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                if (courseStatus.timeMessage.isNotBlank()) {
                    Text(
                        text = courseStatus.timeMessage,
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 15.sp),
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }

            // 地点/教师
            val location = when {
                currentCourse != null -> currentCourse.classroom
                nextCourse != null -> nextCourse.classroom
                else -> ""
            }
            val teacher = when {
                currentCourse != null -> currentCourse.teacher
                nextCourse != null -> nextCourse.teacher
                else -> ""
            }
            if (location.isNotBlank() || teacher.isNotBlank()) {
                Text(
                    text = buildString {
                        if (location.isNotBlank()) append(location)
                        if (location.isNotBlank() && teacher.isNotBlank()) append(" | ")
                        if (teacher.isNotBlank()) append(teacher)
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .height(0.5.dp)
                    .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f))
            )
            Spacer(modifier = Modifier.height(2.dp))
            // 智能提示 + 天气
            val weatherLine = if (weather.loaded && !weather.temperature.isNaN()) {
                val condition = getWeatherCondition(weather.weatherCode)
                val advice = getWeatherAdvice(weather.temperature, weather.weatherCode)
                "${getWeatherEmoji(weather.weatherCode)} ${weather.temperature.toInt()}°C $condition · $advice"
            } else if (weather.loaded) {
                "加载中..."
            } else {
                "⏳ __℃ · 加载中..."
            }

            if (smartTip.isNotBlank() || weatherLine.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (smartTip.isNotBlank()) {
                        Text(
                            text = smartTip,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                    if (weatherLine.isNotBlank()) {
                        Text(
                            text = weatherLine,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
        }
    }
}
