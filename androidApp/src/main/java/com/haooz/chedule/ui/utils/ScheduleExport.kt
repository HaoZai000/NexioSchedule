package com.haooz.chedule.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.BackupStr
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// ════════════════════════════════════════════════════════════════════════
//  课表导出 —— 单文件全包（分享给其他 App / 分享口令）
//
//  一条完整的「把课表发出去」链路，全在此文件：
//  1. [buildShareScheduleMap] 等    从 [CourseRepository] 组装分享 JSON
//  2. [ShareCodeApi]                口令的创建与按口令取回（网络）
//  3. [ShareImageGenerator]         口令卡片图片生成
//  4. [performScheduleShare]        串起全流程：生成口令 → 复制剪贴板 → 出图 → 唤起分享
//
//  **导入侧不在这里** —— 分享数据的解析器（`parseShare*` 全套）归
//  `ScheduleImport.kt`，两边成对改动时需同时看这两个文件。
//
//  共享的数据契约（分享 JSON 的字段名）也定义在导入文件，两边必须同步。
//
//  注意：备份/恢复（本地文件、WebDAV）**不属本文件**，见 `ScheduleBackup.kt`——
//  那是另一套格式与生命周期。
// ════════════════════════════════════════════════════════════════════════

// ── 1. 分享数据组装（读取课表 → JSON）─────────────────────────
//
// buildShareScheduleMap / courseToShareMap / ShareCourseWeekModel 等 2026-10-11
// 已迁 :ui-shared（同包名，本文件直接可见，无需 import）
// —— 见 ui/utils/ShareScheduleCodec.kt。
// 它们只碰 CourseRepository + Map<String, Any>；留在本文件会让共享页反向依赖
// 本文件的 OkHttp / Gson / Bitmap（口令接口与出图都在这里）。

// ── 2 & 3. 口令接口 + 分享卡片图片 ────────────────────────────

/**
 * 课表分享口令接口。
 *
 * 创建（[createShare]）属**导出**链路，取回（[fetchShare]）属**导入**链路，
 * 但二者共用同一服务端地址与 OkHttp 客户端，拆开反而要多写一份连接池配置，
 * 故这个 object 整体留在本文件，导入侧（`ScheduleImport.kt`）直接调用
 * [fetchShare]，不重复定义。
 */
object ShareCodeApi {
    // 与其他后端接口一致：HTTPS(443) 在部分运营商网络下 TLS 握手被干扰，改用 3000 直达
    private const val BASE_URL = "http://182.92.193.223:3000/api/share"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class CreateResult(
        val code: String,
        val scheduleName: String,
        val expiresInSeconds: Int,
    )

    data class FetchResult(
        val code: String,
        val scheduleName: String,
        val scheduleData: Map<String, Any>,
    )

