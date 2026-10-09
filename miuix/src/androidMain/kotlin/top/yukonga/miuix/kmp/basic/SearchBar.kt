// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import android.annotation.SuppressLint
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.edgelight.rememberDefaultEdgeLight
import com.kyant.backdrop.edgelight.isSystemLightTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import top.yukonga.miuix.kmp.material.LocalChromeLensEnabled
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.hasFocusReassignBug
import kotlin.time.Duration.Companion.milliseconds

/**
 * 搜索框组件
 *
 * @param inputField 输入框组件
 * @param onExpandedChange 展开状态变化回调
 * @param modifier 修饰符
 * @param insideMargin 内边距
 * @param expanded 是否展开显示搜索结果
 * @param actionIcon 展开时右侧显示的图标
 * @param actionIconSize 图标大小
 * @param onActionClick 图标点击回调
 * @param content 展开时显示的内容
 */
@Composable
fun SearchBar(
    inputField: @Composable () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = SearchBarDefaults.InsideMargin,
    expanded: Boolean = false,
    actionIcon: ImageVector? = null,
    actionIconSize: Dp = 23.dp,
    onActionClick: (() -> Unit)? = null,
    backdrop: Backdrop? = null,
    backdropAlpha: Float = 0f,
    /**
     * 是否启用 chrome 透镜（折射）。原读 App 的 AppMaterialSettings.chromeLensEnabled()，
     * 那是 App 的材质档位偏好；默认改为从 [LocalChromeLensEnabled] 读，
     * App 在根部 provide，行为与迁移前一致。
     */
    chromeLensEnabled: Boolean = LocalChromeLensEnabled.current,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // 折射随材质档位：最佳保留，均衡及更省档关闭（与液态玻璃按钮一致）
    val chromeLens = chromeLensEnabled

    Column(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = insideMargin.height, horizontal = insideMargin.width),
            ) {
                inputField()
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
            ) {
                val scale by transition.animateFloat(
                    transitionSpec = { tween(durationMillis = 300) },
                    label = "scale"
                ) { enterExit ->
                    if (enterExit == EnterExitState.Visible) 1f else 0.6f
                }
                val blur by transition.animateDp(
                    transitionSpec = { tween(durationMillis = 300) },
                    label = "blur"
                ) { enterExit ->
                    if (enterExit == EnterExitState.Visible) 0.dp else 7.dp
                }

                if (actionIcon != null && onActionClick != null) {

                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(SearchBarDefaults.InputFieldMinHeight)
                            .blur(blur)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = scale
                            }
                            .clip(CircleShape)
                            .clickable { onActionClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (backdrop != null) {
                            // 记得住引用：drawBackdrop 按引用比较 effects，每帧新建会导致重新录制采样层
                            val actionEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(chromeLens) {
                                {
                                    vibrancy()
                                    blur(8f.dp.toPx())
                                    if (chromeLens) lens(14f.dp.toPx(), 14f.dp.toPx())
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { CircleShape },
                                        effects = actionEffects,
                                        highlight = null,
                                        shadow = null,
                                        layerBlock = { alpha = backdropAlpha },
                                        onDrawSurface = {}
                                    )
                                    .edgeLight(shape = CircleShape, edgeLight = rememberDefaultEdgeLight(baseColor = MiuixTheme.colorScheme.surfaceContainerHigh))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    color = if (backdrop != null) {
                                        MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                                    } else MiuixTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                modifier = Modifier.size(actionIconSize),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MiuixTheme.colorScheme.surface,
                )
                .clip(ContinuousRoundedRectangle(20.dp))
        ) {
            content()
        }
    }

    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = expanded,
        onBackCompleted = {
            currentOnExpandedChange(false)
        },
    )
}

