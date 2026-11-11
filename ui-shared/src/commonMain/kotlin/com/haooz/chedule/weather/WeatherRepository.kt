package com.haooz.chedule.weather

import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.storage.AppStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * 天气缓存 —— 卡片、详情页、顶栏徽标共用同一份数据。
 *
 * ## 缓存策略（也是唯一的刷新规则）
 * 1. **查到就落盘**
 * 2. **15 分钟内不重复联网**
 * 3. **只在应用前台判定过期**
 * 4. 打开应用时缓存已过期 → 立刻重拉；没过期 → 直接用缓存
 */
object WeatherStore {

    /** 缓存有效期：超过这个时长才算「过期」，才允许联网。 */
    const val CACHE_TTL_MS: Long = 15 * 60 * 1000L

    /**
     * 落盘快照的最长保留时间
     * 超过一天的数据直接当没有缓存
     */
    private const val CACHE_MAX_AGE_MS: Long = 24 * 60 * 60 * 1000L

    /** 拉取失败后的重试间隔；只在应用前台空转时用得到。 */
    private const val RETRY_MS: Long = 60 * 1000L

    /** 前台轮询的最小间隔：时间戳异常时别把循环打成死转。 */
    private const val MIN_TICK_MS: Long = 5 * 1000L

    private const val PREFS = "weather_prefs"
    private const val KEY_SNAPSHOT = "weather_cache_v1"
    private const val KEY_LOADED_AT = "weather_cache_at"
    private const val KEY_PLACE = "weather_cache_place"
    private const val KEY_SOURCE = "weather_cache_source"

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

    /** 快照的「出生时刻」（最近一次成功联网）。 */
    private var loadedAtMs = 0L

    /** 最近一次尝试（成功或失败）的时刻，只用来算重试间隔。 */
    private var lastAttemptAtMs = 0L
    private var lastAttemptOk = true

    /** 落盘快照只还原一次；没还原成功（存储未就绪）也不反复重试。 */
    private var restored = false

    init {
        // 对象第一次被引用时就还原：卡片要立刻有数据
        runCatching { ensureRestored() }
    }

    /** 设置页改源 / 手动刷新时置零，下次进来重拉（落盘缓存一并作废）。 */
    fun invalidate() {
        loadedAtMs = 0L
        lastAttemptAtMs = 0L
        lastAttemptOk = true
        _snapshot.value = null
        _failed.value = false
        clearPersisted()
        // 宿主的前台循环以它当 key：不 +1 的话循环还在睡，作废后要等下一次 tick 才动
        _refreshToken.value += 1
    }

    /**
     * 距下一次该检查还有多久（毫秒）。宿主的前台循环照着它 `delay`。
     *
     * - 有新鲜快照 → 到过期为止（最多 [CACHE_TTL_MS]）；
     * - 刚拉失败 → [RETRY_MS] 后再试；
     * - 什么都没有 → 尽快（下一次立刻试）。
     */
    fun nextCheckDelayMs(): Long {
        ensureRestored()
        val now = Clock.System.now().toEpochMilliseconds()
        if (!lastAttemptOk && lastAttemptAtMs > 0L) {
            return (lastAttemptAtMs + RETRY_MS - now).coerceIn(MIN_TICK_MS, RETRY_MS)
        }
        val anchor = if (loadedAtMs > 0L) loadedAtMs else lastAttemptAtMs
        if (anchor <= 0L) return MIN_TICK_MS
        return (anchor + CACHE_TTL_MS - now).coerceIn(MIN_TICK_MS, CACHE_TTL_MS)
    }

    /**
     * 详情页「重试」用：拿上次的定位强制重拉一遍。
     *
     * 没有定位（进程被杀后直接恢复本页、且没落过盘）时只发刷新信号、返回 null ——
     * 定位是平台能力，这一层拿不到，得由今日页的卡片补上。
     */
    suspend fun requestRefresh(): WeatherSnapshot? {
        ensureRestored()
        val current = place
        if (current == null) {
            _refreshToken.value += 1
            return null
        }
        return ensureLoaded(current, source, force = true)
    }

