/** 今日助手 - 智能课程状态、天气提醒、时段提示 */
package com.haooz.chedule.ui.screens

// ===================== 天气工具 =====================

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
import androidx.compose.runtime.mutableLongStateOf
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

// 中国天气网 type 字段（中文）→ 图标资源
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

/**
 * 天气图标（贴合轮廓的模糊投影）。
 * 通过把图标位图的 alpha 蒙版用 BlurMaskFilter 模糊成阴影，
 * 使阴影严格跟随图标轮廓，避免半透明图标透出圆形底边界。
 */
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
    // 为模糊投影预留外溢边距，避免阴影被画布裁切
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

/** 将图标资源居中渲染到带边距的 ARGB 位图中，边距供模糊投影外溢 */
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

/** 由图标位图生成贴合其轮廓的模糊阴影位图 */
private fun createWeatherShadowBitmap(source: Bitmap, radiusPx: Float, shadowArgb: Int): Bitmap {
    // 1. 提取图标不透明区域的 alpha 蒙版
    val alphaMask = createBitmap(source.width, source.height, Bitmap.Config.ALPHA_8)
    Canvas(alphaMask).drawBitmap(source, 0f, 0f, null)
    // 2. 用蒙版画出纯色阴影，并做模糊，使阴影贴合轮廓
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

// 让外部（如设置页）能作废缓存，使下一次进入今日页时按新设置重新拉取
fun invalidateWeatherCache() {
    lastWeatherFetchTime = 0L
    cachedWeather = null
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

// 城市名 → 中国天气网 citykey 映射（从 assets/city_code.json 懒加载，进程内缓存）
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
    val needsLocation: Boolean = false
) {
    fun isNight(): Boolean {
        if (sunset.isBlank() || sunrise.isBlank()) return false
        val now = LocalTime.now()
        val sunsetTime = parseTime(sunset.substringAfter("T")) ?: return false
        val sunriseTime = parseTime(sunrise.substringAfter("T")) ?: return false
        return now.isAfter(sunsetTime) || now.isBefore(sunriseTime)
    }
}

// 共用：取最近一次已知位置。优先 GPS（需精确权限，精度高），回退 NETWORK（粗略即可）。无权限/未开定位/无记录都返回 null。
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

// 记录「上次定位」：城市名 + citykey/经纬度，供关闭定位权限后回退使用
private fun saveLastLocation(
    context: Context,
    name: String,
    cityCode: String? = null,
    lngLat: String? = null
) {
    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE).edit()
    prefs.putString("last_location_name", name)
    if (cityCode != null) prefs.putString("last_city_code", cityCode)
    if (lngLat != null) prefs.putString("last_lnglat", lngLat)
    prefs.apply()
}

// 读取上次定位的经纬度（无权限时彩云源回退用）。无记录返回 null。
private fun readLastLngLat(context: Context): Pair<Double, Double>? {
    val s = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        .getString("last_lnglat", null) ?: return null
    val parts = s.split(",")
    if (parts.size != 2) return null
    val lng = parts[0].toDoubleOrNull() ?: return null
    val lat = parts[1].toDoubleOrNull() ?: return null
    return Pair(lng, lat)
}

