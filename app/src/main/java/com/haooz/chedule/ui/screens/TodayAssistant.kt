package com.haooz.chedule.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Geocoder
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import com.google.gson.Gson
import com.haooz.chedule.R
import com.haooz.chedule.data.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

// 中国天气网 type（中文）→ 图标
private fun getWeatherIconRes(type: String, isNight: Boolean = false): Int = when {
    type.contains("冰雹") || type.contains("雷") -> R.drawable.icon_t_storm
    type.contains("雾") || type.contains("霾") || type.contains("浮尘") ||
        type.contains("扬沙") || type.contains("沙尘") -> R.drawable.icon_float_dirt
    type.contains("冻雨") -> R.drawable.icon_ice_rain
    type.contains("雨夹雪") -> R.drawable.icon_light_snow
    type.contains("暴雨") || type.contains("大雨") -> R.drawable.icon_heavy_rain
    type.contains("中雨") -> R.drawable.icon_moderate_rain
    type.contains("小雨") || type.contains("阵雨") || type.contains("雨") -> R.drawable.icon_light_rain
    type.contains("暴雪") || type.contains("大雪") -> R.drawable.icon_heavy_snow
    type.contains("中雪") -> R.drawable.icon_moderate_snow
    type.contains("小雪") || type.contains("雪") -> R.drawable.icon_light_snow
    type.contains("阴") -> R.drawable.icon_overcast
    type.contains("多云") -> if (isNight) R.drawable.icon_cloudy_night else R.drawable.icon_cloudy
    type.contains("晴") -> if (isNight) R.drawable.icon_sunny_night else R.drawable.icon_sunny
    else -> if (isNight) R.drawable.icon_sunny_night else R.drawable.icon_sunny
}

// 阴影用图标 alpha 蒙版模糊，贴合轮廓（半透明图标不透出圆底边界）
@Composable
private fun WeatherIcon(
    resourceId: Int,
    size: Dp,
    shadowColor: Color,
    shadowRadius: Dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconPx = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val radiusPx = with(density) { shadowRadius.toPx() }.coerceAtLeast(1f)
    // 外溢边距，避免阴影被画布裁切
    val marginPx = (radiusPx * 1.1f).roundToInt().coerceAtLeast(2)
    val totalPx = iconPx + marginPx * 2
    val shadowArgb = shadowColor.toArgb()
    val shadowBitmap = remember(resourceId, totalPx, radiusPx, shadowArgb) {
        createWeatherShadowBitmap(
            source = renderWeatherIconBitmap(context, resourceId, totalPx, marginPx),
            radiusPx = radiusPx,
            shadowArgb = shadowArgb
        )
    }
    val iconBitmap = remember(resourceId, totalPx, marginPx) {
        renderWeatherIconBitmap(context, resourceId, totalPx, marginPx)
    }
    val totalDp = with(density) { totalPx.toDp() }
    Canvas(modifier = Modifier.size(totalDp)) {
        drawImage(shadowBitmap.asImageBitmap())
        drawImage(iconBitmap.asImageBitmap())
    }
}

// 图标居中渲染到带边距的 ARGB 位图，边距供阴影外溢
private fun renderWeatherIconBitmap(context: Context, resourceId: Int, totalPx: Int, insetPx: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(context, resourceId)
    val bitmap = createBitmap(totalPx, totalPx)
    if (drawable != null) {
        val canvas = Canvas(bitmap)
        drawable.setBounds(insetPx, insetPx, totalPx - insetPx, totalPx - insetPx)
        drawable.draw(canvas)
    }
    return bitmap
}

// 由图标位图生成贴合轮廓的模糊阴影
private fun createWeatherShadowBitmap(source: Bitmap, radiusPx: Float, shadowArgb: Int): Bitmap {
    val alphaMask = createBitmap(source.width, source.height, Bitmap.Config.ALPHA_8)
    Canvas(alphaMask).drawBitmap(source, 0f, 0f, null)
    val shadow = createBitmap(source.width, source.height)
    Canvas(shadow).drawBitmap(
        alphaMask,
        0f,
        0f,
        Paint().apply {
            color = shadowArgb
            maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
        }
    )
    return shadow
}

