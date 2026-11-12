package com.haooz.chedule.data.platform

import com.haooz.chedule.data.util.length
import com.haooz.chedule.data.util.optBoolean
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray

/** 分页拉取结果；[failed] 用于区分「请求失败」与「成功但为空」 */
data class AppreciationsPage(
    val items: List<AppreciationItem>,
    val hasMore: Boolean,
    val failed: Boolean = false,
)

/**
 * 云端捐赠列表拉取；网络异常或解析失败时返回空数据（App 端不再本地硬编码捐赠样本）。
 */
object AppreciationFetcher {
    // 与公告/上报同源走明文 HTTP：该运营商网络下 HTTPS 的 TLS 握手被干扰，故直达后端 3000。
    private const val API_URL = "http://182.92.193.223:3000/api/appreciations"
    private const val TOP_URL = "http://182.92.193.223:3000/api/appreciations/top"
    const val PAGE_SIZE = 10

    // ⚠ 超时与迁移前一致：connect 5s / read 5s
    /** `internal var` 以便测试注入 FakeHttpService（object 单例需置换实现）。 */
    internal var http: HttpService =
        createHttpService(HttpTimeouts(connectSeconds = 5, readSeconds = 5))

    /** 拉取一页捐赠（从新到旧）；网络异常或无数据时返回空数据 */
    suspend fun fetch(offset: Int = 0, limit: Int = PAGE_SIZE): AppreciationsPage =
        withContext(ioDispatcher) {
            try {
                // 手工拼接查询参数（参数皆数字无需转义）；若将来加入用户可控字符串参数，必须改为 URL 编码。
                val resp = http.get("$API_URL?limit=$limit&offset=$offset")
                if (!resp.isSuccessful) {
                    return@withContext AppreciationsPage(emptyList(), false, failed = true)
                }
                val json = parseJsonObject(resp.text)
                val items = json.optJsonArray("records").toItems()
                AppreciationsPage(items = items, hasMore = json.optBoolean("has_more"))
            } catch (_: Exception) {
                AppreciationsPage(emptyList(), false, failed = true)
            }
        }

    /** 拉取累计捐赠前三（云端已排除匿名）；网络异常返回空列表 */
    suspend fun fetchTop(limit: Int = 3): List<AppreciationItem> =
        withContext(ioDispatcher) {
            try {
                val resp = http.get("$TOP_URL?limit=$limit")
                if (!resp.isSuccessful) return@withContext emptyList()
                val json = parseJsonObject(resp.text)
                json.optJsonArray("records").toItems()
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** 缺 `records` 字段时按空数组处理。 */
    private fun JsonArray?.toItems(): List<AppreciationItem> {
        val array = this ?: return emptyList()
        val items = ArrayList<AppreciationItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJsonObject(i) ?: continue
            items += AppreciationItem(
                nickname = obj.optString("nickname"),
                amount = obj.optString("amount"),
                time = obj.optString("time"),
                remark = obj.optString("remark"),
            )
        }
        return items
    }
}

/**
 * 捐赠明细项；数据由云端后端动态下发（见 [AppreciationFetcher]），App 端不再本地硬编码样本。
 */
data class AppreciationItem(
    val nickname: String,
    val amount: String,
    val time: String,
    val remark: String = "",
)
