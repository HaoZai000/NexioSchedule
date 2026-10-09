package com.haooz.chedule.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

/**
 * 「全屏文档页」的统一脚手架：折叠大标题顶栏 + 玻璃返回按钮 + 内容采样层。
 *
 * ## 为什么抽这个
 *
 * 原来十几个 Activity 壳的 `setContent` **逐字相同**，只差标题、Screen 函数、
 * 以及个别页面的额外元素（顶栏右侧按钮 / 底部悬浮按钮 / 弹窗）。抽出来之后，
 * 路由侧每页只剩「标题 + 内容」两个变量。
 *
 * ## 玻璃采样的层级（改之前先看这段）
 *
 * `backdrop` 是**内容层**的采样源，只包 [content]；顶栏的玻璃按钮必须放在**层外**，
 * 否则会自己采样自己形成循环。底部按钮/弹窗同理走 [overlay]（也在层外）。
 *
 * @param title 顶栏标题（折叠前的大标题同字）
 * @param onBack 返回回调。路由化之后传 `router::popBack`
 * @param endAction 顶栏右侧按钮（如节假日页的「从网络更新」、WebDAV 页的「测试连接」）。
 *   签名与 `CollapsibleTopAppBar.endAction` 一致
 * @param overlay 叠加在采样层**之外**的内容（底部玻璃按钮、弹窗等）。
 *   必须放层外 —— 玻璃控件被采样层包住会自己采自己
 * @param content 内容区。**必须把 [SharedScrollBehavior] 透给 Screen**，否则折叠标题不联动；
 *   需要玻璃采样的 Screen 也拿得到 `liquidGlassBackdrop`
 */
@Composable
fun DocumentPageScaffold(
    title: String,
    onBack: () -> Unit,
    endAction: (@Composable (backdropAlpha: Float, shadowAlpha: Float) -> Unit)? = null,
    overlay: (@Composable BoxScope.(liquidGlassBackdrop: com.kyant.backdrop.Backdrop) -> Unit)? = null,
    content: @Composable (
        scrollBehavior: SharedScrollBehavior,
        liquidGlassBackdrop: com.kyant.backdrop.Backdrop,
    ) -> Unit,
) {
    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val scrollBehavior = rememberSharedScrollBehavior()

    Scaffold(
        topBar = {
            ProgressiveBlurTopBar(
                backdrop = liquidGlassBackdrop,
            ) {
                CollapsibleTopAppBar(
                    title = title,
                    largeTitle = title,
                    modifier = Modifier,
                    scrollBehavior = scrollBehavior,
                    contentPadding = {},
                    startAction = { backdropAlpha, shadowAlpha ->
                        LiquidTopBarButton(
                            onClick = onBack,
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            performHapticFeedback = false,
                            iconSize = 25.dp,
                            iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                        )
                    },
                    endAction = endAction,
                )
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            // 采样层只包内容；顶栏玻璃按钮放在层外，避免循环采样
            Box(
                modifier = Modifier.fillMaxSize().then(
                    Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                )
            ) {
                content(scrollBehavior, liquidGlassBackdrop)
            }
            overlay?.invoke(this, liquidGlassBackdrop)
        }
    }
}
