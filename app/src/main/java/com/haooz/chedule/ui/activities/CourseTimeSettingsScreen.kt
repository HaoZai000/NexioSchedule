/** 课程时间设置页面 - 选择页面 */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppStyle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor

data class TimeConfigCardBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

@SuppressLint("DefaultLocale", "UseOfNonLambdaOffsetOverload")
@Composable
fun CourseTimeSettingsScreen(
    onBack: () -> Unit,
    onEditConfig: (TimeConfig, TimeConfigCardBounds) -> Unit,
    onCreateConfig: (TimeConfigCardBounds) -> Unit,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    refreshTrigger: Int = 0,
    hideConfigId: Long? = null,
    hideFab: Boolean = false,
    newlyAddedConfigId: Long? = null,
    onNewConfigAnimDone: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val repository = remember { CourseRepository(context) }

    var configIds by remember { mutableStateOf(repository.getTimeConfigIds()) }
    var currentConfigId by remember { mutableIntStateOf(repository.getScheduleTimeConfigId(repository.getCurrentScheduleId()).toInt()) }
    var configs by remember {
        mutableStateOf(configIds.map { repository.getTimeConfig(it) })
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            configIds = repository.getTimeConfigIds()
            currentConfigId = repository.getScheduleTimeConfigId(repository.getCurrentScheduleId()).toInt()
            configs = configIds.map { repository.getTimeConfig(it) }
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingConfig by remember { mutableStateOf<TimeConfig?>(null) }
    var deletingConfigId by remember { mutableStateOf<Long?>(null) }

    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val appStyle = rememberAppStyle()
    val isLiquidGlass = appStyle == "liquidglass" && liquidGlassBackdrop != null
    val blurAlpha = if (!isLiquidGlass) {
        if (listScrollY < 50) 0f else ((listScrollY - 50) / 30f).coerceIn(0f, 0.7f)
    } else 0f
    val topBarColorProgress = if (!isLiquidGlass) ((listScrollY - 50) / 30f).coerceIn(0f, 1f) else 0f
    val topBarColor = if (!isLiquidGlass) {
        if (listScrollY < 50) MiuixTheme.colorScheme.surface
        else {
            val surface = MiuixTheme.colorScheme.surface
            val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
            lerp(surface, target, topBarColorProgress)
        }
    } else MiuixTheme.colorScheme.surface
    val topAppBarColors = if (!isLiquidGlass) {
        BlurDefaults.blurColors(
            blendColors = listOf(
                if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
                else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            ),
            brightness = 0f, contrast = 1f, saturation = 1.2f
        )
    } else null

    fun refreshList() {
        configIds = repository.getTimeConfigIds()
        currentConfigId = repository.getScheduleTimeConfigId(repository.getCurrentScheduleId()).toInt()
        configs = configIds.map { repository.getTimeConfig(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (!isLiquidGlass) {
                    TopAppBar(
                        modifier = if (blurAlpha > 0f) {
                            Modifier.textureBlur(backdrop = backdrop, shape = RectangleShape, colors = topAppBarColors!!)
                        } else Modifier,
                        color = topBarColor,
                        title = "课程节数与时间",
                        scrollBehavior = scrollBehavior,
                        navigationIconPadding = 20.dp,
                        navigationIcon = {
                            IconButton(onClick = { onBack() }) {
                                Icon(MiuixIcons.Back,
                                    contentDescription = "返回",
                                    modifier = Modifier.size(28.dp))
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(
                            hapticFeedbackType = HapticFeedbackType.TextHandleMove
                        )
                        .then(
                            if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
                        ),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = if (isLiquidGlass) paddingValues.calculateTopPadding() + 64.dp else paddingValues.calculateTopPadding() + 8.dp,
                        end = 16.dp,
                        bottom = 60.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 配置列表
                    items(configs, key = { it.id }) { config ->
                        val isSelected = config.id == currentConfigId.toLong()
                        val isHidden = config.id == hideConfigId
                        val isNewCard = config.id == newlyAddedConfigId
                        val isDeleting = config.id == deletingConfigId
                        val newCardScale = remember { Animatable(if (isNewCard) 0.8f else 1f) }
                        val newCardAlpha = remember { Animatable(if (isNewCard) 0f else 1f) }
                        LaunchedEffect(isNewCard) {
                            if (isNewCard) {
                                kotlinx.coroutines.delay(100.milliseconds)
                                kotlinx.coroutines.coroutineScope {
                                    launch { newCardScale.animateTo(1f, animationSpec = tween(400)) }
                                    launch { newCardAlpha.animateTo(1f, animationSpec = tween(400)) }
                                }
                                onNewConfigAnimDone()
                            }
                        }
                        LaunchedEffect(isDeleting) {
                            if (isDeleting) {
                                kotlinx.coroutines.coroutineScope {
                                    launch { newCardScale.animateTo(0.8f, animationSpec = tween(300)) }
                                    launch { newCardAlpha.animateTo(0f, animationSpec = tween(300)) }
                                }
                                deletingConfig?.let { config ->
                                    repository.deleteTimeConfig(config.id)
                                    Toast.makeText(context, "已删除「${config.name}」", Toast.LENGTH_SHORT).show()
                                }
                                deletingConfigId = null
                                deletingConfig = null
                                refreshList()
                            }
                        }
                        var cardBounds by remember { mutableStateOf(TimeConfigCardBounds(0f, 0f, 0f, 0f)) }
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier.fillMaxWidth()
                                .then(
                                    if (isNewCard || isDeleting) Modifier.graphicsLayer {
                                        scaleX = newCardScale.value
                                        scaleY = newCardScale.value
                                        alpha = newCardAlpha.value
                                    } else Modifier
                                )
                                .alpha(if (isHidden) 0f else 1f)
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInWindow()
                                    cardBounds = TimeConfigCardBounds(
                                        left = position.x,
                                        top = position.y,
                                        width = coordinates.size.width.toFloat(),
                                        height = coordinates.size.height.toFloat()
                                    )
                                },
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        repository.switchToTimeConfig(config.id)
                                        refreshList()
                                        Toast.makeText(context, "已切换到 ${config.name}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            config.name,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(com.kyant.shapes.RoundedRectangle(6.dp))
                                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "当前",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MiuixTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "上午${config.morningSections}节 · 下午${config.afternoonSections}节 · 晚上${config.eveningSections}节",
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "早${config.getPeriodTimes("morning")[1]?.split("-")?.firstOrNull() ?: "08:00"} · 午${config.getPeriodTimes("afternoon")[1]?.split("-")?.firstOrNull() ?: "14:00"} · 晚${config.getPeriodTimes("evening")[1]?.split("-")?.firstOrNull() ?: "18:30"}",
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                }
                                // 按钮区域：编辑按钮 + 删除按钮，用 Box 绝对定位 + 同步动画
                                Box(modifier = Modifier.height(38.dp)) {
                                    // 编辑按钮（删除按钮出现时向左偏移）
                                    val editOffsetX by animateDpAsState(
                                        targetValue = if (!isSelected) (-62).dp else 0.dp,
                                        animationSpec = tween(durationMillis = 250)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .offset(x = editOffsetX)
                                            .height(38.dp)
                                            .clip(com.kyant.shapes.RoundedRectangle(20.dp))
                                            .background(MiuixTheme.colorScheme.primary)
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                onEditConfig(config, cardBounds)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Edit,
                                            contentDescription = "编辑",
                                            modifier = Modifier.size(22.dp),
                                            tint = Color.White
                                        )
                                    }
                                    // 删除按钮（仅非当前配置可删除，带滑入+淡入动画）
                                    val deleteOffsetX by animateDpAsState(
                                        targetValue = if (!isSelected) 0.dp else 62.dp,
                                        animationSpec = tween(durationMillis = 250)
                                    )
                                    val deleteAlpha by animateFloatAsState(
                                        targetValue = if (!isSelected) 1f else 0f,
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .offset(x = deleteOffsetX)
                                            .graphicsLayer { alpha = deleteAlpha }
                                            .height(38.dp)
                                            .clip(com.kyant.shapes.RoundedRectangle(20.dp))
                                            .background(if (isAppDarkTheme()) Color(0xFF363636) else Color(0xFFF0F0F0))
                                            .clickable(enabled = !isSelected) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                deletingConfig = config
                                                showDeleteDialog = true
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(22.dp),
                                            tint = Color(0xFFF44336)
                                        )
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }

        // 新建悬浮按钮
        var fabBounds by remember { mutableStateOf(TimeConfigCardBounds(0f, 0f, 0f, 0f)) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 32.dp, bottom = 48.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCreateConfig(fabBounds)
                },
                modifier = Modifier
                    .alpha(if (hideFab) 0f else 1f)
                    .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    fabBounds = TimeConfigCardBounds(
                        left = position.x,
                        top = position.y,
                        width = coordinates.size.width.toFloat(),
                        height = coordinates.size.height.toFloat()
                    )
                },
                shadowElevation = 0.dp
            ) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    tint = ComposeColor.White,
                    contentDescription = "新建"
                )
            }
        }
    }

    // 删除确认弹窗（始终在组合树中，通过 show 控制动画）
    OverlayDialog(
        title = "删除时间配置",
        show = showDeleteDialog,
        onDismissRequest = {
            showDeleteDialog = false
            deletingConfig = null
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "确定要删除「${deletingConfig?.name ?: ""}」配置吗？",
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        showDeleteDialog = false
                        deletingConfig = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        deletingConfig?.let { config ->
                            deletingConfigId = config.id
                            showDeleteDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
