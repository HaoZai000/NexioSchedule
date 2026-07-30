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
import androidx.compose.foundation.layout.size
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

import com.haooz.chedule.R

private fun getWeatherIconRes(code: Int, isNight: Boolean = false): Int = when (code) {
    0 -> if (isNight) R.drawable.icon_sunny_night else R.drawable.icon_sunny
    1 -> if (isNight) R.drawable.icon_sunny_night else R.drawable.icon_sunny
    2 -> if (isNight) R.drawable.icon_cloudy_night else R.drawable.icon_cloudy
    3 -> R.drawable.icon_overcast
    45, 48 -> R.drawable.icon_float_dirt
    51, 53, 55 -> R.drawable.icon_light_rain
    56, 57 -> R.drawable.icon_ice_rain
    61 -> R.drawable.icon_light_rain
    63 -> R.drawable.icon_moderate_rain
    65 -> R.drawable.icon_heavy_rain
    66, 67 -> R.drawable.icon_ice_rain
    71 -> R.drawable.icon_light_snow
    73 -> R.drawable.icon_moderate_snow
    75 -> R.drawable.icon_heavy_snow
    77 -> R.drawable.icon_light_snow
    80 -> R.drawable.icon_light_rain
    81 -> R.drawable.icon_moderate_rain
    82 -> R.drawable.icon_heavy_rain
    85 -> R.drawable.icon_light_snow
    86 -> R.drawable.icon_heavy_snow
    95, 96, 99 -> R.drawable.icon_t_storm
    else -> if (isNight) R.drawable.icon_sunny_night else R.drawable.icon_sunny
}

private fun isRainy(code: Int): Boolean = code in 51..67 || code in 80..82

private fun isSnowy(code: Int): Boolean = code in 71..77 || code in 85..86

private fun isStormy(code: Int): Boolean = code in 95..99

private fun isFoggy(code: Int): Boolean = code in 45..48

private fun getWeatherCondition(code: Int): String = when (code) {
    0 -> "晴天"
    1 -> "大部晴朗"
    2 -> "局部多云"
    3 -> "阴天"
    45, 48 -> "雾"
    51, 53, 55 -> "毛毛雨"
    56, 57 -> "冻雨"
    61 -> "小雨"
    63 -> "中雨"
    65 -> "大雨"
    66, 67 -> "冻雨"
    71 -> "小雪"
    73 -> "中雪"
    75 -> "大雪"
    77 -> "雪粒"
    80 -> "小阵雨"
    81 -> "中阵雨"
    82 -> "暴阵雨"
    85 -> "小阵雪"
    86 -> "大阵雪"
    95 -> "雷暴"
    96 -> "雷暴伴冰雹"
    99 -> "强雷暴"
    else -> "未知"
}

