package com.haooz.chedule.weather

import com.haooz.chedule.data.platform.HttpResult
import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.serialization.json.JsonObject

/**
 * 天气抓取的统一出口。
 *
 * 走 :core 的 [HttpService]（Android=OkHttp / iOS=NSURLSession / 桌面=HttpURLConnection），
 * **不引 Ktor** —— 它会把协程顶到 1.10+，全 app 的提醒/小组件都压在 1.9.0 上。
 *
 * 三个源都是「网页自用通道」：无 SLA、结构可能变、经常要伪装 UA。所以这里的
 * 失败一律返回 null 而不是抛，由融合器当作"这一路没数据"，别让一个源拖垮整页。
 */
internal object WeatherHttp {

    /** 部分端点（nmc / weather.com.cn）对无 UA 请求直接 403。 */
    const val UA: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private val client: HttpService by lazy {
        createHttpService(HttpTimeouts(connectSeconds = 6, readSeconds = 9, callSeconds = 13))
    }

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String? = try {
        val result: HttpResult = client.get(url, headers + ("User-Agent" to UA), maxBytes = 2L * 1024 * 1024)
        if (result.isSuccessful && !result.truncated) result.text.takeIf { it.isNotBlank() } else null
    } catch (_: Exception) {
        null
    }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject? =
        getText(url, headers)?.let { text ->
            try {
                parseJsonObject(text.trim().removePrefix("\uFEFF"))
            } catch (_: Exception) {
                null
            }
        }

    suspend fun getJsonArrayText(url: String, headers: Map<String, String> = emptyMap()): String? =
        getText(url, headers)?.trim()?.removePrefix("\uFEFF")
}
