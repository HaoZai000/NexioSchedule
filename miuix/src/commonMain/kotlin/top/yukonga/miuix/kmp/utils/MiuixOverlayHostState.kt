// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.utils

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.material.GlassSurfaceAlphaDrop
import top.yukonga.miuix.kmp.material.LocalGlassBlurDp
import top.yukonga.miuix.kmp.material.LocalGlassDialogBlurDp
import top.yukonga.miuix.kmp.material.LocalGlassPanelBlurDp
import top.yukonga.miuix.kmp.material.LocalGlassSurfaceAlphaDrop
import top.yukonga.miuix.kmp.material.LocalMaterialNoneEnabled
import top.yukonga.miuix.kmp.material.LocalMiuixTextMotion
import top.yukonga.miuix.kmp.material.LocalSoftGlassLayer
import top.yukonga.miuix.kmp.material.SoftGlassLayer
import top.yukonga.miuix.kmp.material.LocalUseFakeProgressiveBlur
import top.yukonga.miuix.kmp.material.MiuixTextMotion
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.LocalAppSettingDark
import top.yukonga.miuix.kmp.theme.LocalColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalColors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.LocalForcedDarkTheme
import top.yukonga.miuix.kmp.theme.LocalMonetColorEnabled
import top.yukonga.miuix.kmp.theme.LocalTextStyles
import top.yukonga.miuix.kmp.theme.TextStyles

/**
 * A root-scoped registry shared by registration and rendering compositions.
 *
 * Install the same instance in both roots with [MiuixOverlayHostProvider]. Render
 * [MiuixPopupUtils.Companion.MiuixPopupHost] only in the upper root, directly inside
 * the provider rather than a nested Scaffold. Scaffold-local overlays remain local.
 */
@Stable
class MiuixOverlayHostState {
    internal val dialogs = mutableStateListOf<MiuixPopupUtils.DialogState>()
    internal val popups = mutableStateListOf<MiuixPopupUtils.PopupState>()

    /** Idle registrations do not block input; entering and exiting entries do. */
    val isActive: Boolean
        get() = dialogs.any { it.showState.value || it.isAnimating } ||
            popups.any { it.showState.value || it.isAnimating }
}

/** Provides a shared registry without replacing platform owners or window locals. */
@Composable
fun MiuixOverlayHostProvider(
    state: MiuixOverlayHostState,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSharedOverlayHost provides state,
        LocalDialogStates provides state.dialogs,
        LocalPopupStates provides state.popups,
        LocalRootDialogStates provides state.dialogs,
        LocalRootPopupStates provides state.popups,
        content = content,
    )
}

internal val LocalSharedOverlayHost = staticCompositionLocalOf<MiuixOverlayHostState?> { null }

internal fun overlayEntryIsActive(
    show: Boolean,
    pendingOpen: Boolean,
    contentCurrent: Boolean,
    contentTarget: Boolean,
    contentIdle: Boolean,
    dimCurrent: Boolean,
    dimTarget: Boolean,
    dimIdle: Boolean,
): Boolean = show || pendingOpen || contentCurrent || contentTarget || !contentIdle ||
    dimCurrent || dimTarget || !dimIdle

/**
 * Only portable theme and component settings travel with an entry. Density,
 * keyboard, focus, accessibility and window owners come from the rendering root.
 */
