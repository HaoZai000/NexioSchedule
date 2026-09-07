package com.haooz.chedule.data

import com.haooz.chedule.ui.data.AppreciationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** 分页拉取结果 */
data class AppreciationsPage(val items: List<AppreciationItem>, val hasMore: Boolean)

/** 云端捐赠列表拉取；网络异常或解析失败时返回空数据（App 端不再本地硬编码捐赠样本） */
object AppreciationFetcher {
    // 与公告/上报同源走明文 HTTP：HTTPS(443) 在该运营商网络下 TLS 握手被干扰（Connection reset），沿用 3000 直达后端
    private const val API_URL = "http://182.92.193.223:3000/api/appreciations"
    const val PAGE_SIZE = 10

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** 拉取一页捐赠（从新到旧）；网络异常或无数据时返回空数据 */
    suspend fun fetch(offset: Int = 0, limit: Int = PAGE_SIZE): AppreciationsPage =
        withContext(Dispatchers.IO) {
            try {
                val url = API_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("limit", limit.toString())
                    .addQueryParameter("offset", offset.toString())
                    .build()
                val resp = client.newCall(Request.Builder().url(url).build()).execute()
                try {
                    if (!resp.isSuccessful) return@withContext AppreciationsPage(emptyList(), false)
                    val json = org.json.JSONObject(resp.body?.string() ?: return@withContext AppreciationsPage(emptyList(), false))
                    val array = json.optJSONArray("records") ?: JSONArray()
                    val items = buildList {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            add(
                                AppreciationItem(
                                    nickname = obj.optString("nickname"),
                                    amount = obj.optString("amount"),
                                    time = obj.optString("time"),
                                    remark = obj.optString("remark"),
                                )
                            )
                        }
                    }
                    AppreciationsPage(items = items, hasMore = json.optBoolean("has_more"))
                } finally {
                    resp.close()
                }
            } catch (_: Exception) {
                AppreciationsPage(emptyList(), false)
            }
        }
}