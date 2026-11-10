package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.AppLanguage

/**
 * 关于页需要的宿主信息：版本号 / 应用名 / 应用图标。
 *
 * 应用图标要「drawable → Bitmap → 裁掉 AdaptiveIcon 四周透明边 → ImageBitmap」，
 * Compose 没有跨平台等价物，只能留在 Android 侧（原 `AboutScreen.cropBitmapToOpaque`
 * 随迁移到 :ui-shared 已删除，此处复刻）。
 *
 * ⚠ 两个调用点共用本函数，别各写一份：全屏路由 `AndroidAboutRoute` 与平板设置右栏内嵌
 * [com.haooz.chedule.ui.settings.SettingsDirectPlatform]。
 */
@Immutable
data class AndroidAboutInfo(
    val version: String,
    val name: String,
    val icon: ImageBitmap?,
)

@Composable
fun rememberAndroidAboutInfo(): AndroidAboutInfo {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val icon = remember {
        runCatching { context.packageManager.getApplicationIcon(context.packageName) }
            .getOrNull()
            ?.let { drawable ->
                val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1)
                val bitmap = createBitmap(size, size)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(android.graphics.Canvas(bitmap))
                val pixels = IntArray(size * size)
                bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                var minX = size
                var minY = size
                var maxX = -1
                var maxY = -1
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        if (pixels[y * size + x] ushr 24 != 0) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
                val cropped = if (maxX >= minX && maxY >= minY &&
                    (maxX - minX + 1 != size || maxY - minY + 1 != size)
                ) {
                    android.graphics.Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1)
                } else {
                    bitmap
                }
                cropped.asImageBitmap()
            }
    }
    val version = remember(AppLanguage.resolved) {
        packageInfo?.versionName ?: AboutStr.unknownVersion()
    }
    val name = remember(AppLanguage.resolved) {
        packageInfo?.applicationInfo?.labelRes?.let { context.getString(it) }
            ?: context.applicationInfo?.nonLocalizedLabel?.toString()
            ?: AboutStr.appName()
    }
    return remember(version, name, icon) { AndroidAboutInfo(version = version, name = name, icon = icon) }
}
