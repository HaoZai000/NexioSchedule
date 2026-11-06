// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.activities.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.haooz.chedule.data.appearance.AppearanceConfig
import com.haooz.chedule.ui.components.SoftGlassSurface
import com.haooz.chedule.ui.components.rememberSoftGlassSurface
import com.haooz.chedule.ui.glass.SoftGlassStyle
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.chedule.ui.glass.untransformGlassOrigin
import com.haooz.chedule.ui.navigation.LocalMainLayerTranslation

/**
 * 课表卡片的柔光样式：外观里的折射 / 模糊 / 边缘厚度 / 高光描边折算成一份 [SoftGlassStyle]。
 *
 * 网格卡片（静态壁纸采样）与长按/交换浮层（实时录制采样）都从这里取值 —— 采样源不同，
 * 但参数必须严格同一份，否则浮层一拖起来玻璃手感就和卡片不是一回事。
 */
@Composable
internal fun rememberScheduleGlassStyle(displayAppearance: AppearanceConfig): SoftGlassStyle {
    val glassRefractionScale = displayAppearance.cardRefraction.scale
    // 外观里的模糊本身就是 px，直接透传
    val glassBlurPx = displayAppearance.cardBlurRadius
    val glassEdgePx = ScheduleSoftGlassStyle.edgeWidth * displayAppearance.cardEdgeThickness.scale
    val glassEdgeHighlight = displayAppearance.cardRim
    return remember(glassRefractionScale, glassBlurPx, glassEdgePx, glassEdgeHighlight) {
        ScheduleSoftGlassStyle.copy(
            refractionScale = glassRefractionScale,
            smallBlurRadius = glassBlurPx,
            edgeWidth = glassEdgePx,
            edgeHighlight = glassEdgeHighlight,
        )
    }
}

/**
 * 浮层专用的压暗档：只改 [SoftGlassStyle.darker]，其余参数仍取 [rememberScheduleGlassStyle]。
 *
 * 为什么浮层需要单独一档：实时路径的大模糊底（`contentBig`）只能采控件自身矩形内的内容，
 * 而网格卡片采的是金字塔最粗一级 —— 整张壁纸的极粗平均。浮层压在亮色卡片上时，
 * 底色天然比卡片亮一档；`darker` 只在亮部区间（`darkerRange`）生效，正好压掉这一段，
 * 暗部与整体色相不动。取值对齐参考实现的大面板档位。
 */
private const val OVERLAY_GLASS_DARKER = 0.2f

/**
 * 长按跟手卡 / 交换飞行卡使用的柔光样式：与网格卡片同一份，仅压暗一档。
 *
 * 除 `darker` 外不要在这里加任何覆盖 —— 模糊、折射、边缘厚度、描边都属于
 * 「和卡片必须是同一块材质」的部分。
 */
@Composable
internal fun rememberOverlayGlassStyle(scheduleGlassStyle: SoftGlassStyle): SoftGlassStyle {
    return remember(scheduleGlassStyle) {
        scheduleGlassStyle.copy(darker = OVERLAY_GLASS_DARKER)
    }
}

/**
 * 课表页的柔光采样源：网格卡片、分界带、落点遮罩共用同一实例。
 *
 * 只在外壳创建一次 —— `prepareSoftGlassBackdrop` 会烘焙一整座壁纸金字塔，多建一份就是
 * 双倍显存 + 一次全尺寸像素处理；共用实例也让两者的玻璃参数不可能漂移。
 * 样式与坐标变换做成绘制期读的 State，改外观参数不重建 surface。
 *
 * ⚠ 长按/交换浮层**不用**这里：浮层要能录到它下面的课程卡片，只能走实时录制层
 * （见 `CourseCard(glassBackdrop = ...)`）；两者共用 [rememberScheduleGlassStyle] 的样式。
 */
@Composable
internal fun rememberScheduleGlassSurface(
    state: CourseScheduleAppState,
    scheduleGlassStyle: SoftGlassStyle,
    wallpaperImage: ImageBitmap?,
    originalWallpaperImage: ImageBitmap?,
    /** 无材质档传 false：卡片不挂玻璃，这里连壁纸金字塔都不烘焙 */
    enabled: Boolean = true,
): SoftGlassSurface? {
    // 无材质档：卡片走普通半透明表面，采样源一个都用不到 ——
    // 别为它白烘一座金字塔（一次整图降采样 + 7 级显存）
    if (!enabled) return null
    return with(state) {
        // 外观页开洞前显示已应用外观（原壁纸 / 原变换）
        val glassWallpaper =
            if (showCustomizePage && !isWindowCutoutActive) originalWallpaperImage else wallpaperImage
        val glassScale =
            if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale
        val glassOffset =
            if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset

        // 采样坐标系修正：卡片 origin 走 positionInWindow()，祖先 graphicsLayer 的缩放/平移
        // 会被算进去；texMap 却按未变换的壁纸层推仿射，必须还原到同一坐标系。
        // 动画值一律在 lambda 内读（绘制期订阅），避免逐帧重组整个外壳。
        val latestCutoutActive by rememberUpdatedState(isWindowCutoutActive)
        val latestCustomizeExiting by rememberUpdatedState(isCustomizeExiting)
        val latestCutoutCenterX by rememberUpdatedState(cutoutCenterXRatio)
        val latestCutoutCenterY by rememberUpdatedState(cutoutCenterYRatio)
        val glassOriginParams: State<FloatArray> = remember(
            cutoutMainScale, customizeExitScale, sheetOffsetY, screenWPx, screenHPx,
        ) {
            derivedStateOf {
                val cutout = cutoutMainScale.value
                val exitScale = if (latestCustomizeExiting) customizeExitScale.value else 1f
                // 与主内容层 graphicsLayer 的 effectiveScale 逐项一致
                val s = if (latestCustomizeExiting && latestCutoutActive) cutout else exitScale * cutout
                val sheetScaleProg = ((cutout - 0.65f) / 0.35f).coerceIn(0f, 1f)
                val ty = sheetOffsetY.value * (1f - sheetScaleProg)
                val px = if (latestCutoutActive) latestCutoutCenterX * screenWPx else screenWPx * 0.5f
                val py = when {
                    latestCutoutActive -> latestCutoutCenterY * screenHPx
                    latestCustomizeExiting -> screenHPx * 0.58f
                    else -> screenHPx * 0.5f
                }
                floatArrayOf(s, ty, px, py)
            }
        }
        // lambda 引用必须稳定：它是 rememberSoftGlassSurface 的 key，重建会连带重建每张卡的 modifier
        val mainLayerTranslation = LocalMainLayerTranslation.current
        val glassOriginTransform: (Offset) -> Offset = remember(
            glassOriginParams, mainLayerTranslation,
        ) {
            { p ->
                val v = glassOriginParams.value
                // 主界面转场是整层平移（壁纸也跟着走），所以窗口坐标要先减掉这段位移，
                // 采样坐标才恒等于「归位后」的值 —— 转场途中任何一次重绘都不会把纹理采歪，
                // 归位补帧也就不会出现「跳变归位」。普通字段，读它不订阅无效化。
                val unshifted = Offset(p.x - mainLayerTranslation.dxPx, p.y)
                untransformGlassOrigin(unshifted, v[0], v[1], v[2], v[3])
            }
        }
        rememberSoftGlassSurface(
            wallpaperBitmap = glassWallpaper,
            wallpaperScale = glassScale,
            wallpaperOffset = glassOffset,
            screenWidthPx = screenWPx,
            screenHeightPx = screenHPx,
            style = scheduleGlassStyle,
            originTransform = glassOriginTransform,
        )
    }
}
