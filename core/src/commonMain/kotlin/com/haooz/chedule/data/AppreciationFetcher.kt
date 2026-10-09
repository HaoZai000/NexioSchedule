package com.haooz.chedule.data

import kotlinx.coroutines.withContext

/** 分页拉取结果 */
data class AppreciationsPage(val items: List<AppreciationItem>, val hasMore: Boolean)

/**
 * 云端捐赠列表拉取；网络异常或解析失败时返回空数据（App 端不再本地硬编码捐赠样本）。
 *
 * ## 迁移状态：三个 Android 依赖已全部解除，已下沉 `:core`
 *
 * | 原依赖 | 现状 |
 * |---|---|
 * | `OkHttpClient` + `HttpUrl` | ✅ [HttpService]，超时仍是 5s/5s |
 * | `org.json.JSONObject` / `JSONArray` | ✅ [parseJsonObject] / [optJsonArray] |
 * | `Context` | 本来就没有 |
 * | `AppreciationItem`（原在 `ui.data`） | ✅ 已一并归位到 `com.haooz.chedule.data` |
 */
object AppreciationFetcher {
    // 与公告/上报同源走明文 HTTP：HTTPS(443) 在该运营商网络下 TLS 握手被干扰（Connection reset），沿用 3000 直达后端
    private const val API_URL = "http://182.92.193.223:3000/api/appreciations"
    private const val TOP_URL = "http://182.92.193.223:3000/api/appreciations/top"
    const val PAGE_SIZE = 10

    // 超时与迁移前一致（connect 5s / read 5s）
    /**
     * 可替换（`internal var`）以便 commonTest 注入 FakeHttpService。
     * 用 var 而非 val：object 是全局单例，测试需要置换实现来覆盖分页/失败分支。
     */
    internal var http: HttpService =
        createHttpService(HttpTimeouts(connectSeconds = 5, readSeconds = 5))

    /** 拉取一页捐赠（从新到旧）；网络异常或无数据时返回空数据 */
    suspend fun fetch(offset: Int = 0, limit: Int = PAGE_SIZE): AppreciationsPage =
        withContext(ioDispatcher) {
            try {
                // 迁移前用 HttpUrl.newBuilder() 拼查询参数；这里手工拼接，参数都是数字，
                // 无需转义。若将来加入用户可控的字符串参数，必须改成 URL 编码。
                val resp = http.get("$API_URL?limit=$limit&offset=$offset")
                if (!resp.isSuccessful) return@withContext AppreciationsPage(emptyList(), false)
                val json = parseJsonObject(resp.text)
                val items = json.optJsonArray("records").toItems()
                AppreciationsPage(items = items, hasMore = json.optBoolean("has_more"))
            } catch (_: Exception) {
                AppreciationsPage(emptyList(), false)
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

    /** 缺 `records` 字段时按空数组处理（对应迁移前的 `?: JSONArray()`）。 */
    private fun kotlinx.serialization.json.JsonArray?.toItems(): List<AppreciationItem> {
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
