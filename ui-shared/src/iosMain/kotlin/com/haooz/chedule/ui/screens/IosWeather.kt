@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.haooz.chedule.data.platform.PrivacyConsent
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.ui.activities.today.WeatherData
import com.haooz.chedule.ui.activities.today.toCardData
import com.haooz.chedule.weather.WeatherPlace
import com.haooz.chedule.weather.WeatherRepository
import com.haooz.chedule.weather.WeatherStore
import kotlin.coroutines.resume
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * iOS 的今日助手天气 + 定位（对应 Android `TodayAssistant.kt` 里的 `rememberWeather`）。
 *
 * ## 与 Android 的分工
 *
 * 卡片本体在 `:ui-shared` 的 `TodayAssistantCard.kt`；抓取与多源融合也在共享层的
 * `weather/`（走 :core 的 `HttpService` → NSURLSession）。**本文件只剩 CoreLocation**。
 *
 * ## 合规
 *
 * 与 Android 同一条铁律：**用户同意隐私政策前不定位、不请求**；`PrivacyConsent.consented`
 * 是 StateFlow，同意后自动重跑（在 `LaunchedEffect` 的 key 里）。
 */

/** 卡片自己的三种"没有数据"状态，有快照时全被覆盖。 */
private enum class IosWeatherPhase { IDLE, NEEDS_LOCATION, FAILED }

/** 设置页改天气源后作废缓存（与 Android `invalidateWeatherCache` 对应）。 */
fun invalidateWeatherCacheIos() {
    WeatherStore.invalidate()
}

/**
 * 取天气 + 定位权限状态。
 *
 * @return `(weather, hasLocationPermission, onRequestLocation)`
 */
@Composable
fun rememberWeatherIos(): Triple<WeatherData, Boolean, () -> Unit> {
    val weatherPrefs = remember { AppStorage.store("weather_prefs") }
    // 每次重组都现读：设置页改完源回来要立刻生效
    val sourceKey = weatherPrefs.getString("weather_source", "fusion")

    // 合规：未同意前不定位、不查询；同意后 key 变化自动重跑
    val privacyConsented by PrivacyConsent.consented.collectAsState()
    val snapshot by WeatherStore.snapshot.collectAsState()
    val refreshToken by WeatherStore.refreshToken.collectAsState()

    val locationClient = remember { IosLocationClient() }
    var phase by remember { mutableStateOf(IosWeatherPhase.IDLE) }
    var hasLocationPermission by remember { mutableStateOf(locationClient.isAuthorized()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(hasLocationPermission, sourceKey, refreshTrigger, privacyConsented, refreshToken) {
        if (!privacyConsented) return@LaunchedEffect
        val coords = if (hasLocationPermission) locationClient.requestLocation() else null
        if (coords == null) {
            phase = IosWeatherPhase.NEEDS_LOCATION
            return@LaunchedEffect
        }
        // pairOf 给的是 (latitude, longitude)，别跟 Android 的 (lng, lat) 混了
        val place = WeatherPlace(latitude = coords.first, longitude = coords.second)
        val loaded = withContext(ioDispatcher) {
            WeatherStore.ensureLoaded(place, WeatherRepository.Source.from(sourceKey))
        }
        phase = if (loaded != null) IosWeatherPhase.IDLE else IosWeatherPhase.FAILED
    }

    val weather = remember(snapshot, phase) {
        snapshot?.toCardData() ?: when (phase) {
            IosWeatherPhase.NEEDS_LOCATION -> WeatherData(needsLocation = true, loaded = true)
            IosWeatherPhase.FAILED -> WeatherData(loaded = true, apiError = true)
            IosWeatherPhase.IDLE -> WeatherData()
        }
    }

    // 记一次：每次重组都新建 lambda，会让下级卡片拿不到稳定引用、白白跟着重组
    val onRequestWeather: () -> Unit = remember(privacyConsented, hasLocationPermission) {
        {
            if (privacyConsented) {
                if (!hasLocationPermission) {
                    locationClient.requestAuthorization { granted -> hasLocationPermission = granted }
                } else {
                    WeatherStore.invalidate()
                    phase = IosWeatherPhase.IDLE
                    refreshTrigger++
                }
            }
        }
    }
    return Triple(weather, hasLocationPermission, onRequestWeather)
}

/**
 * CoreLocation 封装。
 *
 * ⚠ `CLLocationManager.delegate` 是**弱引用**，delegate 必须由本类强引用持有，
 * 否则授权/定位回调永远不会来（表现为「点了授权没反应」）。
 */
private class IosLocationClient {

    private val manager = CLLocationManager()
    private var delegate: LocationDelegate? = null
    private var pendingLocation: ((Pair<Double, Double>?) -> Unit)? = null
    private var pendingAuth: ((Boolean) -> Unit)? = null

    init {
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        val d = LocationDelegate(
            onLocations = { locations ->
                val loc = locations.filterIsInstance<CLLocation>().lastOrNull()
                val cb = pendingLocation
                pendingLocation = null
                cb?.invoke(loc?.let { pairOf(it) })
            },
            onError = {
                val cb = pendingLocation
                pendingLocation = null
                cb?.invoke(null)
            },
            onAuthChanged = { granted ->
                pendingAuth?.invoke(granted)
            },
        )
        delegate = d
        manager.delegate = d
    }

    fun isAuthorized(): Boolean = isGranted(CLLocationManager.authorizationStatus())

    fun requestAuthorization(onResult: (Boolean) -> Unit) {
        pendingAuth = onResult
        manager.requestWhenInUseAuthorization()
    }

    /**
     * 取一次当前位置（挂起）：先看系统缓存的 `manager.location` —— 天气只要城市级精度，
     * 缓存通常就够，省掉一次几秒的定位等待；没有才发真实请求。
     */
    suspend fun requestLocation(): Pair<Double, Double>? {
        manager.location?.let { cached ->
            val pair = pairOf(cached)
            if (pair != null) return pair
        }
        return suspendCancellableCoroutine { cont ->
            pendingLocation = { pair -> if (cont.isActive) cont.resume(pair) }
            manager.requestLocation()
        }
    }

    private fun pairOf(location: CLLocation): Pair<Double, Double>? {
        val coord = location.coordinate
        val pair = coord.useContents { latitude to longitude }
        // 0,0 是 CoreLocation 的「无效坐标」哨兵值
        if (pair.first == 0.0 && pair.second == 0.0) return null
        return pair
    }

    private fun isGranted(status: Int): Boolean =
        status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
}

private class LocationDelegate(
    private val onLocations: (List<*>) -> Unit,
    private val onError: () -> Unit,
    private val onAuthChanged: (Boolean) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        onLocations(didUpdateLocations)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        onError()
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val status = CLLocationManager.authorizationStatus()
        val granted = status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
        onAuthChanged(granted)
    }
}
