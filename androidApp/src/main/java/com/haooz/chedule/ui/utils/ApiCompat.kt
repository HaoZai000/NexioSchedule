package com.haooz.chedule.ui.utils

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Size
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * minSdk 26 兼容层。
 *
 * 应用最低支持 Android 8.0（API 26），但代码里用到了 28~36 才有的 API
 * （RenderEffect 模糊、WindowMetrics、SigningInfo、canScheduleExactAlarms 等）。
 * 这些能力全部收敛到本文件做「存在性检测 + 降级」，低版本一律降级为
 * 「该效果不生效」，绝不抛 NoSuchMethodError / NoSuchFieldError。
 *
 * 约定：调用方拿到 null / false 即表示当前系统不具备该能力，按「无效果」处理。
 */
object ApiCompat {

    // ===== 能力存在性 =====

    /** PackageInfo.signingInfo / longVersionCode 需要 API 28。 */
    val isSigningInfoAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 28

    // ===== RenderEffect 高斯模糊（API 31+）=====

    /**
     * 创建高斯模糊的 Compose RenderEffect。
     * API 31 以下或半径非正数时返回 null（调用方按「不模糊」处理）。
     */
    fun blurRenderEffect(radiusPx: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < 31 || radiusPx <= 0f) return null
        return runCatching {
            android.graphics.RenderEffect.createBlurEffect(
                radiusPx,
                radiusPx,
                android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }.getOrNull()
    }

    /** 取窗口圆角半径（像素）。API 31 以下或取不到时返回 0f。 */
    fun windowCornerRadius(window: Window): Float {
        if (Build.VERSION.SDK_INT < 31) return 0f
        return runCatching {
            @SuppressLint("WrongConstant")
            window.decorView.rootWindowInsets.getRoundedCorner(0)?.radius?.toFloat() ?: 0f
        }.getOrDefault(0f)
    }

    // ===== 窗口尺寸（API 30+ / 旧版 Display 回退）=====

    /** 取当前窗口尺寸。API 30 以下回退到 Display.getRealSize / displayMetrics。 */
    fun currentWindowSize(context: Context): Size {
        if (Build.VERSION.SDK_INT >= 30) {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            if (windowManager != null) {
                runCatching { windowManager.currentWindowMetrics.bounds }
                    .getOrNull()
                    ?.let { return Size(it.width(), it.height()) }
            }
        }
        return legacyWindowSize(context)
    }

    private fun legacyWindowSize(context: Context): Size {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        if (windowManager != null) {
            val point = android.graphics.Point()
            var got = false
            @Suppress("DEPRECATION")
            runCatching { windowManager.defaultDisplay.getSize(point) }
                .onSuccess { got = true }
            if (got && point.x > 0 && point.y > 0) return Size(point.x, point.y)
        }
        val metrics = context.resources.displayMetrics
        return Size(metrics.widthPixels, metrics.heightPixels)
    }

    // ===== 状态栏/导航栏外观（API 30+ / 旧版 systemUiVisibility 回退）=====

    /**
     * 设置系统栏图标明暗。
     *
     * API 30+ 走 [android.view.WindowInsetsController.setSystemBarsAppearance]；
     * API 26~29 回退到等价的 systemUiVisibility 标志位
     * （LIGHT_STATUS_BAR API 23 / LIGHT_NAVIGATION_BAR API 26，minSdk 26 均有）。
     * 两套标志都带 edge-to-edge 副作用，故回退路径只增删本方法负责的那一位，
     * 不整体覆写 decorView.systemUiVisibility。
     */
    fun setSystemBarsAppearance(window: Window, appearance: Int, mask: Int) {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { window.insetsController?.setSystemBarsAppearance(appearance, mask) }
            return
        }
        @Suppress("DEPRECATION")
        val legacyFlag = when (mask) {
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS -> View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS -> View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else -> return
        }
        val light = appearance and mask != 0
        @Suppress("DEPRECATION")
        val decor = window.decorView
        @Suppress("DEPRECATION")
        val updated = if (light) {
            decor.systemUiVisibility or legacyFlag
        } else {
            decor.systemUiVisibility and legacyFlag.inv()
        }
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = updated
    }

    // ===== 精准闹钟（API 31+）=====

    /** 是否已获得「精准闹钟」授权。API 31 以下该权限不存在，恒为 true。 */
    fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    // ===== 版本号（API 28+ longVersionCode）=====

    /** 取 longVersionCode。API 28 以下读已废弃的 versionCode。 */
    fun longVersionCode(packageInfo: PackageInfo): Long =
        if (isSigningInfoAvailable) {
            runCatching { packageInfo.longVersionCode }.getOrDefault(0L)
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
}