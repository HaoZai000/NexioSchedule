package com.haooz.chedule.data

import kotlinx.coroutines.withContext

data class Notice(val id: String, val title: String, val content: String)

/**
 * 后端公告拉取与本地已读去重。
 *
 * ## 迁移状态：三个 Android 依赖已全部解除，可下沉 `:core`
 *
 * | 原依赖 | 现状 |
 * |---|---|
 * | `Context`（仅用于 getSharedPreferences） | ✅ [AppStorage] |
 * | `OkHttpClient`（每文件各建一个） | ✅ [HttpService]，超时仍是 5s/5s |
 * | `org.json.JSONObject` | ✅ [parseJsonObject] |
 */
object NoticeFetcher {
    // 公告走明文 HTTP：HTTPS(443) 在该运营商网络下 TLS 握手被干扰（Connection reset），改用 3000 直达后端
    private const val API_URL = "http://182.92.193.223:3000/api/notice"
    private const val PREFS = "notice_prefs"
    private const val KEY_ID = "seen_id"

    // 超时与迁移前一致（connect 5s / read 5s，不设 callTimeout）
    /** 可替换（`internal var`）以便 commonTest 注入 FakeHttpService。 */
    internal var http: HttpService =
        createHttpService(HttpTimeouts(connectSeconds = 5, readSeconds = 5))

    /** 拉取当前公告；网络异常或无公告时返回 null */
    suspend fun fetch(): Notice? = withContext(ioDispatcher) {
        try {
            val resp = http.get(API_URL)
            if (!resp.isSuccessful) return@withContext null
            val json = parseJsonObject(resp.text)
            // optString 对缺键与显式 null 都返回 ""，等价于原先的 isNull 判断 + optString
            val id = json.optString("id")
            if (id.isBlank()) return@withContext null
            Notice(id, json.optString("title"), json.optString("content"))
        } catch (_: Exception) {
            null
        }
    }

    /** 是否应展示该公告（未读时 true） */
    fun shouldShow(notice: Notice): Boolean =
        AppStorage.store(PREFS).getString(KEY_ID, "") != notice.id

    /** 标记已读，下次启动不再展示 */
    fun markSeen(notice: Notice) {
        AppStorage.store(PREFS).edit {
            putString(KEY_ID, notice.id)
        }
    }
}
