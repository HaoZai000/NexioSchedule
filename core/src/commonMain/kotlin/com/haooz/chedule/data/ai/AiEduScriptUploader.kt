package com.haooz.chedule.data.ai

import com.haooz.chedule.data.platform.ApiEndpoints
import com.haooz.chedule.data.platform.AppInfo
import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.StatsReporter
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.platform.currentDeviceInfo
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.util.jsonObjectOf
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 把 AI 生成的教务适配脚本上传到服务端脚本库。
 *
 * 用户确认导入时填写的学校名称是这条记录的唯一标识：后台按学校名展示、按学校名下载源码，
 * 所以名称是必填项（界面侧据此禁用确认按钮）。
 *
 * ⚠ 接口地址与字段名是与 `server/index.js` 的契约，改一边必须改另一边。
 */
object AiEduScriptUploader {

    private const val TAG = "AiEduScriptUploader"
    private const val BASE_URL = ApiEndpoints.AI_EDU_SCRIPTS

    /** 与服务端 `EDU_SCRIPT_MAX_SCHOOL_LEN` 保持一致 */
    const val MAX_SCHOOL_LENGTH = 64

    /** 可替换（`internal var`）以便测试注入 FakeHttpService */
    internal var http: HttpService = createHttpService(
        HttpTimeouts(connectSeconds = 10, readSeconds = 20, callSeconds = 30),
    )

    /**
     * 上传用的独立作用域：导入完成时预览页会立刻出栈，
     * 页面自己的 rememberCoroutineScope 会被取消，上传必须挂在页面生命周期之外。
     */
    private val background = CoroutineScope(SupervisorJob() + ioDispatcher)

    /**
     * 上传一版脚本，返回服务端分配的 id。
     *
     * 失败一律走 [Result.failure]（网络异常、HTTP 非 2xx、返回体不可解析），
     * 调用方不需要自己包 try/catch —— 失败不影响本地导入结果。
     */
    suspend fun upload(
        school: String,
        pageUrl: String,
        script: String,
        courseCount: Int,
        note: String = "",
        sessionId: String = "",
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val cleanSchool = school.trim().take(MAX_SCHOOL_LENGTH)
            if (cleanSchool.isEmpty()) {
                throw IllegalArgumentException("缺少学校名称")
            }
            if (script.isBlank()) {
                throw IllegalArgumentException("脚本内容为空")
            }
            val device = currentDeviceInfo()
            val payload = jsonObjectOf(
                "school" to cleanSchool,
                "page_url" to pageUrl,
                "note" to note,
                "script" to script,
                "ok" to true,
                "course_count" to courseCount,
                "device_id" to StatsReporter.ensureDeviceId(),
                "session_id" to sessionId.trim(),
                "app_version" to AppInfo.version,
                "platform" to StatsReporter.platformTag(device),
            ).toString()
            NexioLog.i(TAG, "上传教务脚本: school=$cleanSchool len=${script.length}")
            val resp = http.post(BASE_URL, payload)
            val json = runCatching { parseJsonObject(resp.text) }.getOrNull()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    json?.optString("error").orEmpty().ifBlank { "上传失败（HTTP ${resp.code}）" },
                )
            }
            json?.optString("id").orEmpty()
        }
    }

    /**
     * 后台静默上传：调用方直接关闭页面即可，不等待也不关心结果（服务端失败只记日志）。
     *
     * 脚本库属于「顺手收集」，不该因此打断用户已经成功的导入。
     */
    fun uploadInBackground(
        school: String,
        pageUrl: String,
        script: String,
        courseCount: Int,
        note: String = "",
        sessionId: String = "",
    ) {
        if (school.isBlank() || script.isBlank()) return
        background.launch {
            runCatching { upload(school, pageUrl, script, courseCount, note, sessionId) }
                .onFailure { NexioLog.i(TAG, "教务脚本上传失败: ${it.message}") }
        }
    }
}
