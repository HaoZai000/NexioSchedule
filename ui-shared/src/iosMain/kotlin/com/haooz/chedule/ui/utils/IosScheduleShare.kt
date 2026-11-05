@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.platform.toNSData
import com.haooz.chedule.data.util.jsonObjectOf
import com.haooz.chedule.data.util.parseJsonElement
import com.haooz.chedule.data.util.parseJsonObject
import com.haooz.chedule.data.util.toJsonElement
import com.haooz.chedule.i18n.L
import com.haooz.chedule.uishared.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPopoverPresentationController
import platform.UIKit.popoverPresentationController

/**
 * iOS 的「分享课表」全流程（对应 Android `ScheduleExport.performScheduleShare`）。
 *
 * 流程：共享层读课表 → 序列化成 JSON → POST 口令服务（共享 `HttpService` = NSURLSession）
 * → 口令复制到剪贴板（`UIPasteboard`）→ 模板图绘口令 → PNG → `UIActivityViewController` 分享。
 *
 * 上传 + JSON 是跨平台的，但 Android 现有 `ShareCodeApi` 还在用 OkHttp + JSONObject；
 * 为 Android 零回归，这里仅 iOS 侧重写、复用共享网络与 JSON 层，不动 Android 那份。
 *
 * 出图必须用 skia：iOS 没有 `android.graphics`，在模板 1417×1417、口令框 (121,600)-(1296,775)
 * 上绘字，常量与 Android `ShareImageGenerator` 逐字一致，保证两端观感一致。
 */
object IosScheduleShare {

    private const val BASE_URL = "http://182.92.193.223:3000/api/share"

    // 与 Android ShareCodeApi 的连接池配置对齐
    private val http: HttpService by lazy {
        createHttpService(HttpTimeouts(connectSeconds = 12, readSeconds = 20, callSeconds = 30))
    }

    // ── 模板常量：与 Android ShareImageGenerator 逐字一致 ──
    private const val TEMPLATE_ASSET = "drawable/share_card_template.png"
    private const val CODE_BOX_LEFT = 121f
    private const val CODE_BOX_TOP = 600f
    private const val CODE_BOX_RIGHT = 1296f
    private const val CODE_BOX_BOTTOM = 775f

    /**
     * 全流程：上传课表生成口令并唤起系统分享。
     *
     * @param scope 调用方（组合层）的协程作用域
     * @param onSharingChanged 分享进行中标志（页面据此禁用按钮）
     */
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
                        UIPasteboard.generalPasteboard.string = code
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
                            showToast(L("口令已复制「%s」，但生成图片失败", "Code copied: \"%s\", but generating the image failed")(code), longDuration = true)
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

    /** 上传课表创建分享口令；失败返回带原文案原因的 failure（与 Android 同一套文案） */
    private suspend fun createShare(
        scheduleName: String,
        scheduleJson: String,
    ): Result<Pair<String, Int>> =
        withContext(ioDispatcher) {
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
                // 有效期以服务端 expires_in 为准（与 Android 同口径），别再写死 30 分钟
                val validMinutes = ((parsed?.optInt("expires_in", 1800) ?: 1800) + 59) / 60
                Result.success(code to validMinutes.coerceAtLeast(1))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 在模板上绘出口令并唤起系统分享面板；返回是否成功拉起 */
    private suspend fun shareCard(code: String, scheduleName: String, validMinutes: Int): Boolean {
        val png = try {
            renderCardPng(code)
        } catch (e: Exception) {
            NexioLog.e("Share", "出图失败：${e.message}", e)
            null
        } ?: return false
        return presentShareSheet(png, code, scheduleName, validMinutes)
    }

    /** 模板 + 口令 → PNG 字节（skia 绘制） */
    private suspend fun renderCardPng(code: String): ByteArray? {
        val templateBytes = Res.readBytes(TEMPLATE_ASSET)
        val template = Image.makeFromEncoded(templateBytes)
        val w = template.width
        val h = template.height
        if (w <= 0 || h <= 0) return null

        val bitmap = Bitmap().apply {
            allocN32Pixels(w, h)
        }
        val canvas = Canvas(bitmap)
        canvas.drawImage(template, 0f, 0f)

        // 与 Android 同一套尺寸推算：先按框高给上限，再按字数收缩，下限 36
        val boxWidth = CODE_BOX_RIGHT - CODE_BOX_LEFT
        val boxHeight = CODE_BOX_BOTTOM - CODE_BOX_TOP
        val centerX = (CODE_BOX_LEFT + CODE_BOX_RIGHT) / 2f
        val centerY = (CODE_BOX_TOP + CODE_BOX_BOTTOM) / 2f
        val heightCap = boxHeight * 0.52f
        val widthCap = if (code.isNotEmpty()) boxWidth / (code.length * 0.95f) else heightCap
        val textSize = minOf(heightCap, widthCap).coerceAtLeast(36f)

        // skiko 没有 makeFromName/makeDefault（实测 Unresolved），走 FontMgr 匹配：
        // 传 null 家族名 = 系统默认字体，只指定 bold 字重
        val typeface = FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD)
        val font = Font(typeface, textSize)
        val paint = Paint().apply { color = 0xFF1C1C1E.toInt() }

        // 垂直居中：skia 用 metrics 的 ascent/descent 算中心，对应 Android 的 exactCenterY
        val metrics = font.metrics
        val textCenterOffset = (metrics.ascent + metrics.descent) / 2f
        val baselineY = centerY - textCenterOffset
        val textWidth = font.measureTextWidth(code)
        canvas.drawString(code, centerX - textWidth / 2f, baselineY, font, paint)

        return Image.makeFromBitmap(bitmap).encodeToData()?.bytes
    }

    /** PNG 字节 → UIImage → 系统分享面板；文本与 Android 的分享文案逐字一致 */
    private fun presentShareSheet(
        png: ByteArray,
        code: String,
        scheduleName: String,
        validMinutes: Int,
    ): Boolean {
        val host = IosUiHost.topViewController() ?: return false
        val tmpPath = NSTemporaryDirectory() + "share_$code.png"
        val nsData = png.toNSData()
        if (!nsData.writeToFile(tmpPath, atomically = true)) return false
        val image = UIImage.imageWithContentsOfFile(tmpPath) ?: return false

        val text = L(
            "我分享了课表「%s」\n口令：%s\n打开 Nexio课程表，用口令导入即可（%s 分钟内有效）",
            "I shared my schedule \"%s\"\nCode: %s\nOpen Nexio Schedule and import with this code (valid for %s minutes)"
        )(scheduleName, code, validMinutes)
        val controller = UIActivityViewController(
            activityItems = listOf(image, text),
            applicationActivities = null,
        )
        // iPad 上 UIActivityViewController 是 popover，不给锚点会直接崩
        controller.popoverPresentationController?.let { popover ->
            popover.setSourceView(host.view)
            val bounds = host.view.bounds
            popover.setSourceRect(bounds)
        }
        host.presentViewController(controller, animated = true, completion = null)
        return true
    }
}

/**
 * 组合层拿分享入口的便捷封装：内部记住作用域，页面侧只写
 * `onShareSchedule = { name, changed -> rememberIosShareAction()(name, changed) }` 这种形态。
 */
@Composable
fun rememberIosShareAction(): (String, (Boolean) -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { name, changed -> IosScheduleShare.perform(name, scope, changed) }
    }
}
