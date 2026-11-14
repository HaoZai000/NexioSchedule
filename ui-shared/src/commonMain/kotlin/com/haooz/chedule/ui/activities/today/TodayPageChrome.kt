package com.haooz.chedule.ui.activities.today

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.activities.main.PageMoreMenuState
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop

/**
 * 今日页顶栏的宿主注入数据（从上游 1.7.0 搬过来）。
 *
 * 顶栏由页面自己组合，这里只保留页面算不出来的外部动作与几何参数；
 * 日期、是否今天等由页面内部状态直接读。
 */
class TodayPageChrome(
    val forcedDarkTheme: Boolean?,
    /** 共享壁纸采样层；页面会把「壁纸 + 本页内容」组合后给顶栏/菜单采样，避免采到自己 */
    val wallpaperBackdrop: GlassBackdrop? = null,
    /** 「更多」按钮/菜单宿主状态；null 表示不渲染 */
    val moreMenu: PageMoreMenuState? = null,
    /** 外层统一持有的本页内容采样层；null 时页面自建（独立使用 / 预览） */
    val contentBackdrop: LayerBackdrop? = null,
    val onJumpToDate: () -> Unit = {},
    val onEnterCustomize: () -> Unit = {},
    val onCourseManage: () -> Unit = {},
    val titleEndPadding: Dp = 0.dp,
    val blurResampleKey: Int = 0,
    val blurSampleTrack: () -> Float = { 0f },
    /** 本页内容采样层的录制指纹/强制重录谓词（与主内容层同源，滚动/动画时同步刷新） */
    val contentRecordKey: Any? = null,
    val contentMustRecord: (() -> Boolean)? = null,
)
