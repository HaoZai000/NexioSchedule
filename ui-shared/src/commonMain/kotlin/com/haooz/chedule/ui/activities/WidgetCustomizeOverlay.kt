/** 桌面小部件自定义浮层 - 小部件从原位连贯移到屏幕中心，其余区域压成深灰 */
package com.haooz.chedule.ui.activities

import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.CustomizeFill
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.GlassEnvironment
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import top.yukonga.miuix.kmp.material.LocalSoftGlassLayer
import top.yukonga.miuix.kmp.material.LocalGlassBlurDp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.WIDGET_WALLPAPER_BLUR_DEFAULT
import com.haooz.chedule.data.WidgetAppearance
import com.haooz.chedule.data.WidgetWallpaperCrop
import com.haooz.chedule.data.WidgetWallpaperStore
import com.haooz.chedule.data.appearance.ThemeMode
import com.haooz.chedule.data.updateWidgetCropForGesture
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.effects.motion.CustomizePageEasing
import com.haooz.chedule.ui.screens.SliderItem
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.shape.ContinuousCapsule
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.platformDynamicColors
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor

/**
 * 进入自定义浮层需要的现场信息。
 *
 * @param page 预览页序号（0=今日 4×2，1=今明 4×2，2=2×2）
 * @param bounds 该小部件此刻在屏幕（root）上的矩形。浮层从这里起步，
 *   首帧与页面里的预览逐像素重合，才谈得上「连贯移动」。
 */
data class WidgetCustomizeRequest(
    val page: Int,
    val bounds: Rect,
)

/** 与课表外观页同一支底色 */
private val WidgetCustomizeScrim = Color(0xFF1A1A1A)

/** 底部工具钮底色（与课表外观页一致） */
private val WidgetCustomizeToolColor = Color(0xFF363636)

/** 已有壁纸时，壁纸钮变成「清除」（与课表外观页同一个红） */
private val WidgetCustomizeClearColor = Color(0xFFB71C1C)

private val WidgetCustomizeToolSize = 56.dp

/** 每颗工具钮外包的一圈（课表外观页同款），负责钮与钮之间的呼吸感 */
private val WidgetCustomizeToolPadding = 7.dp

/** 工具栏距底（导航栏之外）的固定距离。写死，不跟面板/小部件算 */
private val WidgetCustomizeToolBottom = 80.dp

/** 底部面板距屏幕底（导航栏之外）的距离 */
private val WidgetCustomizeSheetBottom = 60.dp

private const val MOVE_IN_MILLIS = 460
private const val DIM_IN_MILLIS = 320
private const val TOOL_IN_MILLIS = 260
private const val MOVE_OUT_MILLIS = 360
private const val DIM_OUT_MILLIS = 260

/**
 * 页面序号 → 小部件壁纸槽位。
 * 用「种类名」而不是序号当键：序号会随预览页增减漂移，种类名是稳定契约。
 */
internal fun widgetWallpaperKey(page: Int): String = when (page) {
    0 -> WidgetWallpaperStore.KEY_TODAY
    1 -> WidgetWallpaperStore.KEY_TODAY_TOMORROW
    else -> WidgetWallpaperStore.KEY_SMALL
}

/**
 * 小部件自定义浮层。
 *
 * 视觉与交互对齐课表外观页：深灰压屏、顶栏取消/应用、底部工具栏。
 * 差别只在「被编辑的主体」——课表页是小部件之外的卡片，这里就是小部件本身。
 *
 * 壁纸：选图 → 双指缩放/拖动取景 → 「应用」才落盘；「取消」一律不写。
 */
