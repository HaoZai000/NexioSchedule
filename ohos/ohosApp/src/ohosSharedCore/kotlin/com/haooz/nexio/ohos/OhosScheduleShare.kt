@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.jsonObjectOf
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonElement
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.data.util.toJsonElement
import com.haooz.chedule.i18n.L
import com.haooz.chedule.ui.utils.buildShareScheduleMap
import com.haooz.chedule.uishared.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint

/**
 * 鸿蒙的「分享课表」全流程（对应 Android `performScheduleShare` / iOS `IosScheduleShare`）。
 *
 * 上传与 JSON 走共享网络层；模板图用 skia 绘制；口令写系统剪贴板；
 * 图片与文案交给 ArkTS 的 ShareKit（`registerShareHost`）。
 */
object OhosScheduleShare {

    private const val BASE_URL = "http://182.92.193.223:3000/api/share"

    private val http: HttpService by lazy {
        createHttpService(HttpTimeouts(connectSeconds = 12, readSeconds = 20, callSeconds = 30))
    }

    // 模板常量与 Android / iOS 逐字一致
    private const val TEMPLATE_ASSET = "drawable/share_card_template.png"
    private const val CODE_BOX_LEFT = 121f
    private const val CODE_BOX_TOP = 600f
    private const val CODE_BOX_RIGHT = 1296f
    private const val CODE_BOX_BOTTOM = 775f

    fun perform(
        scheduleName: String,
        scope: CoroutineScope,
        onSharingChanged: (Boolean) -> Unit,
    ) {
        val scheduleMap = buildShareScheduleMap(CourseRepository.getInstance(), scheduleName)
        if (scheduleMap == null) {
            showToast(L("「%s」课表为空，无法分享", "\"%s\" is empty and cannot be shared")(scheduleName))
            return
        }
        onSharingChanged(true)
        scope.launch {
            try {
                showToast(L("正在生成分享口令…", "Generating share code…")())
                val json = toJsonElement(scheduleMap).toString()
                createShare(scheduleName, json).fold(
                    onSuccess = { created ->
                        val code = created.first
                        val validMinutes = created.second
                        ShareHostBridge.request("clipboard", code)
                        val ok = shareCard(code, scheduleName, validMinutes)
                        if (ok) {
                            showToast(
                                L(
                                    "口令已复制：「%s」%s 分钟内有效",
                                    "Code copied: \"%s\". Valid for %s minutes",
                                )(code, validMinutes),
                                longDuration = true,
                            )
                        } else {
                            showToast(
                                L("口令已复制「%s」，但打开分享面板失败", "Code copied: \"%s\", but opening the share sheet failed")(code),
                                longDuration = true,
                            )
                        }
                    },
                    onFailure = { e ->
                        showToast(L("分享失败：%s", "Share failed: %s")(e.message ?: L("网络错误", "Network error")()))
                    },
                )
            } finally {
                onSharingChanged(false)
            }
        }
    }

    private suspend fun createShare(
        scheduleName: String,
        scheduleJson: String,
    ): Result<Pair<String, Int>> = withContext(ioDispatcher) {
        try {
            val payload = jsonObjectOf(
                "schedule_name" to scheduleName,
                "schedule_data" to parseJsonElement(scheduleJson),
            ).toString()
            val resp = http.post(BASE_URL, payload)
            val text = resp.text
            if (!resp.isSuccessful) {
                val err = runCatching { parseJsonObject(text).optString("error") }.getOrDefault("")
                return@withContext Result.failure(
                    IllegalStateException(err.ifBlank { L("服务器错误 HTTP %s", "Server error HTTP %s")(resp.code) })
                )
            }
            val parsed = runCatching { parseJsonObject(text) }.getOrNull()
            val code = parsed?.optString("code").orEmpty()
            if (code.isBlank()) {
                return@withContext Result.failure(IllegalStateException(L("服务器未返回口令", "The server did not return a share code")()))
            }
            val validMinutes = ((parsed?.optInt("expires_in", 1800) ?: 1800) + 59) / 60
            Result.success(code to validMinutes.coerceAtLeast(1))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun shareCard(code: String, scheduleName: String, validMinutes: Int): Boolean {
        val png = try {
            renderCardPng(code)
        } catch (e: Exception) {
            NexioLog.e("Share", "出图失败：${e.message}", e)
            null
        } ?: return false
        val file = AppFiles.root.resolve("share_$code.png")
        file.writeBytes(png)
        val text = L(
            "我分享了课表「%s」\n口令：%s\n打开 Nexio课程表，用口令导入即可（%s 分钟内有效）",
            "I shared my schedule \"%s\"\nCode: %s\nOpen Nexio Schedule and import with this code (valid for %s minutes)",
        )(scheduleName, code, validMinutes)
        val payload = buildJsonObject {
            put("path", file.path)
            put("fileName", "share_$code.png")
            put("text", text)
        }.toString()
        return ShareHostBridge.request("share", payload)
    }

    /** 模板 + 口令 → PNG 字节（skia 绘制，尺寸推算与 Android/iOS 一致）。 */
    private suspend fun renderCardPng(code: String): ByteArray? {
        val templateBytes = Res.readBytes(TEMPLATE_ASSET)
        val template = Image.makeFromEncoded(templateBytes)
        val w = template.width
        val h = template.height
        if (w <= 0 || h <= 0) return null

        val bitmap = Bitmap().apply { allocN32Pixels(w, h) }
        val canvas = Canvas(bitmap)
        canvas.drawImage(template, 0f, 0f)

        val boxWidth = CODE_BOX_RIGHT - CODE_BOX_LEFT
        val boxHeight = CODE_BOX_BOTTOM - CODE_BOX_TOP
        val centerX = (CODE_BOX_LEFT + CODE_BOX_RIGHT) / 2f
        val centerY = (CODE_BOX_TOP + CODE_BOX_BOTTOM) / 2f
        val heightCap = boxHeight * 0.52f
        val widthCap = if (code.isNotEmpty()) boxWidth / (code.length * 0.95f) else heightCap
        val textSize = minOf(heightCap, widthCap).coerceAtLeast(36f)

        val typeface = FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD)
        val font = Font(typeface, textSize)
        val paint = Paint().apply { color = 0xFF1C1C1E.toInt() }

        val metrics = font.metrics
        val textCenterOffset = (metrics.ascent + metrics.descent) / 2f
        val baselineY = centerY - textCenterOffset
        val textWidth = font.measureTextWidth(code)
        canvas.drawString(code, centerX - textWidth / 2f, baselineY, font, paint)

        return Image.makeFromBitmap(bitmap).encodeToData()?.bytes
    }
}

/** 组合层便捷入口：与 iOS 的 `rememberIosShareAction` 同签名。 */
@Composable
actual fun rememberOhosShareAction(): (String, (Boolean) -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { name, changed -> OhosScheduleShare.perform(name, scope, changed) }
    }
}
