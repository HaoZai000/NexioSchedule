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
import java.io.File
import java.io.FileOutputStream

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

    /** 系统分享：图片 + 口令文案 */
    fun shareCard(context: Context, code: String, scheduleName: String): Boolean {
        val uri = writeShareImage(context, code) ?: return false
        val text = buildString {
            append("我分享了课表「").append(scheduleName).append("」\n")
            append("口令：").append(code).append("\n")
            append("打开 Nexio课程表，用口令导入即可（30 分钟内有效）")
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(
                android.content.Intent.createChooser(intent, "分享课表").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