@Composable
fun WidgetCustomizeOverlay(
    request: WidgetCustomizeRequest,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    /**
     * 小部件已回到原位、浮层还挂着时回调一次。
     *
     * 给宿主一个「先把页面里那份预览放出来」的机会：两份逐像素重合，谁先谁后看不出来，
     * 但摘浮层那一帧页面必须已经画好了 —— 否则会露出页面底色（深色下就是纯黑）。
     */
    onSettled: () -> Unit = {},
    /** 宿主拉起系统选图器；回调带回解码后的位图（取消/失败传 null） */
    onPickWallpaper: (widthPx: Int, heightPx: Int, onPicked: (ImageBitmap?) -> Unit) -> Unit =
        { _, _, _ -> },
    onCustomizeLayout: () -> Unit = {},
) {
    val densityObj = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // 没壁纸可跟随时的兜底主题
    val appIsDark = isAppDarkTheme()

    val wallpaperKey = remember(request.page) { widgetWallpaperKey(request.page) }
    // 「进浮层那一刻」的快照：取消要**立刻**还回这个样子，不是等退场动画跑完再跳。
    // 壁纸是 var：冷启动时首帧可能是 null，等 LaunchedEffect 读盘回来要把它也更新掉。
    var originalWallpaper by remember(wallpaperKey) {
        mutableStateOf(WidgetWallpaperStore.cached(wallpaperKey))
    }
    var originalCrop by remember(wallpaperKey) {
        mutableStateOf(WidgetWallpaperStore.loadCrop(wallpaperKey))
    }
    /**
     * 面板上只有「跟随系统 / 深色 / 浅色」三段，「跟随壁纸」已经从面板撤掉 ——
     * 它现在只是「挑完图那一刻」的自动行为。旧档位（以及从没选过时的默认值）读出来是
     * FOLLOW_WALLPAPER，进浮层时按当前照片的明暗折算成深/浅，保证三段里必有一段选中。
     */
    fun resolveThemeMode(mode: ThemeMode, bitmap: ImageBitmap?): ThemeMode =
        if (mode != ThemeMode.FOLLOW_WALLPAPER) mode
        // 没有壁纸就没什么可跟的 —— 退回「跟随系统」
        else if (bitmap == null) ThemeMode.FOLLOW_APP
        else if (resolveWidgetDark(bitmap, ThemeMode.FOLLOW_WALLPAPER, appIsDark)) ThemeMode.DARK
        else ThemeMode.LIGHT

    val storedThemeMode = remember(wallpaperKey) {
        WidgetWallpaperStore.loadThemeMode(wallpaperKey)
    }
    val originalThemeMode = remember(wallpaperKey) {
        resolveThemeMode(storedThemeMode, originalWallpaper)
    }
    val originalAppearance = remember(wallpaperKey) {
        WidgetWallpaperStore.loadAppearance(wallpaperKey)
    }
    // 首帧就得有图：页面刚加载过，缓存里已经有了，同步取一份。
    // 交给 LaunchedEffect 会晚一帧 —— 表现就是「进浮层闪一下无壁纸」。
    var wallpaper by remember(wallpaperKey) {
        mutableStateOf(originalWallpaper)
    }
    var wallpaperCrop by remember(wallpaperKey) {
        mutableStateOf(originalCrop)
    }
    // 深浅色档位（跟随系统 / 深色 / 浅色）
    var themeMode by remember(wallpaperKey) {
        mutableStateOf(originalThemeMode)
    }
    // 这一档是不是「自动定的」（旧档位折算 / 选图定深浅）。只有自动定的才在清图时退回跟随系统，
    // 手动选过的深/浅不该因为清了壁纸就被改掉。
    var autoTheme by remember(wallpaperKey) {
        mutableStateOf(storedThemeMode == ThemeMode.FOLLOW_WALLPAPER)
    }
    // 外观参数（字体缩放/模糊/卡片不透明度），与壁纸同槽位
    var appearance by remember(wallpaperKey) {
        mutableStateOf(originalAppearance)
    }
    // 只在用户真的换了图/清了图时才写盘，避免每次「应用」都重编码一遍
    var wallpaperDirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // 底部面板
    var showSheet by remember { mutableStateOf(false) }
    val sheetShift = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(wallpaperKey) {
        // 只有缓存未命中（冷启动直进浮层）才需要读盘
        if (wallpaper != null || !WidgetWallpaperStore.exists(wallpaperKey)) return@LaunchedEffect
        val loaded = WidgetWallpaperStore.load(wallpaperKey, forceReload = true) ?: return@LaunchedEffect
        wallpaper = loaded
        wallpaperCrop = WidgetWallpaperStore.loadCrop(wallpaperKey)
        // 用户还没动过图，读回来的这份才是「原样」，取消时该还的是它
        if (!wallpaperDirty) {
            originalWallpaper = loaded
            originalCrop = wallpaperCrop
        }
    }

    val move = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    val toolAlpha = remember { Animatable(0f) }
    val toolSlide = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }

    // 退出统一走这里：先把小部件送回原位再摘浮层，直接消失会断掉「从哪来回哪去」
    fun close(after: () -> Unit) {
        if (closing) return
        closing = true
        showSheet = false
        scope.launch {
            coroutineScope {
                launch { toolAlpha.animateTo(0f, tween(150)) }
                launch { scrim.animateTo(0f, tween(DIM_OUT_MILLIS)) }
                launch {
                    move.animateTo(
                        0f,
                        tween(MOVE_OUT_MILLIS, easing = CustomizePageEasing)
                    )
                }
            }
            onSettled()
            withFrameNanos { }
            after()
        }
    }

    /**
     * 取消：**立刻**把预览还原成进浮层时的样子。
     *
     * 盘上什么都没写，要还原的只是浮层里这份预览状态。放在退场动画之前做，
     * 飞回去的一路就是原样；等动画跑完再跳变会像「落地时闪了一下」。
     */
    fun revertPreview() {
        wallpaper = originalWallpaper
        wallpaperCrop = originalCrop
        themeMode = originalThemeMode
        autoTheme = storedThemeMode == ThemeMode.FOLLOW_WALLPAPER
        appearance = originalAppearance
        wallpaperDirty = false
    }

    fun cancel() {
        if (closing) return
        revertPreview()
        close(onDismiss)
    }

    // 「应用」：先把图写下去再退场 —— 浮层一摘，作用域里的协程就被取消了
    fun apply() {
        if (closing || saving) return
        saving = true
        scope.launch {
            val bitmap = wallpaper
            if (bitmap == null) {
                // 图被清掉了就删盘
                if (WidgetWallpaperStore.exists(wallpaperKey)) {
                    WidgetWallpaperStore.clear(wallpaperKey)
                }
            } else {
                // 只挪了取景也要存：图没换就别重编码一遍
                if (wallpaperDirty) WidgetWallpaperStore.save(wallpaperKey, bitmap)
                WidgetWallpaperStore.saveCrop(wallpaperKey, wallpaperCrop)
            }
            WidgetWallpaperStore.saveThemeMode(wallpaperKey, themeMode)
            WidgetWallpaperStore.saveAppearance(wallpaperKey, appearance)
            saving = false
            close(onApply)
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                move.animateTo(1f, tween(MOVE_IN_MILLIS, easing = CustomizePageEasing))
            }
            // 压屏比位移晚一点起步：先让「这块是小部件」读出来，再把它周围压暗
            launch {
                scrim.animateTo(1f, tween(DIM_IN_MILLIS, easing = CustomizePageEasing))
            }
            launch {
                coroutineScope {
                    launch {
                        toolAlpha.animateTo(1f, tween(TOOL_IN_MILLIS, easing = CustomizePageEasing))
                    }
                    launch {
                        toolSlide.animateTo(0f, tween(TOOL_IN_MILLIS, easing = CustomizePageEasing))
                    }
                }
            }
        }
    }

    NavigationBackHandlerFor(
        enabled = true,
        onBackCompleted = {
            if (showSheet) showSheet = false else cancel()
        },
        onBackCancelled = {},
    )

    val source = request.bounds
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    // 源矩形换到本层坐标
    val sourceRect = remember(source, overlayOrigin) {
        source.translate(-overlayOrigin.x, -overlayOrigin.y)
    }
    val primaryColor = MiuixTheme.colorScheme.primary
    // 顶栏胶囊采它下面的 scrim + 预览；录制层必须在胶囊之外，避免采到自己
    val glassBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        val sourceW = if (sourceRect.width > 0f) sourceRect.width else screenW
        val sourceH = if (sourceRect.height > 0f) sourceRect.height else screenW

        // 只位移不缩放：落位后仍是原尺寸，用户改的是这张小部件本身，不是它的放大稿
        val targetLeft = (screenW - sourceW) / 2f
        val targetTop = (screenH - sourceH) / 2f

        Box(modifier = Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
        // 压屏：draw 阶段读 alpha，避免每帧重组整层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(WidgetCustomizeScrim.copy(alpha = scrim.value))
                }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = { if (showSheet) showSheet = false }
                )
        )

        // 小部件本体：按源尺寸布局一次，只平移，内容不重排
        Box(
            modifier = Modifier
                .width(with(densityObj) { sourceW.toDp() })
                .height(with(densityObj) { sourceH.toDp() })
                .graphicsLayer {
                    val p = move.value
                    translationX = sourceRect.left + (targetLeft - sourceRect.left) * p
                    translationY = sourceRect.top + (targetTop - sourceRect.top) * p -
                        sheetShift.value * p
                }
                .pointerInput(wallpaper, sourceW, sourceH) {
                    val bitmap = wallpaper
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        if (bitmap == null) {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        do {
                            val event = awaitPointerEvent()
                            // 焦点 + 缩放，按容器尺寸现算 —— 与参考实现同一套模型
                            wallpaperCrop = updateWidgetCropForGesture(
                                crop = wallpaperCrop,
                                pan = event.calculatePan(),
                                zoom = event.calculateZoom(),
                                bitmapW = bitmap.width,
                                bitmapH = bitmap.height,
                                containerW = sourceW,
                                containerH = sourceH,
                            )
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            WidgetPreviewContent(
                page = request.page,
                fillBounds = true,
                appearance = appearance,
                wallpaper = wallpaper?.let { WidgetWallpaper(bitmap = it, crop = wallpaperCrop) },
                isDark = remember(wallpaper, themeMode) {
                    resolveWidgetDark(wallpaper, themeMode, appIsDark)
                },
            )
        }

        }

        // 顶栏：取消 / 应用
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer { alpha = scrim.value },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetChromeCapsule(
                text = CommonStr.cancel(),
                containerColor = Color(0xFF5C5C5C),
                backdrop = glassBackdrop,
                // 取消：先原地还原，再飞回去
                onClick = { cancel() }
            )
            WidgetChromeCapsule(
                text = SchedStr.applyChanges(),
                containerColor = primaryColor,
                backdrop = glassBackdrop,
                onClick = { apply() }
            )
        }

        // 底部工具栏：小部件只要壁纸 + 自定义
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                // 距底**固定** 80dp
                .padding(bottom = WidgetCustomizeToolBottom)
                .graphicsLayer {
                    alpha = toolAlpha.value
                    translationY = toolSlide.value * 40.dp.toPx()
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.padding(WidgetCustomizeToolPadding)) {
                val hasWallpaper = wallpaper != null
                WidgetToolButton(
                    icon = if (hasWallpaper) MiuixIcons.Normal.Close else MiuixIcons.Image,
                    contentDescription = if (hasWallpaper) SchedStr.clearWallpaper()
                    else SchedStr.wallpaper(),
                    iconSize = 26.dp,
                    containerColor = if (hasWallpaper) WidgetCustomizeClearColor
                    else WidgetCustomizeToolColor,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        if (hasWallpaper) {
                            wallpaper = null
                            wallpaperCrop = WidgetWallpaperCrop()
                            wallpaperDirty = true
                            if (autoTheme) themeMode = ThemeMode.FOLLOW_APP
                        } else {
                            onPickWallpaper(sourceW.toInt(), sourceH.toInt()) { picked ->
                                if (picked != null) {
                                    wallpaper = picked
                                    wallpaperCrop = WidgetWallpaperCrop()
                                    wallpaperDirty = true
                                    // 选完图，深浅就按照片明暗自动定下来
                                    themeMode = resolveThemeMode(ThemeMode.FOLLOW_WALLPAPER, picked)
                                    autoTheme = true
                                }
                            }
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.padding(WidgetCustomizeToolPadding)) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = 36.dp)
                        .clip(ContinuousCapsule())
                        .background(WidgetCustomizeToolColor)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.padding(WidgetCustomizeToolPadding)) {
                WidgetToolButton(
                    icon = MiuixIcons.GridView,
                    contentDescription = SchedStr.customize(),
                    iconSize = 24.dp,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onCustomizeLayout()
                        showSheet = true
                    }
                )
            }
        }

        // 面板升起时小部件整体上移，落在「面板之上那半屏」的正中
        LaunchedEffect(showSheet, sheetHeightPx) {
            val target = if (showSheet) sheetHeightPx / 2f else 0f
            sheetShift.animateTo(target, tween(320, easing = CustomizePageEasing))
        }

        WidgetCustomizeSheet(
            visible = showSheet,
            themeMode = themeMode,
            onThemeModeChange = {
                themeMode = it
                autoTheme = false
            },
            appearance = appearance,
            onAppearanceChange = { appearance = it },
            hasWallpaper = wallpaper != null,
            onHeightChange = { sheetHeightPx = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** 顶栏胶囊（取消 / 应用） */
@Composable
private fun WidgetChromeCapsule(
    text: String,
    containerColor: Color,
    backdrop: GlassBackdrop,
    onClick: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val glassEffectsOn = AppMaterialSettings.glassEffectsEnabled()
    val glassBlur = AppMaterialSettings.glassBlurDp()
    val capsuleShape = remember { ContinuousCapsule() }
    val glassStyle = AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = GlassEnvironment.isDark,
        fill = CustomizeFill(containerColor),
        tone = BrightTone,
    )
    val capsuleEffects: GlassEffectScope.() -> Unit = remember(glassEffectsOn, glassBlur, glassStyle) {
        {
            if (glassEffectsOn) {
                blur(glassBlur.dp.toPx())
                runtimeShaderEffect(
                    key = "WidgetChromeCapsule_" + text,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(style = glassStyle, width = size.width, height = size.height)
                }
            }
        }
    }
    val capsuleSurface: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)? =
        if (glassEffectsOn) null else ({ drawRect(containerColor.copy(AppMaterialSettings.solidSurfaceAlpha(0.8f))) })
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(40.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { capsuleShape },
                effects = capsuleEffects,
                layerBlock = {
                    val progress = highlight.pressProgress
                    val scale = 1f + 2f.dp.toPx() / 40.dp.toPx() * progress
                    scaleX = scale
                    scaleY = scale
                    val offset = highlight.offset
                    translationX = size.minDimension * 0.05f * offset.x / size.maxDimension
                    translationY = size.minDimension * 0.05f * offset.y / size.maxDimension
                },
                onDrawSurface = capsuleSurface,
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                }
            )
            .then(highlight.modifier)
            .then(highlight.gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 底部圆形工具钮 */
@Composable
private fun WidgetToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    containerColor: Color = WidgetCustomizeToolColor,
) {
    Box(
        modifier = Modifier
            .size(WidgetCustomizeToolSize)
            .clip(ContinuousCapsule())
            .background(containerColor)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = Color.White
        )
    }
}


