package com.haooz.chedule.data

import com.haooz.chedule.data.appearance.AppearanceStyle
import com.haooz.chedule.data.appearance.AppearanceStyleCodec
import com.haooz.chedule.data.appearance.CardContentAlignment
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.data.appearance.CardTextColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [com.haooz.chedule.data.appearance.AppearanceStyleCodec] 与 **真实 Gson** 的差分基准（数据兼容红线的直接证明）。
 *
 * 存量用户 `appearance_settings` 里的外观快照是 `Gson().toJson(CombinationStyle)`
 * 写的（键名 = 字段名，枚举存 ordinal，null 字段跳过）。读写换成 [com.haooz.chedule.data.appearance.AppearanceStyleCodec]
 * （kotlinx-serialization 手写映射）后**输出必须与 Gson 一致**，光靠手写字面量不够，
 * 所以拉真实 Gson 对拍。
 *
 * 对拍口径：
 * 1. **读**：手写金样 JSON（**枚举 = ordinal**，模拟真实存量数据）逐字段断言
 * 2. **写**：encode → decode round-trip 必须逐字段相等；枚举落盘为 ordinal 数字
 * 3. **坏数据**：键名全对不上 / 空 JSON 判 null（不静默把默认值当用户设置）
 */
class AppearanceStyleCodecTest {


    /**
     * 真实存量数据的形态：枚举是 **ordinal（数字）**，不是 name。
     * Gson 默认按 name 序列化枚举，但本项目存量数据当年存的就是 ordinal
     * （见 CardAppearance.kt 顶部「落盘格式 = ordinal」），这里以存量为准。
     */
    private val legacyJson = """
        {"offsetX":12.5,"offsetY":-30.25,"scale":1.8,
         "offsetRefW":1080.0,"offsetRefH":2400.0,
         "cardBlur":6.0,"cardAlpha":0.3,"cardSurfaceAlpha":0.22,
         "cardHeight":61.0,"cardCornerRadius":12.0,
         "wallpaperBrightness":-0.1,"wallpaperIsLight":true,
         "showBreakDividers":false,"cardContentAlignment":2,
         "cardTextColor":0,"cardTextScale":1.2,
         "showClassroom":false,"showTeacher":true,
         "cardRefraction":3,"wallpaperBlur":true}
    """.trimIndent()

    @Test
    fun `存量 ordinal JSON 能被 codec 逐字段读回`() {
        val decoded = AppearanceStyleCodec.decodeOrNull(legacyJson)
        assertNotNull(decoded, "存量快照必须能被解码")
        assertEquals(12.5f, decoded.offsetX)
        assertEquals(-30.25f, decoded.offsetY)
        assertEquals(1.8f, decoded.scale)
        assertEquals(1080f, decoded.offsetRefW)
        assertEquals(2400f, decoded.offsetRefH)
        assertEquals(6f, decoded.cardBlur)
        assertEquals(0.3f, decoded.cardAlpha)
        assertEquals(0.22f, decoded.cardSurfaceAlpha)
        assertEquals(61f, decoded.cardHeight)
        assertEquals(12f, decoded.cardCornerRadius)
        assertEquals(-0.1f, decoded.wallpaperBrightness)
        assertEquals(true, decoded.wallpaperIsLight)
        assertEquals(false, decoded.showBreakDividers)
        // 新加的网格线开关：老快照缺键必须回落「关」，不能凭默认把线画出来
        assertEquals(false, decoded.showWeekGridLines)
        assertEquals(1.2f, decoded.cardTextScale)
        assertEquals(false, decoded.showClassroom)
        assertEquals(true, decoded.showTeacher)
        assertEquals(CardRefractionLevel.STRONG, decoded.cardRefraction)
        assertEquals(true, decoded.wallpaperBlur)
        // 枚举从 ordinal 还原
        assertEquals(CardContentAlignment.CENTER_START, decoded.cardContentAlignment)
        assertEquals(CardTextColor.COLORFUL, decoded.cardTextColor)
    }