// 供设置页作废缓存，下次进入今日页按新设置重拉
fun invalidateWeatherCache() {
    lastWeatherFetchTime = 0L
    cachedWeather = null
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

private fun parseTime(timeStr: String): LocalTime? {
    return try {
        LocalTime.parse(timeStr, TIME_FORMATTER)
    } catch (_: Exception) {
        null
    }
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

private var lastWeatherFetchTime = 0L
private var cachedWeather: WeatherData? = null
private const val WEATHER_REFRESH_INTERVAL = 2 * 60 * 1000L

// assets/city_code.json 懒加载后进程内缓存
private var cityCodeMap: Map<String, String>? = null
private val cityCodeMapLock = Any()

private fun getCityCodeMap(context: Context): Map<String, String> {
    cityCodeMap?.let { return it }
    synchronized(cityCodeMapLock) {
        cityCodeMap?.let { return it }
        val map = mutableMapOf<String, String>()
        try {
            context.assets.open("city_code.json").bufferedReader().use { reader ->
                @Suppress("UNCHECKED_CAST")
                val root = Gson().fromJson(reader.readText(), Map::class.java) as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val provinces = root?.get("城市代码") as? List<Map<String, Any>>
                provinces?.forEach { province ->
                    @Suppress("UNCHECKED_CAST")
                    (province["市"] as? List<Map<String, Any>>)?.forEach { city ->
                        val name = city["市名"] as? String
                        val code = city["编码"] as? String
                        if (!name.isNullOrBlank() && !code.isNullOrBlank()) map[name] = code
                    }
                }
            }
        } catch (_: Exception) { }
        cityCodeMap = map
        return map
    }
}

private data class WeatherData(
    val temperature: Float = Float.NaN,
    val weatherType: String = "",
    val sunset: String = "",
    val sunrise: String = "",
    val notice: String = "",
    val loaded: Boolean = false,
    val needsLocation: Boolean = false,
    val apiError: Boolean = false
) {
    fun isNight(): Boolean {
        if (sunset.isBlank() || sunrise.isBlank()) return false
        val now = LocalTime.now()
        val sunsetTime = parseTime(sunset.substringAfter("T")) ?: return false
        val sunriseTime = parseTime(sunrise.substringAfter("T")) ?: return false
        return now.isAfter(sunsetTime) || now.isBefore(sunriseTime)
    }
}

// GPS 优先，回退 NETWORK；无权限/未开定位/无记录返回 null
@Suppress("MissingPermission")
private fun getLastKnownLocation(context: Context, useLocation: Boolean): android.location.Location? {
    if (!useLocation) return null
    val fineGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) return null
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val gps = if (fineGranted && lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
        val net = if (coarseGranted && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
        gps ?: net
    } catch (_: Exception) {
        null
    }
}

// 记录上次定位，供关闭定位权限后回退
private fun saveLastLocation(
    context: Context,
    name: String,
    cityCode: String? = null,
    lngLat: String? = null
) {
    context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE).edit {
        putString("last_location_name", name)
        if (cityCode != null) putString("last_city_code", cityCode)
        if (lngLat != null) putString("last_lnglat", lngLat)
    }
}

// 上次定位经纬度（无权限时彩云回退用）
private fun readLastLngLat(context: Context): Pair<Double, Double>? {
    val s = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        .getString("last_lnglat", null) ?: return null
    val parts = s.split(",")
    if (parts.size != 2) return null
    val lng = parts[0].toDoubleOrNull() ?: return null
    val lat = parts[1].toDoubleOrNull() ?: return null
    return Pair(lng, lat)
}

// 定位 → Geocoder → citykey；无定位时回退上次 citykey
private fun resolveCityCode(context: Context, useLocation: Boolean): String? {
    val map = getCityCodeMap(context)
    val loc = getLastKnownLocation(context, useLocation)
    if (loc == null) {
        return context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            .getString("last_city_code", null)
    }
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
        val addr = addresses?.firstOrNull()
        // locality > subAdminArea > adminArea（直辖市兜底）
        val candidates = listOfNotNull(
            addr?.locality, addr?.subAdminArea, addr?.adminArea
        ).filter { it.isNotBlank() }
        for (raw in candidates) {
            val cleaned = raw.removeSuffix("市")
                .removeSuffix("地区").removeSuffix("自治州").removeSuffix("盟")
            // 直辖市 locality 可能是区名，剥「区」后也能匹配区码
            val cleanedDistrict = if (cleaned.endsWith("区") && cleaned.length > 2)
                cleaned.removeSuffix("区") else cleaned
            val code = map[cleanedDistrict] ?: map[cleaned] ?: map[raw]
            if (code != null) {
                saveLastLocation(context, raw, cityCode = code)
                return code
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

// 彩云源取经纬度；无定位时回退上次经纬度
private fun resolveCoordinates(context: Context, useLocation: Boolean): Pair<Double, Double>? {
    val loc = getLastKnownLocation(context, useLocation)
    if (loc == null) return readLastLngLat(context)
    val lng = loc.longitude
    val lat = loc.latitude
    val name = try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        val addr = addresses?.firstOrNull()
        listOfNotNull(addr?.locality, addr?.subAdminArea, addr?.adminArea)
            .firstOrNull { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
    saveLastLocation(context, name ?: "%.2f,%.2f".format(lat, lng), lngLat = "$lng,$lat")
    return Pair(lng, lat) // 彩云 URL 经度在前
}

@Composable
private fun rememberWeather(): Triple<WeatherData, Boolean, () -> Unit> {
    val context = LocalContext.current
    val weatherPrefs = remember { context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE) }
    val weatherSource = weatherPrefs.getString("weather_source", "caiyun") ?: "caiyun"

    var weather by remember { mutableStateOf(cachedWeather ?: WeatherData()) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants.values.any { it }
        if (hasLocationPermission) lastWeatherFetchTime = 0L // 授权后强制刷新
    }

    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(hasLocationPermission, weatherSource, refreshTrigger) {
        val now = System.currentTimeMillis()
        if (now - lastWeatherFetchTime < WEATHER_REFRESH_INTERVAL && cachedWeather != null) {
            weather = cachedWeather!!
            return@LaunchedEffect
        }
        lastWeatherFetchTime = now
        withContext(Dispatchers.IO) {
            when (weatherSource) {
                "caiyun" -> {
                    val coords = resolveCoordinates(context, hasLocationPermission)
                    if (coords == null) {
                        weather = WeatherData(needsLocation = true, loaded = true)
                        return@withContext
                    }
                    try {
                        val (lng, lat) = coords
                        val url = "https://v1.apizero.cn/api/weather?location=$lng,$lat"
                        val request = Request.Builder().url(url).build()
                        httpClient.newCall(request).execute().use { resp ->
                            val body = resp.body?.string() ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val json = Gson().fromJson(body, Map::class.java) as? Map<String, Any> ?: return@use
                            val code = (json["code"] as? Number)?.toInt() ?: -1
                            if (code != 0) {
                                val errorData = WeatherData(loaded = true, apiError = true)
                                cachedWeather = errorData
                                weather = errorData
                                return@use
                            }
                            @Suppress("UNCHECKED_CAST")
                            val data = json["data"] as? Map<String, Any> ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val summary = data["summary"] as? Map<String, Any> ?: return@use
                            val temp = (summary["temperature"] as? Number)?.toFloat() ?: Float.NaN
                            val type = (summary["skycon"] as? String).orEmpty()
                            val notice = (data["forecast_keypoint"] as? String).orEmpty()
                            @Suppress("UNCHECKED_CAST")
                            val daily = data["daily"] as? Map<String, Any> ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val astroList = daily["astro"] as? List<Map<String, Any>> ?: emptyList()
                            val astro = astroList.firstOrNull() ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val sunriseObj = astro["sunrise"] as? Map<String, Any>
                            @Suppress("UNCHECKED_CAST")
                            val sunsetObj = astro["sunset"] as? Map<String, Any>
                            val sunrise = (sunriseObj?.get("time") as? String).orEmpty()
                            val sunset = (sunsetObj?.get("time") as? String).orEmpty()
                            val newData = WeatherData(temp, type, sunset, sunrise, notice, true)
                            cachedWeather = newData
                            weather = newData
                        }
                    } catch (_: Exception) {
                        val errorData = WeatherData(loaded = true, apiError = true)
                        cachedWeather = errorData
                        weather = errorData
                    }
                }
                else -> {
                    // 中国天气网：定位 → citykey → 拉取
                    val cityCode = resolveCityCode(context, hasLocationPermission)
                    if (cityCode == null) {
                        weather = WeatherData(needsLocation = true, loaded = true)
                        return@withContext
                    }
                    try {
                        val url = "http://t.weather.itboy.net/api/weather/city/$cityCode"
                        val request = Request.Builder().url(url).build()
                        httpClient.newCall(request).execute().use { resp ->
                            val body = resp.body?.string() ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val json = Gson().fromJson(body, Map::class.java) as? Map<String, Any> ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val data = json["data"] as? Map<String, Any> ?: return@use
                            val wendu = (data["wendu"] as? String).orEmpty()
                            val temp = wendu.substringBefore("℃").trim().toFloatOrNull() ?: Float.NaN
                            @Suppress("UNCHECKED_CAST")
                            val forecast = data["forecast"] as? List<Map<String, Any>> ?: emptyList()
                            val today = forecast.firstOrNull() ?: return@use
                            val type = (today["type"] as? String).orEmpty()
                            val sunrise = (today["sunrise"] as? String).orEmpty()
                            val sunset = (today["sunset"] as? String).orEmpty()
                            val notice = (today["notice"] as? String).orEmpty()
                            val newData = WeatherData(temp, type, sunset, sunrise, notice, true)
                            cachedWeather = newData
                            weather = newData
                        }
                    } catch (_: Exception) {
                        val errorData = WeatherData(loaded = true, apiError = true)
                        cachedWeather = errorData
                        weather = errorData
                    }
                }
            }
        }
    }
    return Triple(weather, hasLocationPermission) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            lastWeatherFetchTime = 0L
            weather = WeatherData(loaded = false)
            refreshTrigger++
        }
    }
}

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
    // 时间区间只随 courses/sectionTimes 重算，循环内复用；保持原顺序以兼容 first-match
    val ranges = remember(courses, sectionTimes) {
        buildCourseTimeRanges(courses, sectionTimes, sortByStart = false)
    }
    LaunchedEffect(ranges) {
        while (true) {
            val now = LocalTime.now()
            // 与 generateSmartTip 同一判定：开始含、结束不含，避免整点卡在「正在上课」
            val current = ranges.find { !now.isBefore(it.start) && now.isBefore(it.end) }
            val next = ranges.find { now.isBefore(it.start) }
            val message = when {
                current != null -> {
                    val minutes = ceilMinutesUntil(now, current.end)
                    "还剩 ${formatCountdownMinutes(minutes)}"
                }
                next != null -> {
                    val minutes = ceilMinutesUntil(now, next.start)
                    formatCountdownMinutes(minutes, trailing = "后")
                }
                courses.isEmpty() -> ""
                else -> ""
            }
            val newStatus = CourseStatus(current?.course, next?.course, message)
            // 文案未变化时不写状态，避免每秒重组
            if (newStatus != status) status = newStatus
            delay(1000L.milliseconds)
        }
    }
    return status
}

private data class CourseTimeRange(
    val course: Course,
    val start: LocalTime,
    val end: LocalTime
)

private fun buildCourseTimeRanges(
    courses: List<Course>,
    sectionTimes: Map<Int, String>,
    sortByStart: Boolean = true
): List<CourseTimeRange> {
    val ranges = courses.mapNotNull { course ->
        val startStr = course.getEffectiveStartTime(sectionTimes) ?: return@mapNotNull null
        val endStr = course.getEffectiveEndTime(sectionTimes) ?: return@mapNotNull null
        val start = parseTime(startStr) ?: return@mapNotNull null
        val end = parseTime(endStr) ?: return@mapNotNull null
        CourseTimeRange(course, start, end)
    }
    return if (sortByStart) ranges.sortedBy { it.start } else ranges
}

// 向上取整，与顶部倒计时、系统岛倒计时共用同一套「剩余分钟」口径
private fun ceilMinutesUntil(from: LocalTime, to: LocalTime): Int {
    val remain = java.time.Duration.between(from, to).toMillis()
    if (remain <= 0L) return 0
    return ((remain + 59_999L) / 60_000L).toInt()
}

private fun formatCountdownMinutes(minutes: Int, trailing: String = ""): String {
    val hours = minutes / 60
    val mins = minutes % 60
    val body = when {
        hours > 0 && mins > 0 -> "${hours}小时${mins}分钟"
        hours > 0 -> "${hours}小时"
        else -> "${minutes} 分钟"
    }
    return body + trailing
}

// 稳定轮换：同一钟点+时间桶内固定一条，避免每秒乱跳
private fun pickTip(variants: List<String>, seed: Int): String {
    if (variants.isEmpty()) return ""
    val idx = ((seed % variants.size) + variants.size) % variants.size
    return variants[idx]
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

    fun roomOf(course: Course): String =
        course.classroom.ifBlank { "教室待定" }

    fun tomorrowNote(): String {
        val hour = now.hour
        if (hour < 20) return ""
        val n = tomorrowCourses.size
        if (n == 0) return " · 明天没课，可以睡到自然醒"
        val first = tomorrowRanges.firstOrNull()?.course
        val firstLine = if (first != null) "${first.name} 开头" else "第一节待定"
        return " · 明天 $n 节，$firstLine"
    }

    if (ranges.isEmpty()) {
        // 时间残缺时避免误报「今天没课」
        if (courses.isNotEmpty()) {
            return pickTip(
                listOf(
                    "今天有 ${courses.size} 节课，但节次时间没填全，先去课表补齐再排日程",
                    "课表里有 ${courses.size} 节，时间字段是空的，补完才好倒计时",
                    "有课，没点——节次时间残缺，去课表修一下"
                ),
                seed = now.hour
            )
        }
        val hour = now.hour
        val note = tomorrowNote()
        val pool = when (hour) {
            in 5..8 -> listOf(
                "今天没课，闹钟可以往后挪一格",
                "没课的早晨，床比计划表靠谱",
                "今天空着，多睡可以，睡到中午就是另一回事了"
            )
            in 9..11 -> listOf(
                "今天没课，上午自己排，别一睁眼就交给了床",
                "上午空档，图书馆有座，你有借口",
                "没课的上午很贵，刷完就没了"
            )
            in 12..13 -> listOf(
                "下午也没课，吃饭按点，别用零食糊弄",
                "中午自由，胃要照顾好，下午才站得住",
                "没课的饭点，是你今天最实在的自由"
            )
            in 14..17 -> listOf(
                "今天没课，做点正事，也别把自己排崩",
                "下午阳光很好，适合出门，也适合焦虑——你选",
                "空着的下午，进度条容易一直是 0%"
            )
            in 18..21 -> listOf(
                "今天没课，适当放空，别熬到明天一起还债",
                "晚上自由，手机会替你安排——你要不要抢回来",
                "没课的夜更容易晚睡，自己收着点"
            )
            in 22..23 -> listOf(
                "今天没课，早点收，夜里的时间不增值",
                "该睡了，自由日的尽头通常是熬夜",
                "收工吧，明天有没有课都得起床面对自己"
            )
            else -> listOf("今天没有课")
        }
        return pickTip(pool, seed = hour) + note
    }

    val ongoing = ranges.find { now >= it.start && now < it.end }
    val next = ranges.find { now.isBefore(it.start) }
    val prev = ranges.lastOrNull { now.isAfter(it.end) }

    val eveningCount = courses.count {
        it.periodIndex(sectionTimes, morningSections, afternoonSections) == Course.PERIOD_EVENING
    }
    val afternoonCount = courses.count {
        it.periodIndex(sectionTimes, morningSections, afternoonSections) == Course.PERIOD_AFTERNOON
    }
    val totalCount = courses.size
    val completedCount = ranges.count { now.isAfter(it.end) }

    return when {
        // 正在上课：剩余 + 连堂，多套冷静说法
        ongoing != null -> {
            val remaining = ceilMinutesUntil(now, ongoing.end)
            val nextAfter = ranges.find { it.start > ongoing.end }
            val gap = nextAfter?.let { ceilMinutesUntil(ongoing.end, it.start) }
            val gapPart = when {
                gap != null && gap <= 3 -> " · 连堂只歇 $gap 分钟，东西别收太彻底"
                gap != null && gap <= 10 -> " · 下课后仅 $gap 分钟，补给要快"
                gap != null && gap <= 15 -> " · 下课后还有 $gap 分钟，够喘口气"
                gap != null && gap <= 30 -> " · 下课后有 $gap 分钟，可离开教室"
                else -> ""
            }
            val pool = when {
                remaining <= 1 -> listOf(
                    "马上收尾",
                    "到点边缘，准备收势",
                    "最后几十秒，别提前弹射"
                )
                remaining <= 5 -> listOf(
                    "还剩 $remaining 分钟，准备收势",
                    "还剩 $remaining 分钟，书包可以先热身",
                    "还剩 $remaining 分钟，再撑一下就到"
                )
                remaining <= 15 -> listOf(
                    "还剩 $remaining 分钟，保持节奏",
                    "还剩 $remaining 分钟，后半段别掉线",
                    "还剩 $remaining 分钟，重点一般在这截"
                )
                remaining <= 45 -> listOf(
                    "还剩 $remaining 分钟",
                    "还剩 $remaining 分钟，按自己的步子走",
                    "还剩 $remaining 分钟，别提前进入下课模式"
                )
                else -> listOf(
                    "还剩 $remaining 分钟",
                    "还剩 $remaining 分钟，刚上不久，稳住",
                    "还剩 $remaining 分钟，后面还长，别透支注意力"
                )
            }
            pickTip(pool, seed = now.hour * 31 + remaining / 5) + gapPart
        }

        // 课间：剩余 + 去向，多套说法
        prev != null && next != null -> {
            val remainToNext = ceilMinutesUntil(now, next.start)
            val room = roomOf(next.course)
            val name = next.course.name
            val pool = when {
                remainToNext <= 1 -> listOf(
                    "马上上课，座位先落定",
                    "铃要响了，回座",
                    "零缓冲，直接进教室"
                )
                remainToNext <= 5 -> listOf(
                    "还剩 $remainToNext 分钟，该回座位了 · $room",
                    "还剩 $remainToNext 分钟 · $room，别压点",
                    "课间进入收尾 · $room，起身吧"
                )
                remainToNext <= 10 -> listOf(
                    "课间还剩 $remainToNext 分钟，别压点 · $room",
                    "还剩 $remainToNext 分钟 · $room，$name 在等",
                    "课间还剩 $remainToNext 分钟，路线先想好 · $room"
                )
                remainToNext <= 20 -> listOf(
                    "课间还剩 $remainToNext 分钟，下节去 $room",
                    "休息还剩 $remainToNext 分钟 · $name · $room",
                    "还剩 $remainToNext 分钟，够缓一下，别开新局"
                )
                remainToNext <= 45 -> listOf(
                    "休息还剩 $remainToNext 分钟，下节 $room，别走远",
                    "长课间还剩 $remainToNext 分钟 · $name",
                    "还剩 $remainToNext 分钟，可以回血，别掉段"
                )
                else -> listOf(
                    "距下节还有 $remainToNext 分钟 · $room，按自己节奏来",
                    "大空档还剩 $remainToNext 分钟 · $name · $room",
                    "还有 $remainToNext 分钟才上课，时间归你安排 · $room"
                )
            }
            pickTip(pool, seed = now.hour * 31 + remainToNext / 5)
        }

        // 今天课已上完
        prev != null && next == null -> {
            val pool = when {
                completedCount >= totalCount -> listOf(
                    "今天 $totalCount 节已收官，知识点自己认领，没认领的算漏网",
                    "今日 $totalCount 节通关，作业群才是下一关",
                    "今天 $totalCount 节结束，人可以放，进度自己盯"
                )
                else -> listOf(
                    "已上完 $completedCount/$totalCount 节，后面的按原计划走",
                    "进度 $completedCount/$totalCount，别在收尾段掉链子",
                    "还剩 ${totalCount - completedCount} 节，稳着走完"
                )
            }
            pickTip(pool, seed = now.hour) + tomorrowNote()
        }

        // 第一节课还没开始
        next != null -> {
            val minutes = ceilMinutesUntil(now, next.start)
            val room = roomOf(next.course)
            val name = next.course.name
            val hour = now.hour
            val dayPlanPool = when {
                totalCount >= 5 -> listOf(
                    "今天共 $totalCount 节，强度不低",
                    "满课向：$totalCount 节，分配好体力",
                    "今日 $totalCount 节，别在上午就把电用完"
                )
                totalCount >= 3 -> listOf(
                    "今天 $totalCount 节，正常强度",
                    "今日 $totalCount 节，节奏正常",
                    "共 $totalCount 节，按课表走就行"
                )
                else -> listOf(
                    "今天就 $totalCount 节，别太飘",
                    "课不多，$totalCount 节，省着点浪费",
                    "轻量日：$totalCount 节，自由也别全刷掉"
                )
            }
            val dayPlan = pickTip(dayPlanPool, seed = hour)
            val pool = when {
                minutes > 180 -> listOf(
                    "$dayPlan · 第一节还有 ${minutes / 60} 小时以上",
                    "$dayPlan · 离上课还早，先按自己的节奏来",
                    "$dayPlan · 还有大把时间，别一开局就瘫"
                )
                minutes > 90 -> listOf(
                    "$dayPlan · 还有 $minutes 分钟，$room",
                    "$dayPlan · 距 $name 还有 $minutes 分钟",
                    "$dayPlan · 还有 $minutes 分钟，不用急，也别完全躺"
                )
                minutes > 30 -> listOf(
                    "$dayPlan · 还有 $minutes 分钟，可以准备了 · $room",
                    "还有 $minutes 分钟 · $name · $room，东西可以理了",
                    "$dayPlan · 还有 $minutes 分钟，预热一下状态"
                )
                minutes > 15 -> listOf(
                    "还有 $minutes 分钟，该动身了 · $room",
                    "还有 $minutes 分钟 · $name，路线可以定了",
                    "还剩 $minutes 分钟 · $room，别把缓冲用光"
                )
                minutes > 5 -> listOf(
                    "还有 $minutes 分钟 · $room，路上别磨",
                    "还剩 $minutes 分钟 · $name，匀速过去就行",
                    "还有 $minutes 分钟 · $room，现在走刚好"
                )
                minutes >= 1 -> listOf(
                    "还有 $minutes 分钟 · $room，保持匀速",
                    "还剩 $minutes 分钟 · $room，别冲刺也别散步",
                    "还有 $minutes 分钟，$name，到了先落座"
                )
                else -> {
                    val periodHint = when {
                        afternoonCount > 0 && hour in 12..13 ->
                            " · 下午还有 $afternoonCount 节"
                        eveningCount > 0 && hour in 14..17 ->
                            " · 晚上还有 $eveningCount 节"
                        else -> ""
                    }
                    listOf(
                        "快进教室 · $room$periodHint",
                        "铃在响了 · $room$periodHint",
                        "直接进教室 · $name$periodHint"
                    )
                }
            }
            pickTip(pool, seed = hour * 31 + minutes / 15)
        }

        else -> pickTip(
            listOf(
                "今天有 $totalCount 节课，按课表走就行",
                "今日共 $totalCount 节，日程已排好",
                "课表就绪：$totalCount 节"
            ),
            seed = now.hour
        )
    }
}

@Composable
fun TodayAssistantCard(
    courses: List<Course>,
    tomorrowCourses: List<Course> = emptyList(),
    sectionTimes: Map<Int, String>,
    morningSections: Int,
    afternoonSections: Int,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    wallpaperBackdrop: com.kyant.backdrop.Backdrop? = null,
    blurRadius: Float = 0f,
    surfaceOpacity: Float
) {
    val (weather, hasLocationPermission, requestLocation) = rememberWeather()
    val courseStatus = rememberCourseStatus(courses, sectionTimes)
    var smartTip by remember { mutableStateOf("") }
    LaunchedEffect(courses, tomorrowCourses, sectionTimes, morningSections, afternoonSections) {
        while (true) {
            val newTip = generateSmartTip(
                courses, tomorrowCourses, sectionTimes, morningSections, afternoonSections
            ).orEmpty()
            // 与顶部倒计时同频轮询；文案未变化时不写状态，避免每秒重组
            if (newTip != smartTip) smartTip = newTip
            delay(1_000L.milliseconds)
        }
    }

    BlurCard(
        cornerRadius = 20.dp,
        wallpaperBackdrop = wallpaperBackdrop,
        blurRadius = blurRadius,
        surfaceOpacity = surfaceOpacity,
        showEdgeLight = wallpaperBackdrop != null && blurRadius > 0f,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val currentCourse = courseStatus.currentCourse
            val nextCourse = courseStatus.nextCourse

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
                // weight 限制左侧宽度，过长课程名换行，右侧剩余时间保持完整
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
            val displayLocation = if (showClassroom) location else ""
            val displayTeacher = if (showTeacher) teacher else ""
            if (displayLocation.isNotBlank() || displayTeacher.isNotBlank()) {
                Text(
                    text = buildString {
                        if (displayLocation.isNotBlank()) append(displayLocation)
                        if (displayLocation.isNotBlank() && displayTeacher.isNotBlank()) append(" | ")
                        if (displayTeacher.isNotBlank()) append(displayTeacher)
                    },
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                        WeatherIcon(
                            resourceId = getWeatherIconRes(weather.weatherType, weather.isNight()),
                            size = 23.dp,
                            shadowColor = Color.Black.copy(alpha = 0.24f),
                            shadowRadius = 5.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${weather.temperature.toInt()}°C ${weather.weatherType} · ${weather.notice}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    } else if (weather.needsLocation) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(
                                        id = R.drawable.ic_widget_location
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasLocationPermission) "无法获取位置信息·点击重试" else "需要定位权限·点击授权",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.clickable { requestLocation() }
                                )
                            }
                        }
                    } else if (weather.apiError) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(
                                        id = R.drawable.ic_widget_location
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "天气服务异常·点击重试",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.clickable { requestLocation() }
                                )
                            }
                        }
                    } else {
                        WeatherIcon(
                            resourceId = R.drawable.icon_overcast,
                            size = 23.dp,
                            shadowColor = Color.Black.copy(alpha = 0.24f),
                            shadowRadius = 5.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
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