/** 小部件自定义面板 */
@Composable
private fun WidgetCustomizeSheet(
    visible: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appearance: WidgetAppearance,
    onAppearanceChange: (WidgetAppearance) -> Unit,
    /** 没有壁纸时「壁纸模糊」没意义，直接禁用（右侧显示「需设置壁纸」） */
    hasWallpaper: Boolean,
    onHeightChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dynamic = MiuixTheme.isDynamicColor
    // platformDynamicColors 是 @Composable（要读系统色），不能塞进 remember
    val sheetColors = if (dynamic) {
        platformDynamicColors(dark = true)
    } else {
        // 非动态色就用 Miuix 的深色方案，只把两张卡片的底色对齐现有深色观感
        remember {
            darkColorScheme(
                surfaceContainerHigh = Color(0xFF303030),
                surfaceContainerHighest = Color(0xFF3F3F3F),
            )
        }
    }
    MiuixTheme(colors = sheetColors) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = CustomizePageEasing)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250, easing = CustomizePageEasing)
            ) + fadeOut(tween(150)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = WidgetCustomizeSheetBottom)
                    .onSizeChanged { onHeightChange(it.height.toFloat()) },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WidgetSheetCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = SchedStr.defaultTheme(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WidgetThemeOptions.forEach { mode ->
                                val selected = themeMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(ContinuousCapsule())
                                        .background(
                                            if (selected) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.surfaceContainerHighest
                                        )
                                        .clickable(
                                            interactionSource = null,
                                            indication = null,
                                            role = Role.Button,
                                            onClick = { onThemeModeChange(mode) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = widgetThemeLabel(mode),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) Color.White
                                        else MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                WidgetSheetCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        SliderItem(
                            label = SchedStr.cardTextScale(),
                            value = appearance.textScale,
                            valueRange = WidgetTextScaleRange,
                            keyPoints = listOf(WidgetTextScaleDefault),
                            enabled = true,
                            onValueChange = { onAppearanceChange(appearance.copy(textScale = it)) },
                            quantize = { (it * 20f).roundToInt() / 20f },
                            suffix = "%",
                            displayValue = { (it * 100).roundToInt().toString() },
                            parseInput = { input ->
                                input.replace(',', '.').toFloatOrNull()
                                    ?.div(100f)
                                    ?.coerceIn(WidgetTextScaleRange.start, WidgetTextScaleRange.endInclusive)
                            }
                        )
                        SliderItem(
                            label = SchedStr.wallpaperBlur(),
                            value = appearance.wallpaperBlur,
                            valueRange = WidgetWallpaperBlurRange,
                            keyPoints = listOf(WidgetWallpaperBlurDefault),
                            enabled = hasWallpaper,
                            onValueChange = { onAppearanceChange(appearance.copy(wallpaperBlur = it)) },
                            quantize = { it.roundToInt().toFloat() },
                            suffix = "dp",
                            displayValue = { it.roundToInt().toString() },
                            parseInput = { input ->
                                input.replace(',', '.').toFloatOrNull()
                                    ?.coerceIn(
                                        WidgetWallpaperBlurRange.start,
                                        WidgetWallpaperBlurRange.endInclusive
                                    )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 面板里的小部件主题档位（顺序即展示顺序）。
 *
 * 「跟随壁纸」已从面板撤掉：它现在只是**选图那一刻的自动行为** ——
 * 挑完图按照片明暗直接落到深色或浅色，三段里始终有一段是选中的。
 */
private val WidgetThemeOptions = listOf(
    ThemeMode.FOLLOW_APP,
    ThemeMode.DARK,
    ThemeMode.LIGHT,
)

/** 档位展示名：跟随时对小部件说的是「跟随系统」，不是课表页那个「跟随应用」 */
private fun widgetThemeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.FOLLOW_APP -> SchedStr.followSystem()
    else -> mode.label
}

/** 字号倍率可调区间 */
private val WidgetTextScaleRange = 0.8f..1.4f

/** 字号倍率的出厂值（100%）：滑杆上的锚点 */
private const val WidgetTextScaleDefault = 1f

/** 壁纸模糊可调区间（dp） */
private val WidgetWallpaperBlurRange = 0f..24f

/** 壁纸模糊的出厂值：与落盘默认值同源，别各写各的 */
private const val WidgetWallpaperBlurDefault = WIDGET_WALLPAPER_BLUR_DEFAULT

@Composable
private fun WidgetSheetCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurface
        ),
    ) { content() }
}