    /**
     * 上传课表创建分享口令。
     * @param scheduleJson 已序列化的课表 JSON 字符串
     */
    suspend fun createShare(
        scheduleName: String,
        scheduleJson: String,
    ): Result<CreateResult> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("schedule_name", scheduleName)
                put("schedule_data", JSONObject(scheduleJson))
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(BASE_URL).post(body).build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = try {
                        JSONObject(text).optString("error")
                    } catch (_: Exception) {
                        ""
                    }
                    return@withContext Result.failure(
                        IllegalStateException(err.ifBlank { AndroidMiscStr.serverErrorHttpN(resp.code) })
                    )
                }
                val json = JSONObject(text)
                val code = json.optString("code")
                if (code.isBlank()) {
                    return@withContext Result.failure(IllegalStateException(AndroidMiscStr.serverNoCodeReturned()))
                }
                Result.success(
                    CreateResult(
                        code = code,
                        scheduleName = json.optString("schedule_name").ifBlank { scheduleName },
                        expiresInSeconds = json.optInt("expires_in", 1800),
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    /** 按口令取回课表数据（**导入侧入口**，结果交给 `ScheduleImport.kt` 的 `parseShare*` 解析） */
    suspend fun fetchShare(code: String): Result<FetchResult> = withContext(Dispatchers.IO) {
        try {
            val trimmed = code.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException(BackupStr.enterShareCode()))
            }
            val url = "$BASE_URL?code=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    null
                }
                if (!resp.isSuccessful) {
                    val err = json?.optString("error").orEmpty()
                    return@withContext Result.failure(
                        IllegalStateException(
                            err.ifBlank {
                                when (resp.code) {
                                    404 -> BackupStr.shareCodeNotFound()
                                    else -> AndroidMiscStr.serverErrorHttpN(resp.code)
                                }
                            }
                        )
                    )
                }
                if (json == null || !json.optBoolean("ok", false)) {
                    return@withContext Result.failure(IllegalStateException(BackupStr.shareCodeNotFound()))
                }
                @Suppress("UNCHECKED_CAST")
                val data = json.optJSONObject("schedule_data")?.let { obj ->
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    com.google.gson.Gson().fromJson<Map<String, Any>>(obj.toString(), type)
                }
                if (data == null) {
                    return@withContext Result.failure(IllegalStateException(AndroidMiscStr.scheduleDataInvalid()))
                }
                Result.success(
                    FetchResult(
                        code = json.optString("code").ifBlank { trimmed },
                        scheduleName = json.optString("schedule_name").ifBlank { AndroidMiscStr.sharedScheduleName() },
                        scheduleData = data,
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 课表分享卡片：在模板图的白色口令框内绘制趣味口令。
 *
 * 模板 1417×1417，口令框约 (121, 600) – (1296, 775)。
 */
object ShareImageGenerator {
    private const val TEMPLATE_ASSET = "share_card_template.png"
    private const val CODE_BOX_LEFT = 121f
    private const val CODE_BOX_TOP = 600f
    private const val CODE_BOX_RIGHT = 1296f
    private const val CODE_BOX_BOTTOM = 775f

    /** 在模板上绘制口令，返回完整位图 */
    fun generateCard(context: Context, code: String): Bitmap? {
        return try {
            val template = context.assets.open(TEMPLATE_ASSET).use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return null
            val bitmap = template.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            val canvas = Canvas(bitmap)

            val boxWidth = CODE_BOX_RIGHT - CODE_BOX_LEFT
            val boxHeight = CODE_BOX_BOTTOM - CODE_BOX_TOP
            val centerX = (CODE_BOX_LEFT + CODE_BOX_RIGHT) / 2f
            val centerY = (CODE_BOX_TOP + CODE_BOX_BOTTOM) / 2f

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(28, 28, 30)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                // 先按高度给上限，再按字数收缩，保证 8 字左右居中且不溢出
                val heightCap = boxHeight * 0.52f
                val widthCap = if (code.length > 0) boxWidth / (code.length * 0.95f) else heightCap
                textSize = minOf(heightCap, widthCap).coerceAtLeast(36f)
            }

            val bounds = Rect()
            paint.getTextBounds(code, 0, code.length, bounds)
            // 垂直居中
            val textCenterY = centerY - bounds.exactCenterY()
            canvas.drawText(code, centerX, textCenterY, paint)

            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 生成分享卡片并写入 cache/share/，返回 FileProvider 可用的 content Uri。
     */
    fun writeShareImage(context: Context, code: String): android.net.Uri? {
        val bitmap = generateCard(context, code) ?: return null
        return try {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            // 文件名只用安全字符，避免口令里的中文/符号导致路径问题
            val safeName = "share_${System.currentTimeMillis()}.png"
            val file = File(dir, safeName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    /** 系统分享：图片 + 口令文案（[validMinutes] 用服务端返回的 expires_in，别写死） */
    fun shareCard(context: Context, code: String, scheduleName: String, validMinutes: Int): Boolean {
        val uri = writeShareImage(context, code) ?: return false
        val text = AndroidMiscStr.shareTextNN(scheduleName, code, validMinutes)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(
                android.content.Intent.createChooser(intent, AndroidMiscStr.shareScheduleChooser()).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}

// ── 4. 全流程：上传 → 复制口令 → 出图 → 唤起系统分享 ────────────

/** 上传课表生成趣味口令并唤起系统分享（确认后调用）；成功后自动复制口令到剪贴板 */
fun performScheduleShare(
    context: Context,
    scope: CoroutineScope,
    scheduleName: String,
    onSharingChanged: (Boolean) -> Unit = {},
) {
    val repository = CourseRepository.getInstance()
    val scheduleMap = buildShareScheduleMap(repository, scheduleName)
    if (scheduleMap == null) {
        showToast(AndroidMiscStr.scheduleEmptyNoShareN(scheduleName))
        return
    }
    onSharingChanged(true)
    scope.launch {
        try {
            showToast(AndroidMiscStr.generatingShareCode())
            val json = GsonBuilder().create().toJson(scheduleMap)
            ShareCodeApi.createShare(scheduleName, json).fold(
                onSuccess = { created ->
                    // 有效期以服务端 expires_in 为准，向上取整到分钟，至少 1
                    val validMinutes = ((created.expiresInSeconds + 59) / 60).coerceAtLeast(1)
                    copyShareCodeToClipboard(context, created.code)
                    val ok = ShareImageGenerator.shareCard(
                        context,
                        created.code,
                        created.scheduleName,
                        validMinutes,
                    )
                    if (ok) {
                        showToast(
                            AndroidMiscStr.codeCopiedValidN(created.code, validMinutes),
                            longDuration = true,
                        )
                    } else {
                        showToast(AndroidMiscStr.codeCopiedImageFailedN(created.code), longDuration = true)
                    }
                },
                onFailure = { e ->
                    showToast(AndroidMiscStr.shareFailedN(e.message ?: AndroidMiscStr.networkErrorShort()))
                }
            )
        } finally {
            onSharingChanged(false)
        }
    }
}