private fun getWeatherAdvice(temp: Float, weatherCode: Int): String = when {
    // 恶劣天气优先
    isStormy(weatherCode) -> "雷暴天气，避免外出"
    isFoggy(weatherCode) -> "能见度低，注意安全"
    // 雨天
    weatherCode == 56 || weatherCode == 57 -> "冻雨路滑，减少出行"
    weatherCode == 66 || weatherCode == 67 -> "冻雨天气，注意安全"
    weatherCode == 65 -> "大雨倾盆，带好雨具"
    weatherCode in 61..63 || isRainy(weatherCode) -> "记得带伞"
    // 雪天
    weatherCode == 75 -> "大雪纷飞，注意保暖"
    weatherCode == 86 -> "大阵雪，减少出行"
    isSnowy(weatherCode) -> "雨雪天气，注意保暖"
    // 温度
    temp < 0f -> "严寒天气，注意防冻"
    temp in 0f..5f -> "天气寒冷，注意保暖"
    temp in 5f..10f -> "气温较低，注意保暖"
    temp in 10f..15f -> "天气偏凉，适当添衣"
    temp in 15f..20f -> "气温舒适"
    temp in 20f..28f -> "适合出行"
    temp in 28f..33f -> "天气炎热，注意防暑"
    temp in 33f..35f -> "高温天气，减少户外活动"
    temp > 35f -> "极端高温，避免外出"
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

private var lastWeatherFetchTime = 0L
private var cachedWeather: WeatherData? = null
private const val WEATHER_REFRESH_INTERVAL = 2 * 60 * 1000L // 2分钟

private data class WeatherData(
    val temperature: Float = Float.NaN,
    val weatherCode: Int = -1,
    val sunset: String = "",
    val sunrise: String = "",
    val loaded: Boolean = false
) {
    fun isNight(): Boolean {
        if (sunset.isBlank() || sunrise.isBlank()) return false
        val now = LocalTime.now()
        val sunsetTime = parseTime(sunset.substringAfter("T")) ?: return false
        val sunriseTime = parseTime(sunrise.substringAfter("T")) ?: return false
        return now.isAfter(sunsetTime) || now.isBefore(sunriseTime)
    }
}

@Composable
private fun rememberWeather(): WeatherData {
    var weather by remember { mutableStateOf(cachedWeather ?: WeatherData()) }
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        if (now - lastWeatherFetchTime < WEATHER_REFRESH_INTERVAL && cachedWeather != null) {
            weather = cachedWeather!!
            return@LaunchedEffect
        }
        lastWeatherFetchTime = now
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=23.585&longitude=116.459" +
                    "&current=temperature_2m,weathercode" +
                    "&daily=sunset,sunrise" +
                    "&timezone=Asia/Shanghai" +
                    "&forecast_days=1"
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
                    @Suppress("UNCHECKED_CAST")
                    val daily = json["daily"] as? Map<String, Any> ?: return@use
                    @Suppress("UNCHECKED_CAST")
                    val sunsetList = daily["sunset"] as? List<String> ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val sunriseList = daily["sunrise"] as? List<String> ?: emptyList()
                    val sunset = sunsetList.firstOrNull() ?: ""
                    val sunrise = sunriseList.firstOrNull() ?: ""
                    val newData = WeatherData(temp, code, sunset, sunrise, true)
                    cachedWeather = newData
                    weather = newData
                }
            } catch (_: Exception) {
                val errorData = WeatherData(loaded = true)
                cachedWeather = errorData
                weather = errorData
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
                        if (minutes >= 60) {
                            val hours = minutes / 60
                            val mins = minutes % 60 + 1
                            if (mins >= 60) "还剩 ${hours + 1}小时"
                            else "还剩 ${hours}小时${mins}分钟"
                        } else {
                            "还剩 ${minutes + 1} 分钟"
                        }
                    } else ""
                }
                next != null -> {
                    val startStr = sectionTimes[next.startSection]?.split("-")?.firstOrNull() ?: ""
                    val start = parseTime(startStr)
                    if (start != null) {
                        val minutes = java.time.Duration.between(now, start).toMinutes()
                        if (minutes >= 60) {
                            val hours = minutes / 60
                            val mins = minutes % 60 + 1
                            if (mins >= 60) "${hours + 1}小时后"
                            else "${hours}小时${mins}分钟后"
                        } else {
                            "${minutes + 1} 分钟后"
                        }
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

private fun getGreeting(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..6 -> "早安"
        in 7..8 -> "早上好"
        in 9..11 -> "上午好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..19 -> "傍晚好"
        in 20..22 -> "晚上好"
        else -> "夜深了"
    }
}

private fun generateSmartTip(
    courses: List<Course>,
    tomorrowCourses: List<Course>,
    sectionTimes: Map<Int, String>,
    morningSections: Int,
    afternoonSections: Int
): String? {
    val now = LocalTime.now()
    val ranges = buildCourseTimeRanges(courses, sectionTimes)
    val tomorrowRanges = buildCourseTimeRanges(tomorrowCourses, sectionTimes)
    if (ranges.isEmpty()) {
        val greeting = getGreeting()
        val hour = now.hour
        val tomorrowCount = tomorrowCourses.size
        val tomorrowInfo = if (tomorrowCount > 0) {
            val firstCourse = tomorrowRanges.firstOrNull()?.course
            when {
                firstCourse != null && hour in 20..23 -> "，明天有 $tomorrowCount 节课，${firstCourse.name}是第一节课"
                hour in 20..23 -> "，明天有 $tomorrowCount 节课"
                else -> ""
            }
        } else if (hour in 20..23) {
            "，明天也没课"
        } else ""
        return when (hour) {
            in 6..8 -> "$greeting，今天没有课，可以睡个懒觉$tomorrowInfo"
            in 9..11 -> "$greeting，今天没有课，自由安排吧$tomorrowInfo"
            in 12..14 -> "$greeting，今天下午也没课，好好享受$tomorrowInfo"
            in 14..17 -> "$greeting，今天没课，做点自己想做的事$tomorrowInfo"
            in 18..19 -> "$greeting，今天没有课，可以放松一下$tomorrowInfo"
            in 20..22 -> "$greeting，今天没课，早点休息养精蓄锐$tomorrowInfo"
            else -> "$greeting，今天没有课$tomorrowInfo"
        }
    }

    val ongoing = ranges.find { now in it.start..it.end }
    val next = ranges.find { now.isBefore(it.start) }
    val prev = ranges.lastOrNull { now.isAfter(it.end) }

    val eveningCount = courses.count { it.startSection > morningSections + afternoonSections }
    val afternoonCount = courses.count { it.startSection in (morningSections + 1)..(morningSections + afternoonSections) }
    val totalCount = courses.size
    val completedCount = ranges.count { now.isAfter(it.end) }

    return when {
        ongoing != null -> {
            val remaining = java.time.Duration.between(now, ongoing.end).toMinutes()
            val nextAfter = ranges.find { it.start > ongoing.end }
            val gap = nextAfter?.let { java.time.Duration.between(ongoing.end, it.start).toMinutes() }
            val course = ongoing.course
            when {
                remaining <= 1 -> "马上就要下课了，再坚持一下"
                remaining <= 3 -> "还有 $remaining 分钟，快下课了"
                remaining <= 5 -> "还有 $remaining 分钟下课"
                remaining <= 10 -> "${course.name} 还有 $remaining 分钟，认真听讲"
                remaining <= 15 -> "认真听讲，${course.name} 还有 $remaining 分钟"
                remaining <= 30 -> "正在上${course.name}，还有 $remaining 分钟"
                gap != null && gap <= 3 -> "下课只有 $gap 分钟，抓紧休息"
                gap != null && gap <= 10 -> "下课后休息 $gap 分钟"
                gap != null && gap <= 15 -> "下课后有 $gap 分钟休息时间"
                else -> "正在上${course.name}"
            }
        }

        prev != null && next != null -> {
            val breakMinutes = java.time.Duration.between(prev.end, next.start).toMinutes()
            val nextCourse = next.course
            when {
                breakMinutes <= 1 -> "马上开始${nextCourse.name}"
                breakMinutes <= 3 -> "还有 $breakMinutes 分钟上${nextCourse.name}"
                breakMinutes <= 5 -> "还有 $breakMinutes 分钟，准备上${nextCourse.name}"
                breakMinutes <= 10 -> "课间休息中，下一节是${nextCourse.name}"
                breakMinutes <= 15 -> "休息一下，$breakMinutes 分钟后上${nextCourse.name}"
                breakMinutes <= 20 -> "还有 $breakMinutes 分钟，可以去${nextCourse.classroom}"
                breakMinutes <= 30 -> "休息时间还剩 $breakMinutes 分钟"
                breakMinutes <= 60 -> "休息中，$breakMinutes 分钟后${nextCourse.name}"
                breakMinutes in 61..120 -> "距${nextCourse.name}还有 $breakMinutes 分钟"
                else -> "距${nextCourse.name}还有 $breakMinutes 分钟，时间充裕"
            }
        }

        prev != null && next == null -> {
            val greeting = getGreeting()
            val hour = now.hour
            val tomorrowCount = tomorrowCourses.size
            val tomorrowInfo = if (tomorrowCount > 0 && hour >= 21) {
                val firstCourse = tomorrowRanges.firstOrNull()?.course
                if (firstCourse != null) "，明天有 $tomorrowCount 节课，${firstCourse.name}是第一节课"
                else "，明天有 $tomorrowCount 节课"
            } else if (tomorrowCount == 0 && hour >= 21) {
                "，明天没课"
            } else ""
            when {
                completedCount == totalCount && hour >= 22 -> "$greeting，今天 $totalCount 节课都上完了，早点休息$tomorrowInfo"
                completedCount == totalCount && hour in 18..21 -> "$greeting，今天 $totalCount 节课都上完了，辛苦了$tomorrowInfo"
                completedCount == totalCount -> "$greeting，今天 $totalCount 节课都上完了$tomorrowInfo"
                completedCount > 0 && hour >= 22 -> "$greeting，已经上了 $completedCount/$totalCount 节课，早点休息$tomorrowInfo"
                completedCount > 0 -> "$greeting，已经上了 $completedCount/$totalCount 节课$tomorrowInfo"
                afternoonCount > 0 && hour in 12..14 -> "$greeting，下午还有 $afternoonCount 节课"
                eveningCount > 0 && hour in 14..17 -> "$greeting，晚上还有 $eveningCount 节课"
                else -> "$greeting，今天还有 $totalCount 节课"
            }
        }

        next != null -> {
            val minutes = java.time.Duration.between(now, next.start).toMinutes()
            val nextCourse = next.course
            val greeting = getGreeting()
            when {
                minutes > 180 -> "$greeting，今天共 $totalCount 节课"
                minutes in 121..180 -> "$greeting，还有 $minutes 分钟上${nextCourse.name}"
                minutes in 61..120 -> "$greeting，还有 $minutes 分钟上${nextCourse.name}"
                minutes in 30..60 -> "$greeting，还有 $minutes 分钟，可以准备出发了"
                minutes in 15..29 -> "还有 $minutes 分钟上${nextCourse.name}，该出发了"
                minutes in 10..14 -> "还有 $minutes 分钟，准备去${nextCourse.classroom}"
                minutes in 5..9 -> "还有 $minutes 分钟，${nextCourse.name}要开始了"
                minutes in 2..4 -> "快 $minutes 分钟了，抓紧时间"
                else -> "马上要上${nextCourse.name}了"
            }
        }

        else -> "美好的一天开始了"
    }
}

// ===================== 主组件（紧凑排版） =====================

@Composable
fun TodayAssistantCard(
    courses: List<Course>,
    tomorrowCourses: List<Course> = emptyList(),
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
    val smartTip = remember(courses, tomorrowCourses, weather, sectionTimes, morningSections, afternoonSections, tick) {
        generateSmartTip(courses, tomorrowCourses, sectionTimes, morningSections, afternoonSections) ?: ""
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (weather.loaded && !weather.temperature.isNaN()) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(
                                id = getWeatherIconRes(weather.weatherCode, weather.isNight())
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val condition = getWeatherCondition(weather.weatherCode)
                        val advice = getWeatherAdvice(weather.temperature, weather.weatherCode)
                        Text(
                            text = "${weather.temperature.toInt()}°C $condition · $advice",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    } else {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(
                                id = R.drawable.icon_overcast
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "加载中...",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
        }
    }
}
