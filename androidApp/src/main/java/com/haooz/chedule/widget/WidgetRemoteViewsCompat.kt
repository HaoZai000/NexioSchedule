/** 小组件 RemoteViews 的高版本 API 兼容层。
 *
 * RemoteViews.setViewLayoutWidth / setViewLayoutHeight / setViewLayoutMargin 需要
 * API 31，低版本桌面（Launcher）直接抛 NoSuchMethodError 会让整个小部件更新失败。
 * 这里统一收口，低版本静默跳过（尺寸回落到布局文件里写的值），保证小组件至少能刷新。
 */
package com.haooz.chedule.widget

import android.os.Build
import android.widget.RemoteViews

/** RemoteViews 尺寸/边距设置能力（API 31+）。 */
internal val supportsRemoteViewsLayout: Boolean
    get() = Build.VERSION.SDK_INT >= 31

/** API 31+：按单位设置子 View 宽度。低版本跳过。 */
internal fun RemoteViews.setLayoutWidthCompat(viewId: Int, value: Float, unit: Int) {
    if (!supportsRemoteViewsLayout) return
    setViewLayoutWidth(viewId, value, unit)
}

/** API 31+：按单位设置子 View 高度。低版本跳过。 */
internal fun RemoteViews.setLayoutHeightCompat(viewId: Int, value: Float, unit: Int) {
    if (!supportsRemoteViewsLayout) return
    setViewLayoutHeight(viewId, value, unit)
}

/** API 31+：按单位设置子 View 边距。低版本跳过。 */
internal fun RemoteViews.setLayoutMarginCompat(viewId: Int, type: Int, value: Float, unit: Int) {
    if (!supportsRemoteViewsLayout) return
    setViewLayoutMargin(viewId, type, value, unit)
}