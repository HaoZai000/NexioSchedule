package com.haooz.chedule.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Notice(val id: String, val title: String, val content: String)

/** 后端公告拉取与本地已读去重 */
object NoticeFetcher {
    private const val API_URL = "https://nexioschedule.icu/api/notice"
    private const val PREFS = "notice_prefs"
    private const val KEY_ID = "seen_id"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** 拉取当前公告；网络异常或无公告时返回 null */
    suspend fun fetch(context: Context): Notice? = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url(API_URL).build()).execute()
            try {
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                val id = if (json.isNull("id")) "" else json.optString("id")
                if (id.isBlank()) return@withContext null
                Notice(id, json.optString("title"), json.optString("content"))
            } finally {
                resp.close()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 是否应展示该公告（未读时 true） */
    fun shouldShow(context: Context, notice: Notice): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ID, "") != notice.id
    }

    /** 标记已读，下次启动不再展示 */
    fun markSeen(context: Context, notice: Notice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_ID, notice.id)
        }
    }
}