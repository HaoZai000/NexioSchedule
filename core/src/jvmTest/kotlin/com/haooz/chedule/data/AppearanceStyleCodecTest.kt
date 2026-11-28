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

/** 通过固定的旧格式样本检查读取兼容性，并验证枚举编码与快照往返。 */
class AppearanceStyleCodecTest {


    /** 此样本以数字 ordinal 保存枚举，而不是枚举名称。 */
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
        // 存量数据里枚举是数字，字符串值视为「无该字段」→ null（safe getter 兜默认）
        val json = """{"cardBlur":7.0,"cardTextColor":"SOLID"}"""
        val decoded = AppearanceStyleCodec.decodeOrNull(json)
        assertNotNull(decoded)
        assertEquals(7f, decoded.cardBlur)
        assertNull(decoded.cardTextColor)
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
        assertNull(AppearanceStyleCodec.decodeOrNull("""{"foo":1,"bar":2}"""))
        assertNull(AppearanceStyleCodec.decodeOrNull("not a json"))
        assertNull(AppearanceStyleCodec.decodeOrNull(""))
    }

    @Test
    fun `空对象仍判可用（全部回落默认值）`() {
        // 空对象没有已知字段，不可作为外观快照。
        assertNull(AppearanceStyleCodec.decodeOrNull("{}"))
    }

    @Test
    fun `非字符串的枚举值不炸`() {
        val decoded = AppearanceStyleCodec.decodeOrNull(
            """{"cardBlur":5.0,"cardTextColor":123}"""
        )
        assertNotNull(decoded)
        assertEquals(5f, decoded.cardBlur)
        // 越界 ordinal 回退到默认枚举值。
        assertEquals(CardTextColor.COLORFUL, decoded.cardTextColor)
    }
}
