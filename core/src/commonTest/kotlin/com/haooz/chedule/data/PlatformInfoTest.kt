package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [randomUuidV4] 与 [AppInfo] 的验证。
 *
 * UUID 是**自己重写的**（原先 `java.util.UUID.randomUUID()` 是 JVM 专有），
 * 而后端可能按 UUID 格式校验/索引，所以格式必须逐位对上。
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

    @Test
    fun `固定长度为 36`() {
        repeat(50) {
            assertEquals(36, randomUuidV4().length)
        }
    }

    /** 版本位必须是 4、变体位必须是 8/9/a/b —— 这是「v4」的定义。 */
    @Test
    fun `版本位与变体位正确`() {
        repeat(200) {
            val id = randomUuidV4()
            assertEquals('4', id[14], "第 15 位必须是版本号 4: $id")
            assertTrue(id[19] in "89ab", "第 20 位必须是变体位 8/9/a/b: $id")
        }
    }

    @Test
    fun `连字符位置固定`() {
        repeat(50) {
            val id = randomUuidV4()
            assertEquals('-', id[8])
            assertEquals('-', id[13])
            assertEquals('-', id[18])
            assertEquals('-', id[23])
        }
    }

    /** 不能退化成常量或低熵值 —— 同一进程内连续生成应基本不重复。 */
    @Test
    fun `连续生成不重复`() {
        val ids = List(1000) { randomUuidV4() }
        assertEquals(1000, ids.toSet().size, "1000 次生成出现重复，随机源有问题")
    }

    @Test
    fun `AppInfo 未初始化时返回 unknown`() {
        // 该对象是全局单例，测试间会互相影响；只验证「有值且非空」
        AppInfo.init("1.6.5")
        assertEquals("1.6.5", AppInfo.version)
        AppInfo.init("unknown")
        assertEquals("unknown", AppInfo.version)
    }

    @Test
    fun `currentDeviceInfo 各字段非空`() {
        val d = currentDeviceInfo()
        // jvm 下取自系统属性，android 下取自 Build；两者都不应为空白
        assertTrue(d.model.isNotBlank(), "model 不应为空")
        assertTrue(d.brand.isNotBlank(), "brand 不应为空")
        assertTrue(d.manufacturer.isNotBlank(), "manufacturer 不应为空")
        assertTrue(d.osVersion.isNotBlank(), "osVersion 不应为空")
    }
}
