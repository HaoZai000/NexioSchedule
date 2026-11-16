package com.haooz.chedule.data.platform

import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.coroutines.withContext

data class Notice(val id: String, val title: String, val content: String)

/** 后端公告拉取与本地已读去重。 */
object NoticeFetcher {
    // 统一走域名 HTTPS；明文 3000 只保留给老版本兼容
    private const val API_URL = ApiEndpoints.NOTICE
    private const val PREFS = "notice_prefs"
    private const val KEY_ID = "seen_id"

    // 超时 connect 5s / read 5s，不设 callTimeout
    /** 可替换（`internal var`）以便 commonTest 注入 FakeHttpService。 */
    internal var http: HttpService =
        createHttpService(HttpTimeouts(connectSeconds = 5, readSeconds = 5))

    /** 拉取当前公告；网络异常或无公告时返回 null */
    suspend fun fetch(): Notice? = withContext(ioDispatcher) {
        try {
            val resp = http.get(API_URL)
            if (!resp.isSuccessful) return@withContext null
            val json = parseJsonObject(resp.text)
            // optString 对缺键与显式 null 都返回 ""
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
