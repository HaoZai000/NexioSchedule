package com.haooz.chedule.data.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 访问密钥只跟着自家服务端走。
 *
 * 不依赖密钥是否注入：空值断言不加头，有值断言加对头，CI 与发布构建都能跑。
 */
class AccessKeyHeaderTest {

    private val header = "X-Nexio-Key"

    @Test
    fun ownApiCarriesKeyWhenConfigured() {
        val url = "${ApiEndpoints.BASE}/api/notice"
        val headers = withAccessKey(url, emptyMap())
        if (NexioAccessKey.VALUE.isEmpty()) {
            assertTrue(header !in headers, "密钥为空时不该带请求头")
        } else {
            assertEquals(NexioAccessKey.VALUE, headers[header])
        }
    }

    @Test
    fun thirdPartyNeverGetsKey() {
        // 换服务商（BYOK 直连）与 WebDAV 都走同一个 HttpService，密钥绝不能漏过去
        listOf(
            "https://api.deepseek.com/v1/chat/completions",
            "https://dav.example.com/remote.php/dav/files/me/",
            "https://nexioschedule.icu.evil.com/api/notice",
        ).forEach { url ->
            assertTrue(header !in withAccessKey(url, emptyMap()), "不应给第三方地址带密钥：$url")
        }
    }

    @Test
    fun callerHeaderWins() {
        // 调用方自己写了这个头（测试/调试）时不覆盖
        val headers = withAccessKey("${ApiEndpoints.BASE}/api/notice", mapOf(header to "mine"))
        assertEquals("mine", headers[header])
    }
}
