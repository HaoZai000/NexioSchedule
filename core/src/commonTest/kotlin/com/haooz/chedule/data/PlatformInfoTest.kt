package com.haooz.chedule.data

import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.platform.stableDeviceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [randomUuidV4] 与稳定设备标识的验证。
 *
 * `randomUuidV4` 现在只用于课程/课表等业务实体 ID；设备标识改由平台提供。
 */
class PlatformInfoTest {

    private val uuidRegex =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    @Test
    fun `生成的标识符合 UUID v4 格式`() {
        repeat(200) {
            val id = randomUuidV4()
            assertTrue(
                uuidRegex.matches(id),
                "不符合 UUID v4 格式: $id",
            )
        }
    }

    /** 不能退化成常量或低熵值 —— 同一进程内连续生成应基本不重复。 */
    @Test
    fun `连续生成不重复`() {
        val ids = List(1000) { randomUuidV4() }
        assertEquals(1000, ids.toSet().size, "1000 次生成出现重复，随机源有问题")
    }

    /** 桌面端机器码派生：多次调用必须稳定，且格式符合服务端校验。 */
    @Test
    fun `stableDeviceId 稳定且格式合法`() {
        val first = stableDeviceId()
        val second = stableDeviceId()
        assertTrue(first.isNotBlank(), "桌面端应能读取系统机器码")
        assertTrue(
            Regex("^[A-Za-z0-9_-]{8,64}$").matches(first),
            "设备标识格式不合法: $first",
        )
        assertEquals(first, second)
    }
}
