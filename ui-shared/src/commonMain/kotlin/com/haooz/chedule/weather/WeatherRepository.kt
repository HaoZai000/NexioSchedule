package com.haooz.chedule.weather

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * 天气编排：并发拉多源 → 融合 → 缓存。
 *
 * 三个源各自独立失败：小米挂了 Open-Meteo 顶上，中国天气网挂了只是少了几项指数。
 * 全挂才返回 null（调用方据此显示「获取失败」）。
 */
object WeatherRepository {

    /** 数据源策略。免配置三家；和风/彩云需要用户自己申请 key，本项目不接。 */
    enum class Source(val key: String) {
        /** 三家并发取共识（默认）。 */
        FUSION("fusion"),

        /** 只用小米：最快，字段最全，但只有国内准。 */
        XIAOMI("xiaomi"),

        /** 只用 Open-Meteo：全球可用，纯数值预报。 */
        OPEN_METEO("openmeteo"),

        /** 只用中国天气网：官方口径，但没有逐小时。 */
        WEATHERCN("weathercn");

        companion object {
            fun from(raw: String?): Source = entries.firstOrNull { it.key == raw } ?: FUSION
        }
    }

    suspend fun load(place: WeatherPlace, source: Source): WeatherSnapshot? = coroutineScope {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val nowEpochSec = nowMs / 1000

        // 区县码只有小米能反查（它按经纬度给回 weathercn:101010100），
        // 所以「中国天气网」这一路天然依赖它 —— 单飞模式下也照样先查一次。
        val needsArea = source == Source.FUSION || source == Source.WEATHERCN || source == Source.XIAOMI
        val locatedDeferred = if (needsArea) async { runCatching { XiaomiWeather.locate(place) }.getOrNull() } else null

        val omDeferred = if (source == Source.FUSION || source == Source.OPEN_METEO) {
            async { runCatching { OpenMeteoWeather.fetch(place, nowEpochSec) }.getOrNull() }
        } else null

        val located = locatedDeferred?.await()
        val areaId = located?.areaId

        val xiaomiDeferred = if ((source == Source.FUSION || source == Source.XIAOMI) && located != null) {
            async { runCatching { XiaomiWeather.fetch(place, located) }.getOrNull() }
        } else null
        val wcnDeferred = if ((source == Source.FUSION || source == Source.WEATHERCN) && areaId != null) {
            async { runCatching { WeatherCnWeather.fetch(areaId) }.getOrNull() }
        } else null

        val collected = listOfNotNull(
            xiaomiDeferred?.await(),
            omDeferred?.await(),
            wcnDeferred?.await(),
        ).map { if (it.utcOffsetSec == null) it.copy(utcOffsetSec = 28_800) else it }

        if (collected.isEmpty()) return@coroutineScope null
        WeatherFusion.merge(place, collected, nowEpochSec, nowMs)
    }
}

/**
 * 进程内单例缓存 —— 卡片与详情页共用同一份数据。
 *
 * 为什么不用路由参数传：`AppRoute` 只有稳定 id、没有参数通道（见 `AppRoute` 的 KDoc），
 * 页面之间只能靠共享状态对齐。这里就是那个共享状态。
 */
object WeatherStore {

    private const val TTL_MS = 10 * 60 * 1000L

    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /**
     * 刷新信号：没有定位时置零 + 自增，让今日页的卡片重新走一次定位。
     * 卡片是常驻底座（`AppRouter` 的叠加层设计），只要用户在主界面它就活着。
     */
    private val _refreshToken = MutableStateFlow(0)
    val refreshToken: StateFlow<Int> = _refreshToken.asStateFlow()

    private val mutex = Mutex()
    private var place: WeatherPlace? = null
    private var source: WeatherRepository.Source = WeatherRepository.Source.FUSION
    private var loadedAtMs = 0L

    /** 设置页改源 / 手动刷新时置零，下次进来重拉。 */
    fun invalidate() {
        loadedAtMs = 0L
        _snapshot.value = null
        _failed.value = false
    }

    /**
     * 详情页「重试」用：拿上次的定位强制重拉一遍。
     *
     * 没有定位（进程被杀后直接恢复本页）时只发刷新信号、返回 null —— 定位是平台能力，
     * 这一层拿不到，得由今日页的卡片补上。
     */
    suspend fun requestRefresh(): WeatherSnapshot? {
        val current = place
        if (current == null) {
            _refreshToken.value += 1
            return null
        }
        return ensureLoaded(current, source, force = true)
    }

    /** 卡片/页面拿到定位后调用；同一次定位在 TTL 内不重复请求。 */
    suspend fun ensureLoaded(
        place: WeatherPlace,
        source: WeatherRepository.Source = this.source,
        force: Boolean = false,
    ): WeatherSnapshot? {
        val now = Clock.System.now().toEpochMilliseconds()
        val cached = _snapshot.value
        val samePlace = this.place != null &&
            kotlin.math.abs(this.place!!.latitude - place.latitude) < 0.05 &&
            kotlin.math.abs(this.place!!.longitude - place.longitude) < 0.05
        if (!force && cached != null && samePlace && source == this.source && now - loadedAtMs < TTL_MS) {
            return cached
        }
        return mutex.withLock {
            // 双检：并发进来的第二个调用直接拿第一次的结果
            val again = _snapshot.value
            if (!force && again != null && this.place == place && source == this.source &&
                Clock.System.now().toEpochMilliseconds() - loadedAtMs < TTL_MS
            ) {
                return@withLock again
            }
            this.place = place
            this.source = source
            _loading.value = true
            try {
                val result = WeatherRepository.load(place, source)
                if (result != null) {
                    _snapshot.value = result
                    _failed.value = false
                    loadedAtMs = Clock.System.now().toEpochMilliseconds()
                } else {
                    _failed.value = true
                }
                result
            } catch (_: Exception) {
                _failed.value = true
                null
            } finally {
                _loading.value = false
            }
        }
    }
}