/**
 * 搜索输入框组件
 *
 * @param query 当前查询文本
 * @param onQueryChange 查询文本变化回调
 * @param onSearch 搜索触发回调
 * @param expanded 是否展开
 * @param onExpandedChange 展开状态变化回调
 * @param modifier 修饰符
 * @param label 输入框未聚焦时显示的提示文本
 * @param enabled 是否启用
 * @param textStyle 文本样式
 * @param leadingIcon 前置图标
 * @param trailingIcon 后置图标
 * @param interactionSource 交互源
 */
@SuppressLint("UseKtx")
@Composable
fun InputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    backdrop: Backdrop? = null,
    backdropAlpha: Float = 1f,
    shadowAlpha: Float = 0f,
    /** 同 [SearchBar]：chrome 透镜开关，由 App 的材质档位注入（LocalChromeLensEnabled）。 */
    chromeLensEnabled: Boolean = LocalChromeLensEnabled.current,
    /**
     * 当前是否浅色主题。原读 App 的 isAppDarkTheme()。
     *
     * 传 () -> Boolean 而不是直接传 Boolean：调用点在 decorationBox 里取值，
     * 需要的是「每帧现读」而非组合期快照。
     * 为 null 时退回系统深浅色（组合期取一次）；App 若有「强制浅/深」偏好，
     * 传入自己的判断即可覆盖 —— 迁移前 InputField 内部读的就是 isAppDarkTheme()。
     */
    isLightTheme: (() -> Boolean)? = null,
) {
    // 系统主题要在组合期取一次（isSystemLightTheme 是 @Composable，不能塞进 lambda）
    val systemIsLightTheme = isSystemLightTheme()
    val resolveIsLightTheme: () -> Boolean = isLightTheme ?: { systemIsLightTheme }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val internalInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val capsuleShape = ContinuousCapsule()
    // 折射随材质档位：最佳保留，均衡及更省档关闭
    val chromeLens = chromeLensEnabled

    val actualLeadingIcon = leadingIcon ?: {
        Box(
            modifier = Modifier.padding(start = SearchBarDefaults.LeadingIconStartPadding, end = SearchBarDefaults.LeadingIconEndPadding)
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = MiuixIcons.Basic.Search,
                tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                contentDescription = "搜索",
            )
        }
    }

    val actualTrailingIcon = trailingIcon ?: {
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier.padding(start = SearchBarDefaults.TrailingIconStartPadding, end = SearchBarDefaults.TrailingIconEndPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    modifier = Modifier
                        .clip(capsuleShape)
                        .clickable { currentOnQueryChange("") },
                    imageVector = MiuixIcons.Basic.SearchCleanup,
                    tint = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                    contentDescription = "清除",
                )
            }
        }
    }

    val focused = internalInteractionSource.collectIsFocusedAsState().value
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val textAlpha = remember { Animatable(1f) }

    val textColor = LocalContentColor.current
    val inputTextStyle = MiuixTheme.textStyles.main
        .copy(fontWeight = FontWeight.Medium)
        .merge(textStyle)
        .copy(color = textColor)

    val cursorBrush = SolidColor(MiuixTheme.colorScheme.primary)
    val labelText by remember(query, expanded, label) {
        derivedStateOf { if (!(query.isNotEmpty() || expanded)) label else "" }
    }

    // API 26-27 的 bug 修复：收起时禁用 TextField 防止焦点错误重分配
    val workaroundEnabled = !hasFocusReassignBug || expanded
    val expandOnTapModifier = if (workaroundEnabled || !enabled) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) { detectTapGestures { currentOnExpandedChange(true) } }
    }

    BasicTextField(
        value = query,
        onValueChange = currentOnQueryChange,
        modifier = modifier
            .then(expandOnTapModifier)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) currentOnExpandedChange(true) }
            .semantics {
                onClick {
                    focusRequester.requestFocus()
                    true
                }
            },
        enabled = enabled && workaroundEnabled,
        singleLine = true,
        textStyle = inputTextStyle,
        cursorBrush = cursorBrush,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { currentOnSearch(query) }),
        interactionSource = internalInteractionSource,
        decorationBox = { innerTextField ->
            val isLightTheme = resolveIsLightTheme()
            val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.76f)
                else Color(0xFF242424).copy(0.84f)
            val shadowColor = if (isLightTheme) android.graphics.Color.parseColor("#12000000")
                else android.graphics.Color.parseColor("#20000000")
            // 记得住引用：drawBackdrop 按引用比较 effects，每帧新建会导致重新录制采样层
            val containerEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(chromeLens) {
                {
                    vibrancy()
                    blur(8f.dp.toPx())
                    if (chromeLens) lens(8f.dp.toPx(), 24f.dp.toPx())
                }
            }

            Box(
                modifier = Modifier
                    .then(
                        if (backdrop != null) {
                            Modifier.drawBehind {
                                if (shadowAlpha > 0.01f) {
                                    val maxBlurRadius = 10f * density
                                    val blurRadius = maxBlurRadius * shadowAlpha
                                    val composePath = Path().apply {
                                        val outline = capsuleShape.createOutline(size, layoutDirection, this@drawBehind)
                                        when (outline) {
                                            is Outline.Rounded -> addRoundRect(outline.roundRect)
                                            is Outline.Generic -> addPath(outline.path)
                                            is Outline.Rectangle -> addRect(outline.rect)
                                        }
                                    }
                                    val path = composePath.asAndroidPath()
                                    val paint = Paint().apply {
                                        color = android.graphics.Color.argb(
                                            (android.graphics.Color.alpha(shadowColor) * 1f).coerceAtMost(255f).toInt(),
                                            android.graphics.Color.red(shadowColor),
                                            android.graphics.Color.green(shadowColor),
                                            android.graphics.Color.blue(shadowColor)
                                        )
                                        maskFilter = BlurMaskFilter(
                                            blurRadius.coerceAtLeast(0.1f),
                                            BlurMaskFilter.Blur.NORMAL
                                        )
                                    }
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawPath(path, paint)
                                    }
                                }
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (backdrop != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { capsuleShape },
                                effects = containerEffects,
                                highlight = null,
                                shadow = null,
                                layerBlock = {
                                    alpha = backdropAlpha
                                },
                                onDrawSurface = {}
                            )
                            .edgeLight(shape = capsuleShape, edgeLight = rememberDefaultEdgeLight(baseColor = containerColor))
                    )
                }
                Row(
                    modifier = Modifier
                        .background(
                            color = if (backdrop != null) {
                                lerp(
                                    MiuixTheme.colorScheme.surfaceContainerHigh,
                                    containerColor,
                                    backdropAlpha
                                )
                            } else MiuixTheme.colorScheme.surfaceContainerHigh,
                            shape = capsuleShape,
                        )
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actualLeadingIcon()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = SearchBarDefaults.InputFieldMinHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val mergedLabelStyle = remember(textStyle) {
                            TextStyle(fontSize = SearchBarDefaults.InputFieldFontSize, fontWeight = FontWeight.Medium).merge(textStyle)
                        }
                        Text(
                            text = labelText,
                            style = mergedLabelStyle,
                            color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                        )
                        Box(modifier = Modifier.graphicsLayer { alpha = textAlpha.value }) {
                            innerTextField()
                        }
                    }
                    actualTrailingIcon()
                }
            }
        },
    )

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        } else if (focused) {
            delay(100.milliseconds)
            if (query.isNotEmpty()) {
                textAlpha.animateTo(0f)
                currentOnQueryChange("")
                textAlpha.snapTo(1f)
            }
            focusManager.clearFocus()
        }
    }
}

/** 默认值配置 */
object SearchBarDefaults {
    val InsideMargin = DpSize(10.dp, 0.dp)
    val InputFieldMinHeight = 45.dp
    val InputFieldFontSize = 16.sp
    val LeadingIconStartPadding = 12.dp
    val LeadingIconEndPadding = 8.dp
    val TrailingIconStartPadding = 8.dp
    val TrailingIconEndPadding = 16.dp
}