// 中国天气网源：城市名 → citykey。成功后记录「上次定位」。无权限/无实时定位时回退到上次定位的 citykey；仍无则返回 null。
private fun resolveCityCode(context: Context, useLocation: Boolean): String? {
    val map = getCityCodeMap(context)
    val loc = getLastKnownLocation(context, useLocation)
    if (loc == null) {
        // 无定位权限/无实时定位时，使用上次定位
        return context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            .getString("last_city_code", null)
    }
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
        val addr = addresses?.firstOrNull()
        // 候选城市名：locality(地级市) > subAdminArea > adminArea(直辖市兜底)
        val candidates = listOfNotNull(
            addr?.locality, addr?.subAdminArea, addr?.adminArea
        ).filter { it.isNotBlank() }
        for (raw in candidates) {
            val cleaned = raw.removeSuffix("市")
                .removeSuffix("地区").removeSuffix("自治州").removeSuffix("盟")
            // 直辖市的 locality 可能是"海淀区"这类区名，剥掉"区"也能匹配到对应区码
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

// 彩云天气源：取经纬度（lng, lat）。成功后记录「上次定位」。无权限/无实时定位时回退到上次定位的经纬度；仍无则返回 null。
private fun resolveCoordinates(context: Context, useLocation: Boolean): Pair<Double, Double>? {
    val loc = getLastKnownLocation(context, useLocation)
    // 无定位权限/无实时定位时，使用上次定位
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
    return Pair(lng, lat) // 彩云 URL 路径中经度在前
}

@Composable
private fun rememberWeather(): Pair<WeatherData, () -> Unit> {
    val context = LocalContext.current
    val weatherPrefs = remember { context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE) }
    val weatherSource = weatherPrefs.getString("weather_source", "itboy") ?: "itboy"

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
        if (hasLocationPermission) lastWeatherFetchTime = 0L // 授权后强制刷新一次
    }

    LaunchedEffect(hasLocationPermission, weatherSource) {
        val now = System.currentTimeMillis()
        if (now - lastWeatherFetchTime < WEATHER_REFRESH_INTERVAL && cachedWeather != null) {
            weather = cachedWeather!!
            return@LaunchedEffect
        }
        lastWeatherFetchTime = now
        withContext(Dispatchers.IO) {
            when (weatherSource) {
                "caiyun" -> {
                    // 彩云天气（apizero 免 Token 聚合源）：经纬度查询
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
                                val errorData = WeatherData(loaded = true)
                                cachedWeather = errorData
                                weather = errorData
                                return@use
                            }
                            @Suppress("UNCHECKED_CAST")
                            val data = json["data"] as? Map<String, Any> ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val summary = data["summary"] as? Map<String, Any> ?: return@use
                            val temp = (summary["temperature"] as? Number)?.toFloat() ?: Float.NaN
                            val type = (summary["skycon"] as? String).orEmpty() // 已是中文
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
                        val errorData = WeatherData(loaded = true)
                        cachedWeather = errorData
                        weather = errorData
                    }
                }
                else -> {
                    // 中国天气网源：定位 → citykey → 拉取
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
                        val errorData = WeatherData(loaded = true)
                        cachedWeather = errorData
                        weather = errorData
                    }
                }
            }
        }
    }
    return weather to {
        if (!hasLocationPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
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
                val startStr = course.getEffectiveStartTime(sectionTimes) ?: return@find false
                val endStr = course.getEffectiveEndTime(sectionTimes) ?: return@find false
                val start = parseTime(startStr) ?: return@find false
                val end = parseTime(endStr) ?: return@find false
                !now.isBefore(start) && !now.isAfter(end)
            }
            val next = courses.find { course ->
                val startStr = course.getEffectiveStartTime(sectionTimes) ?: return@find false
                val start = parseTime(startStr) ?: return@find false
                now.isBefore(start)
            }
            val message = when {
                current != null -> {
                    val endStr = current.getEffectiveEndTime(sectionTimes) ?: ""
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
                    val startStr = next.getEffectiveStartTime(sectionTimes) ?: ""
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
        val startStr = course.getEffectiveStartTime(sectionTimes) ?: return@mapNotNull null
        val endStr = course.getEffectiveEndTime(sectionTimes) ?: return@mapNotNull null
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
        // 今天有课程但时间信息不完整/无法解析，避免误报“今天没课”
        if (courses.isNotEmpty()) {
            return "今天有 ${courses.size} 节课，但课程时间残缺得跟你的人生规划似的，去课表补一下！"
        }
        val greeting = getGreeting()
        val hour = now.hour
        val tomorrowCount = tomorrowCourses.size
        val tomorrowInfo = if (tomorrowCount > 0) {
            val firstCourse = tomorrowRanges.firstOrNull()?.course
            when {
                firstCourse != null && hour in 20..23 -> "，明天${tomorrowCount}节，${firstCourse.name}带头，躲不掉"
                hour in 20..23 -> "，明天${tomorrowCount}节，别想逃"
                else -> ""
            }
        } else if (hour in 20..23) {
            "，明天没课，乐一下"
        } else ""
        return when (hour) {
            in 6..8 -> "$greeting，今天没课，放心睡，反正也没人指望你$tomorrowInfo"
            in 9..11 -> "$greeting，今天没课，自由安排——别自由到把一天废了就行$tomorrowInfo"
            in 12..14 -> "$greeting，今天下午也没课，好好享（摆）受（烂）吧$tomorrowInfo"
            in 14..17 -> "$greeting，今天没课，做点想做的事，别又想了一下午啥也没干$tomorrowInfo"
            in 18..19 -> "$greeting，今天没课，放松一下，别放松到最后还是又刷手机又焦虑$tomorrowInfo"
            in 20..22 -> "$greeting，今天没课，早点休息——你确定忍得住不摸手机？$tomorrowInfo"
            else -> "$greeting，今天没有课$tomorrowInfo"
        }
    }

    val ongoing = ranges.find { now >= it.start && now < it.end }
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
            when {
                // 临近下课且课间紧张时，优先提示赶场
                gap != null && gap <= 3 && remaining <= 15 -> "下课只有 $gap 分钟！赶场冲刺，慢了半节课都白听了"
                gap != null && gap <= 10 && remaining <= 15 -> "下课后只剩 $gap 分钟，赶紧补口血，别把魂放出去忘了回收"
                gap != null && gap <= 15 && remaining <= 15 -> "下课后有 $gap 分钟，喝口水，但别顺带把精神也放了"
                remaining <= 1 -> "1分钟下课！坐直点，别让老师看你如释重负的样子"
                remaining <= 3 -> "冲刺！还有 $remaining 分钟，别瘫桌上装尸体"
                remaining <= 5 -> "还有 $remaining 分钟下课，眼皮抬起来，撑住这最后一程"
                remaining <= 10 -> "还有 $remaining 分钟下课，坚持就是胜利——坚持不住也得装到底"
                remaining <= 15 -> "距下课 $remaining 分钟，笔记补完没？没补等死吧（开个玩笑……但真会死）"
                remaining <= 20 -> "还有 $remaining 分钟，喝口水缓一缓，别第十分钟就开始坐不住"
                remaining <= 30 -> "还剩 $remaining 分钟，专注点，你走神的对象又不给你发工资"
                remaining <= 45 -> "过半了，还有 $remaining 分钟，半场刚打完，别提前庆祝"
                remaining <= 60 -> "还有 $remaining 分钟，稳住我们能赢——至少口号不能输"
                remaining <= 75 -> "还有 $remaining 分钟，按自己的节奏来，但别把节奏定成睡着"
                remaining <= 90 -> "长课还有 $remaining 分钟，耐心点，老师讲得比你听得还累"
                remaining <= 105 -> "时间充裕，还有 $remaining 分钟，慢慢听，别把重点漏了"
                remaining <= 120 -> "刚开课，还有 $remaining 分钟，进入状态吧——别第一分钟就开始数秒表"
                // 超长课（>120 分钟）时，提前预告课间安排
                gap != null && gap <= 3 -> "下课后只有 $gap 分钟，提前收拾好东西，别聊着聊着就迟到了"
                gap != null && gap <= 10 -> "下课后休息 $gap 分钟，够你喝口水，别指望补个觉"
                gap != null && gap <= 15 -> "下课后有 $gap 分钟休息，抓紧眯会儿也行"
                else -> "认真听课，离下课还有 $remaining 分钟——认真不认真都是坐着听，不如装得像点"
            }
        }

        prev != null && next != null -> {
            val breakMinutes = java.time.Duration.between(prev.end, next.start).toMinutes()
            val nextClassroom = next.course.classroom
            when {
                breakMinutes <= 1 -> "下节课马上开始，快滚回座位！别卡着点挑战老师底线"
                breakMinutes <= 3 -> "还有 $breakMinutes 分钟，歇够没？够了就回座位，别磨"
                breakMinutes <= 5 -> "还有 $breakMinutes 分钟，把桌面收拾利索，别一摊烂桌面迎接下节课"
                breakMinutes <= 10 -> "课间 $breakMinutes 分钟，抓紧，别刷着刷着忘了上课"
                breakMinutes <= 15 -> "还有 $breakMinutes 分钟，回来吧，人到了魂也先归个位"
                breakMinutes <= 20 -> "还有 $breakMinutes 分钟，该出发去${nextClassroom}了，别磨"
                breakMinutes <= 30 -> "休息还剩 $breakMinutes 分钟，伸个懒腰，别把腰也一起睡没"
                breakMinutes <= 45 -> "课间 $breakMinutes 分钟，看看远方，别盯手机把眼盯瞎"
                breakMinutes <= 60 -> "休息 $breakMinutes 分钟，养精蓄锐，待会才好继续被虐"
                breakMinutes in 61..120 -> "大课间 $breakMinutes 分钟，够你睡个回笼，但别真睡，醒了自己都怕"
                else -> "距下节课还有 $breakMinutes 分钟，时间充裕到你能把手机充个电再接着玩"
            }
        }

        prev != null && next == null -> {
            val greeting = getGreeting()
            val hour = now.hour
            val tomorrowCount = tomorrowCourses.size
            val tomorrowInfo = if (tomorrowCount > 0 && hour >= 21) {
                val firstCourse = tomorrowRanges.firstOrNull()?.course
                if (firstCourse != null) "，明天${tomorrowCount}节，${firstCourse.name}带头，跑不掉"
                else "，明天${tomorrowCount}节，别想躲"
            } else if (tomorrowCount == 0 && hour >= 21) {
                "，明天没课，随便熬"
            } else ""
            when {
                completedCount == totalCount && hour >= 22 -> "$greeting，今天 $totalCount 节课终于上完了，奖励自己躺平，但别躺到天亮$tomorrowInfo"
                completedCount == totalCount && hour in 18..21 -> "$greeting，今天 $totalCount 节课都熬完了，辛苦的不止今天，还有昨晚你那顿夜$tomorrowInfo"
                completedCount == totalCount -> "$greeting，今天 $totalCount 节课总算上完了，债还清了$tomorrowInfo"
                completedCount > 0 && hour >= 22 -> "$greeting，已经上了 $completedCount/$totalCount 节，累了就睡，别拿'再刷一会'自欺欺人$tomorrowInfo"
                completedCount > 0 -> "$greeting，已上完 $completedCount/$totalCount 节，剩下那几节可得撑住，别逃$tomorrowInfo"
                afternoonCount > 0 && hour in 12..14 -> "$greeting，下午还有 $afternoonCount 节课，别被午饭骗得忘了"
                eveningCount > 0 && hour in 14..17 -> "$greeting，晚上还有 $eveningCount 节课，别天真地以为下午就解放了"
                else -> "$greeting，今天还有 $totalCount 节课，认命吧，跑不掉的"
            }
        }

        next != null -> {
            val minutes = java.time.Duration.between(now, next.start).toMinutes()
            val nextClassroom = next.course.classroom
            val greeting = getGreeting()
            when {
                minutes > 180 -> "$greeting，今天共 $totalCount 节课，安排得明明白白——明白你也得照样躺"
                minutes in 121..180 -> "$greeting，还有 $minutes 分钟才上课，够你从容准备，也够你从容拖延"
                minutes in 91..120 -> "$greeting，还有 $minutes 分钟上课，时间够用？是够你用手机用完"
                minutes in 61..90 -> "$greeting，还有 $minutes 分钟，翻翻书预习一下——别看了，就翻翻"
                minutes in 45..60 -> "$greeting，还有 $minutes 分钟，准备出发吧，别走到半路又折回来拿手机"
                minutes in 30..44 -> "$greeting，还有 $minutes 分钟，该收拾东西了，等下别怪课不等人"
                minutes in 15..29 -> "还有 $minutes 分钟上课，该出发啦，别到时候演一出'马上到'"
                minutes in 10..14 -> "还有 $minutes 分钟，动身去${nextClassroom}，别迟到了被老师眼神处刑"
                minutes in 5..9 -> "还有 $minutes 分钟上课，跑起来，你现在的速度决定你的体面"
                minutes in 2..4 -> "$minutes 分钟倒计时！冲！没到教室就等着全校看你表演迟到"
                else -> "上课铃都快响了，还坐这？？起来，滚去教室"
            }
        }

        else -> "美好的一天开始了——信不信，随你"
    }
}

// ===================== 主组件（紧凑排版） =====================

@Composable
fun TodayAssistantCard(
    courses: List<Course>,
    tomorrowCourses: List<Course> = emptyList(),
    sectionTimes: Map<Int, String>,
    morningSections: Int,
    afternoonSections: Int,
    wallpaperBackdrop: com.kyant.backdrop.Backdrop? = null,
    blurRadius: Float = 0f
) {
    val (weather, requestLocation) = rememberWeather()
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

    BlurCard(
        cornerRadius = 20.dp,
        wallpaperBackdrop = wallpaperBackdrop,
        blurRadius = blurRadius,
        lightAlpha = 0.74f,
        darkAlpha = 0.74f,
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
                // 左侧：标签 + 课程名（weight 限制宽度，课程名过长时换行显示，右侧剩余时间保持完整）
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
                    .then(
                        if (wallpaperBackdrop == null || blurRadius <= 0f) {
                            Modifier.background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        } else Modifier
                    ),
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
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(
                                id = R.drawable.ic_widget_location
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "需要定位权限·点击授权",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.clickable { requestLocation() }
                        )
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
