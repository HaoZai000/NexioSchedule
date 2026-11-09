/** 小部件的 Material You 取色：系统动态色落到 RemoteViews 的 tint 与文字色上 */
package com.haooz.chedule.widget

import android.app.WallpaperManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.KEY_CHANGED_ANYWHERE
import com.haooz.chedule.ui.theme.KEY_MONET_COLOR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetMonetColors {
    private const val THEME_PREFS = "app_theme_prefs"
    private const val WALLPAPER_CARD_ALPHA = 0xD0

    data class Palette(
        val surface: Int,
        val card: Int,
        val activeCard: Int,
        val title: Int,
        val secondary: Int,
        val accent: Int,
    ) {
        /** 进内容签名：换壁纸后色值变了，组件才不会被 shouldSkip 跳过。 */
        fun signature(): String = listOf(surface, card, activeCard, title, secondary, accent)
            .joinToString(":") { Integer.toHexString(it) }
    }

    private fun switchEnabled(): Boolean =
        AppStorage.store(THEME_PREFS).getBoolean(KEY_MONET_COLOR, false)

    /** 复用应用内「莫奈取色」开关，关闭或低于 Android 12 时完全不介入。 */
    fun palette(context: Context, dark: Boolean): Palette? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (!switchEnabled()) return null
        return runCatching {
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            Palette(
                surface = scheme.surfaceContainer.toArgb(),
                card = scheme.surfaceContainerLow.toArgb(),
                activeCard = scheme.primaryContainer.toArgb(),
                title = scheme.onSurface.toArgb(),
                secondary = scheme.onSurfaceVariant.toArgb(),
                accent = scheme.primary.toArgb(),
            )
        }.getOrNull()
    }

    fun signature(palette: Palette?): String = palette?.signature() ?: "off"

    /** 圆角 shape 用 tint 换色；setBackgroundColor 会把 drawable 的圆角抹掉。 */
    fun tintBackground(views: RemoteViews, id: Int, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(color))
        }
    }

    /** 有壁纸时卡片留一点透明度，照片仍透得出来。 */
    fun cardColor(palette: Palette, active: Boolean, hasWallpaper: Boolean): Int {
        val color = if (active) palette.activeCard else palette.card
        return if (hasWallpaper) (color and 0x00FFFFFF) or (WALLPAPER_CARD_ALPHA shl 24) else color
    }

    /** 标题与课名走 onSurface，倒计时走 primary，其余走 onSurfaceVariant。 */
    fun textColor(palette: Palette, entry: String): Int = when {
        entry == "widget_week" ||
            entry.startsWith("widget_title") ||
            entry.startsWith("widget_header") ||
            entry.startsWith("widget_name") ||
            entry == "widget_course_name" -> palette.title

        entry.startsWith("widget_now") -> palette.accent
        else -> palette.secondary
    }

    /** 开关切换或系统壁纸取色变化时立刻重刷小部件。 */
    fun installRefresh(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val app = context.applicationContext
        val refresh = {
            CoroutineScope(Dispatchers.IO).launch {
                WidgetUpdateCache.clear()
                WidgetUpdateCache.updateInstalledWidgets(app)
            }
        }
        AppStorage.store(THEME_PREFS).registerListener { key ->
            if (key == KEY_MONET_COLOR || key == KEY_CHANGED_ANYWHERE) refresh()
        }
        runCatching {
            WallpaperManager.getInstance(app).addOnColorsChangedListener(
                WallpaperManager.OnColorsChangedListener { _, _ -> if (switchEnabled()) refresh() },
                Handler(Looper.getMainLooper()),
            )
        }
    }
}