internal data class MiuixOverlayTheme(
    val colors: Colors,
    val textStyles: TextStyles,
    val colorSchemeMode: ColorSchemeMode?,
    val contentColor: Color,
    val indication: Indication,
    val overscrollFactory: OverscrollFactory?,
    val appDark: Boolean?,
    val forcedDark: Boolean?,
    /**
     * 宿主是否在用系统壁纸取色。必须随主题一起快照：弹窗内容（BlurBottomSheet 等）
     * 是在 root host 位置渲染的，那里读不到声明页面的 CompositionLocal，
     * 漏传就会退回默认 false → 弹窗里的莫奈分支重新变成死代码。
     */
    val monetColorEnabled: Boolean,
    val dismiss: (() -> Unit)?,
    /** 无材质档快照：玻璃关 + 渐进模糊走渐变遮罩，root host 读不到声明页面的 CompositionLocal。 */
    val materialNoneEnabled: Boolean,
    val useFakeProgressiveBlur: Boolean,
    /** 玻璃件模糊半径（dp）快照：root host 读不到声明页面的 CompositionLocal。 */
    val glassBlurDp: Float,
    /** 下拉面板模糊半径（dp）快照，同上。 */
    val glassPanelBlurDp: Float,
    /** 对话框 / 贴底弹窗模糊半径（dp）快照，同上。 */
    val glassDialogBlurDp: Float,
    /** 玻璃底色 alpha 降幅快照（柔光玻璃档 >0，其余 0），同上。 */
    val glassSurfaceAlphaDrop: GlassSurfaceAlphaDrop,
    /** 描边降级解析器快照，同上：弹窗描边在 root host 算，读不到声明页面的 CompositionLocal。 */
    /** 柔光玻璃注入快照，同上：弹窗面板在 root host 渲染。 */
    val softGlassLayer: SoftGlassLayer?,
    /**
     * 换字动效宿主快照，同上：弹窗在 root host 渲染，读不到声明页面的 CompositionLocal。
     * 漏传的表现是「页面里的文字换字有模糊，弹窗标题 / 摘要却硬切」。
     */
    val textMotion: MiuixTextMotion?,
    val predictiveBackEnabled: Boolean,
) {
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalTextStyles provides textStyles,
            LocalColorSchemeMode provides colorSchemeMode,
            LocalContentColor provides contentColor,
            LocalIndication provides indication,
            LocalOverscrollFactory provides overscrollFactory,
            LocalAppSettingDark provides appDark,
            LocalForcedDarkTheme provides forcedDark,
            LocalMonetColorEnabled provides monetColorEnabled,
            LocalDismissState provides dismiss,
            LocalMaterialNoneEnabled provides materialNoneEnabled,
            LocalUseFakeProgressiveBlur provides useFakeProgressiveBlur,
            LocalGlassBlurDp provides glassBlurDp,
            LocalGlassPanelBlurDp provides glassPanelBlurDp,
            LocalGlassDialogBlurDp provides glassDialogBlurDp,
            LocalGlassSurfaceAlphaDrop provides glassSurfaceAlphaDrop,
            LocalSoftGlassLayer provides softGlassLayer,
            LocalMiuixTextMotion provides textMotion,
            LocalPredictiveBackEnabled provides predictiveBackEnabled,
            content = content,
        )
    }
}

@Composable
internal fun currentMiuixOverlayTheme(): MiuixOverlayTheme = MiuixOverlayTheme(
    colors = LocalColors.current,
    textStyles = LocalTextStyles.current,
    colorSchemeMode = LocalColorSchemeMode.current,
    contentColor = LocalContentColor.current,
    indication = LocalIndication.current,
    overscrollFactory = LocalOverscrollFactory.current,
    appDark = LocalAppSettingDark.current,
    forcedDark = LocalForcedDarkTheme.current,
    monetColorEnabled = LocalMonetColorEnabled.current,
    dismiss = LocalDismissState.current,
    materialNoneEnabled = LocalMaterialNoneEnabled.current,
    useFakeProgressiveBlur = LocalUseFakeProgressiveBlur.current,
    glassBlurDp = LocalGlassBlurDp.current,
    glassPanelBlurDp = LocalGlassPanelBlurDp.current,
    glassDialogBlurDp = LocalGlassDialogBlurDp.current,
    glassSurfaceAlphaDrop = LocalGlassSurfaceAlphaDrop.current,
    softGlassLayer = LocalSoftGlassLayer.current,
    textMotion = LocalMiuixTextMotion.current,
    predictiveBackEnabled = LocalPredictiveBackEnabled.current,
)
