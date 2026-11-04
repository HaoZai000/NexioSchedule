package com.haooz.chedule.ui.screens

// ════════════════════════════════════════════════════════════════════════
//  今日助手卡 · Android 侧数据来源
//
//  卡片本体与纯逻辑在 `:ui-shared` 的 `TodayAssistantCard.kt`；天气抓取与融合在
//  `:ui-shared` 的 `weather/`（走 :core 的 HttpService，全平台一份）。
//  本文件只剩**Android 专有的一件事**：拿坐标（LocationManager + 权限）。
//
//  宿主（MainActivity）用法：
//      todayAssistantCard = { d ->
//          val (weather, hasPerm, request) = rememberWeather()
//          TodayAssistantCard(weather, hasPerm, request, d.courses, …)
//      }
// ════════════════════════════════════════════════════════════════════════

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.haooz.chedule.data.platform.PrivacyConsent
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.ui.activities.today.WeatherData
import com.haooz.chedule.ui.activities.today.toCardData
import com.haooz.chedule.weather.WeatherPlace
import com.haooz.chedule.weather.WeatherRepository
import com.haooz.chedule.weather.WeatherStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 卡片自己的三种"没有数据"状态，有快照时全被覆盖。 */
private enum class WeatherPhase { IDLE, NEEDS_LOCATION, FAILED }

/**
 * 设置页改天气源后作废缓存 —— 下次进今日页按新设置重拉。
 *
 * 只作废存储层缓存，不主动重拉：设置页出栈时今日页会重组，`weather_source` 是
 * `LaunchedEffect` 的 key，自己就会重跑一次。
 */
fun invalidateWeatherCache() {
    WeatherStore.invalidate()
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

// 记录上次定位：关掉定位权限后仍能靠它出天气（城市级精度够用很久）
private fun saveLastLocation(context: Context, lngLat: String, name: String?) {
    AppStorage.store("weather_prefs").edit {
        if (!name.isNullOrBlank()) putString("last_location_name", name)
        putString("last_lnglat", lngLat)
    }
}

/** 上次定位经纬度（存的是 "lng,lat"，与旧版本键名保持一致，升级不丢定位）。 */
private fun readLastLngLat(context: Context): Pair<Double, Double>? {
    val s = AppStorage.store("weather_prefs").getStringOrNull("last_lnglat") ?: return null
    val parts = s.split(",")
    if (parts.size != 2) return null
    val lng = parts[0].toDoubleOrNull() ?: return null
    val lat = parts[1].toDoubleOrNull() ?: return null
    return Pair(lng, lat) // 经度在前，与写入时一致
}

@Composable
fun rememberWeather(): Triple<WeatherData, Boolean, () -> Unit> {
    val context = LocalContext.current
    val weatherPrefs = remember { AppStorage.store("weather_prefs") }
    // 源改了要重拉；旧的 "caiyun"/"itboy" 由 Source.from 统一回落到融合源，无需迁移
    val sourceKey = weatherPrefs.getString("weather_source", "fusion")
    // 合规：可观察的隐私同意状态；未同意前不定位/不查询，同意后自动重新执行
    val privacyConsented by PrivacyConsent.consented.collectAsState()

    val snapshot by WeatherStore.snapshot.collectAsState()
    // 详情页点「重试」时若本卡还没定位过，靠这个信号把定位流程再走一遍
    val refreshToken by WeatherStore.refreshToken.collectAsState()

    var phase by remember { mutableStateOf(WeatherPhase.IDLE) }
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
        if (hasLocationPermission) WeatherStore.invalidate() // 授权后强制刷新
    }

    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(hasLocationPermission, sourceKey, refreshTrigger, privacyConsented, refreshToken) {
        // 合规：用户同意隐私政策前，不进行定位与天气数据获取；同意后会因 key 变化自动重跑
        if (!privacyConsented) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val loc = getLastKnownLocation(context, hasLocationPermission)
            val coords = if (loc != null) Pair(loc.latitude, loc.longitude)
            else readLastLngLat(context)?.let { (lng, lat) -> Pair(lat, lng) }
            if (coords == null) {
                phase = WeatherPhase.NEEDS_LOCATION
                return@withContext
            }
            val (lat, lng) = coords
            val place = WeatherPlace(latitude = lat, longitude = lng)
            val loaded = WeatherStore.ensureLoaded(place, WeatherRepository.Source.from(sourceKey))
            if (loaded != null) {
                phase = WeatherPhase.IDLE
                // 数据源自己反查的城市名比 Geocoder 稳（海外、离线都不用额外权限）
                saveLastLocation(context, "$lng,$lat", loaded.placeName)
            } else {
                phase = WeatherPhase.FAILED
            }
        }
    }

    val weather = remember(snapshot, phase) {
        snapshot?.toCardData() ?: when (phase) {
            WeatherPhase.NEEDS_LOCATION -> WeatherData(needsLocation = true, loaded = true)
            WeatherPhase.FAILED -> WeatherData(loaded = true, apiError = true)
            WeatherPhase.IDLE -> WeatherData()
        }
    }

    // 合规：未同意隐私政策前不申请定位权限，也不触发天气查询
    // 记一次：每次重组都新建 lambda，会让下级卡片拿不到稳定引用、白白跟着重组
    val onRequestWeather: () -> Unit = remember(privacyConsented, hasLocationPermission) {
        {
            if (privacyConsented) {
                if (!hasLocationPermission) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else {
                    WeatherStore.invalidate()
                    phase = WeatherPhase.IDLE
                    refreshTrigger++
                }
            }
        }
    }
    return Triple(weather, hasLocationPermission, onRequestWeather)
}