    /** 卡片/页面拿到定位后调用；同一次定位在缓存有效期内不重复请求。 */
    suspend fun ensureLoaded(
        place: WeatherPlace,
        source: WeatherRepository.Source = this.source,
        force: Boolean = false,
    ): WeatherSnapshot? {
        ensureRestored()
        val cached = _snapshot.value
        if (!force && cached != null && isSamePlace(this.place, place) &&
            source == this.source && isFresh()
        ) {
            return cached
        }
        return mutex.withLock {
            // 双检：并发进来的第二个调用直接拿第一次的结果
            val again = _snapshot.value
            if (!force && again != null && isSamePlace(this.place, place) &&
                source == this.source && isFresh()
            ) {
                return@withLock again
            }
            this.place = place
            this.source = source
            _loading.value = true
            try {
                val result = WeatherRepository.load(place, source)
                lastAttemptAtMs = Clock.System.now().toEpochMilliseconds()
                lastAttemptOk = result != null
                if (result != null) {
                    _snapshot.value = result
                    _failed.value = false
                    loadedAtMs = lastAttemptAtMs
                    // 落盘在 IO 上做：详情页手动刷新是 main 线程调进来的，
                    // 一次 KV 事务虽然只有几毫秒，但足以在动画里掉一帧
                    withContext(ioDispatcher) { persist(result, place, source) }
                } else {
                    // 拉不到就留着旧快照：过期归过期，总比空一块强（下一轮会重试）
                    _failed.value = true
                }
                result
            } catch (_: Exception) {
                lastAttemptAtMs = Clock.System.now().toEpochMilliseconds()
                lastAttemptOk = false
                _failed.value = true
                null
            } finally {
                _loading.value = false
            }
        }
    }

    private fun isFresh(): Boolean {
        val at = loadedAtMs
        if (at <= 0L) return false
        return Clock.System.now().toEpochMilliseconds() - at < CACHE_TTL_MS
    }

    private fun isSamePlace(a: WeatherPlace?, b: WeatherPlace): Boolean {
        if (a == null) return false
        // 约 5km 以内视为同一地点：定位漂移不该触发联网
        return kotlin.math.abs(a.latitude - b.latitude) < 0.05 &&
            kotlin.math.abs(a.longitude - b.longitude) < 0.05
    }

    // ── 落盘 ────────────────────────────────────────────────────────────

    private fun ensureRestored() {
        if (restored) return
        // 存储还没初始化（对象被提前访问）就先不动手：下次访问再来一次，
        // 一旦置位就再也不试了 —— 那时候缓存还没进内存，等于白白丢掉一次落盘数据
        if (!AppStorage.isReady) return
        restored = true
        runCatching {
            val prefs = AppStorage.store(PREFS)
            val raw = prefs.getString(KEY_SNAPSHOT, "")
            val at = prefs.getLong(KEY_LOADED_AT, 0L)
            if (raw.isEmpty() || at <= 0L) return@runCatching
            val now = Clock.System.now().toEpochMilliseconds()
            if (now - at > CACHE_MAX_AGE_MS) {
                clearPersisted()
                return@runCatching
            }
            val cached = decodeWeatherSnapshot(raw) ?: return@runCatching
            _snapshot.value = cached
            loadedAtMs = at
            lastAttemptAtMs = at
            lastAttemptOk = true
            source = WeatherRepository.Source.from(prefs.getString(KEY_SOURCE, ""))
            prefs.getString(KEY_PLACE, "").split(',').let { parts ->
                val lat = parts.getOrNull(0)?.toDoubleOrNull()
                val lng = parts.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lng != null) place = WeatherPlace(latitude = lat, longitude = lng)
            }
        }
    }

    private fun persist(
        snapshot: WeatherSnapshot,
        place: WeatherPlace,
        source: WeatherRepository.Source,
    ) {
        if (!AppStorage.isReady) return
        runCatching {
            val raw = encodeWeatherSnapshot(snapshot)
            AppStorage.store(PREFS).edit {
                putString(KEY_SNAPSHOT, raw)
                putLong(KEY_LOADED_AT, loadedAtMs)
                putString(KEY_PLACE, "${place.latitude},${place.longitude}")
                putString(KEY_SOURCE, source.key)
            }
        }
    }

    private fun clearPersisted() {
        if (!AppStorage.isReady) return
        runCatching {
            AppStorage.store(PREFS).edit {
                remove(KEY_SNAPSHOT)
                remove(KEY_LOADED_AT)
                remove(KEY_PLACE)
                remove(KEY_SOURCE)
            }
        }
    }
}
