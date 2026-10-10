package com.haooz.chedule.data

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 上报负载的**字段契约**测试。
 *
 * `device_id` / `event_type` / `timestamp` / `app_version` / `device_model` /
 * `brand` / `manufacturer` / `android_version` / `sdk_level` 这九个键是
 * **服务端既有统计口径**。迁移中把任意一个改名或漏掉，客户端不会报错，
 * 只表现为「服务端某维度数据突然归零/变成 unknown」—— 属极难发现的静默故障。
 *
 * 所以断言的价值不在于测逻辑，而在于**锁住字段名**：谁改字段，测试立刻红。
 *
 * ## 测试隔离说明
 *
 * [StatsReporter] 是 object，内部 `deviceId` 有内存缓存且不可清空。
 * 因此这里**整个测试类共用一个 [InMemoryKeyValueStore] 实例**，
 * 保证「内存缓存」与「落盘数据」始终指向同一份，不会被 @BeforeTest 重置搞得不一致。
 */
class StatsReporterPayloadTest {

    companion object {
        private val sharedStore = InMemoryKeyValueStore()
    }

    @BeforeTest
    fun setUp() {
        AppStorage.init { sharedStore }
        AppInfo.init("1.6.5-test")
        // StatsReporter 是全局 object，deviceId 会跨测试类残留；
        // 而别的测试类可能已把 AppStorage 指向另一个存储 —— 不清就会假失败。
        StatsReporter.resetForTest()
    }

    private fun payload(eventType: String) =
        parseJsonObject(StatsReporter.buildPayload(eventType))

    @Test
    fun `九个上报字段一个都不能少`() {
        val expected = setOf(
            "device_id",
            "event_type",
            "timestamp",
            "app_version",
            "device_model",
            "brand",
            "manufacturer",
            "android_version",
            "sdk_level",
        )
        assertEquals(expected, payload("install").keys, "上报字段集合变化，需同步服务端统计口径")
    }

    @Test
    fun `event_type 原样透传`() {
        assertEquals("active", payload("active").optString("event_type"))
    }

    @Test
    fun `app_version 取自 AppInfo`() {
        assertEquals("1.6.5-test", payload("install").optString("app_version"))
    }

    @Test
    fun `timestamp 是合理的毫秒时间戳`() {
        val ts = payload("install").optLong("timestamp", 0L)
        // 2020-01-01 之后、2100 年之前，足以发现「误用秒」或「恒为 0」
        assertTrue(ts > 1_577_836_800_000L, "timestamp 过小，可能误用了秒: $ts")
        assertTrue(ts < 4_102_444_800_000L, "timestamp 过大: $ts")
    }

    /** device_id 必须是 UUID v4 格式（服务端可能按此校验）。 */
    @Test
    fun `device_id 是 UUID v4 格式`() {
        val id = payload("install").optString("device_id")
        assertTrue(id.isNotBlank(), "device_id 不能为空")
        assertTrue(
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
                .matches(id),
            "device_id 应为 UUID v4: $id",
        )
    }

    /** 重复上报必须复用同一个 device_id，否则服务端会把一台设备统计成多台。 */
    @Test
    fun `多次构造 payload 的 device_id 保持一致`() {
        val a = payload("install").optString("device_id")
        val b = payload("active").optString("device_id")
        val c = payload("install").optString("device_id")
        assertEquals(a, b)
        assertEquals(b, c)
    }

    /** 必须落盘：否则每次冷启动都会换新 id，安装量会被无限放大。 */
    @Test
    fun `device_id 已写入 stats_prefs`() {
        val id = payload("install").optString("device_id")
        val stored = AppStorage.store("stats_prefs").getString("device_id", "")
        assertEquals(id, stored, "device_id 必须落盘到 stats_prefs")
    }

    @Test
    fun `设备字段来自 currentDeviceInfo`() {
        val device = currentDeviceInfo()
        val obj = payload("install")
        assertEquals(device.sdkLevel, obj.optInt("sdk_level", -999))
        assertEquals(device.osVersion, obj.optString("android_version"))
        assertEquals(device.model, obj.optString("device_model"))
        assertEquals(device.brand, obj.optString("brand"))
        assertEquals(device.manufacturer, obj.optString("manufacturer"))
    }

    /** 上报体必须是合法 JSON 对象（服务端按 JSON 解析）。 */
    @Test
    fun `payload 是合法 JSON 对象`() {
        val raw = StatsReporter.buildPayload("install")
        assertTrue(raw.startsWith("{") && raw.endsWith("}"), "必须是 JSON 对象: $raw")
        // 能被重新解析即说明结构合法
        payload("install")
    }
}