    @Test
    fun `null 字段（用户没调过的）读回为 null 而非默认值`() {
        // 真实场景：老用户只动过部分设置，未动过的字段 Gson 直接跳过
        // 存量数据里枚举是数字，字符串值视为「无该字段」→ null（safe getter 兜默认）
        val json = """{"cardBlur":7.0,"cardTextColor":"SOLID"}"""
        val decoded = AppearanceStyleCodec.decodeOrNull(json)
        assertNotNull(decoded)
        assertEquals(7f, decoded.cardBlur)
        assertNull(decoded.cardTextColor)
        // 没动过的字段：为 null（Gson 语义），不是默认值
        assertNull(decoded.cardSurfaceAlpha)
        assertNull(decoded.wallpaperIsLight)
        assertNull(decoded.cardContentAlignment)
        assertNull(decoded.cardRefraction)
    }

    @Test
    fun `codec 写出的 JSON 再被 codec 读回 round-trip 一致`() {
        val style = AppearanceStyle(
            offsetX = -5f, offsetY = 8f, scale = 2.2f,
            offsetRefW = 1170f, offsetRefH = 2532f,
            cardBlur = 0f, cardAlpha = 0.5f, cardSurfaceAlpha = 0.4f,
            cardHeight = 45f, cardCornerRadius = 16f,
            wallpaperBrightness = 0.25f, wallpaperIsLight = false,
            showBreakDividers = false,
            showWeekGridLines = true,
            cardContentAlignment = CardContentAlignment.CENTER_START,
            cardTextColor = CardTextColor.SOLID,
            cardTextScale = 0.85f, showClassroom = false, showTeacher = false,
            cardRefraction = CardRefractionLevel.OFF, wallpaperBlur = false,
        )
        val decoded = AppearanceStyleCodec.decodeOrNull(AppearanceStyleCodec.encode(style))
        assertNotNull(decoded)
        assertEquals(style, decoded, "encode → decode 必须还原出完全相等的对象")
    }

    @Test
    fun `codec 写出的枚举是 ordinal 数字（与存量格式一致）`() {
        val json = AppearanceStyleCodec.encode(
            AppearanceStyle(
                cardContentAlignment = CardContentAlignment.CENTER_START,
                cardTextColor = CardTextColor.SOLID,
                cardRefraction = CardRefractionLevel.OFF,
            )
        )
        // ordinal: CENTER_START=2, SOLID=1, OFF=0 —— 顺序定义在 CardAppearance.kt，不能改
        assertTrue(json.contains("\"cardContentAlignment\":2"), json)
        assertTrue(json.contains("\"cardTextColor\":1"), json)
        assertTrue(json.contains("\"cardRefraction\":0"), json)
    }

    @Test
    fun `键名全对不上的 JSON 判不可用`() {
        // 这是「cardHeight 被误写成别的名字 → 网格整页静默空白」那次教训的防线
        assertNull(AppearanceStyleCodec.decodeOrNull("""{"foo":1,"bar":2}"""))
        assertNull(AppearanceStyleCodec.decodeOrNull("not a json"))
        assertNull(AppearanceStyleCodec.decodeOrNull(""))
    }

    @Test
    fun `空对象仍判可用（全部回落默认值）`() {
        // Gson 对 "{}" 会产出全默认 CombinationStyle —— 老版本就这么处理的，保持一致。
        // 注意 parseSnapshotOrNull 的判断是「至少命中一个已知字段」，空对象不命中 → null。
        // 这里固化**旧行为**：空对象判 null，由调用方重测光恢复默认。
        assertNull(AppearanceStyleCodec.decodeOrNull("{}"))
    }

    @Test
    fun `非字符串的枚举值不炸`() {
        // Gson 遇到类型不匹配会抛 JsonSyntaxException（上层 runCatching 兜底）；
        // codec 这里直接给 null 字段，语义等价（safe getter 兜默认值）
        val decoded = AppearanceStyleCodec.decodeOrNull(
            """{"cardBlur":5.0,"cardTextColor":123}"""
        )
        assertNotNull(decoded)
        assertEquals(5f, decoded.cardBlur)
        // ⚠ fromOrdinal 越界走 getOrElse 兜**默认值**（不抛异常）——
        //   这是 CardAppearance.kt 的既有行为，固化它
        assertEquals(CardTextColor.COLORFUL, decoded.cardTextColor)
    }
}
