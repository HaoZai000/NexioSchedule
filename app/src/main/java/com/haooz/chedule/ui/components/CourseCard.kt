package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.time.Duration.Companion.milliseconds
import android.graphics.Color as AndroidColor

@Composable
fun CourseCard(
    course: Course,
    isCurrentWeek: Boolean = true,
    isHoliday: Boolean = false,
    isWorkSwap: Boolean = false,
    hasMultipleCourses: Boolean = false,
    wallpaperBackdrop: Backdrop? = null,
    cardBlurRadius: Float = 0f,
    cardAlpha: Float = 0.15f,
    cardHeightPerSection: Float = 54f,
    // 自定义时间课程显式指定卡片高度（dp），null 时按节次数量计算
    customCardHeightDp: Float? = null,
    cardCornerRadius: Float = 10f,
    isTablet: Boolean = false,
    cardContentAlignment: com.haooz.chedule.data.CardContentAlignment = com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER,
    cardTextColor: com.haooz.chedule.data.CardTextColor = com.haooz.chedule.data.CardTextColor.COLORFUL,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    cardRefraction: com.haooz.chedule.data.CardRefractionLevel = com.haooz.chedule.data.CardRefractionLevel.DEFAULT,
    isDragging: Boolean = false,
    disablePadding: Boolean = false,
    onClick: () -> Unit,
    onLongPressStart: (cardLeft: Float, cardTop: Float, width: Float, height: Float) -> Unit = { _, _, _, _ -> },
    onDragStart: () -> Unit = {},
    onDrag: (offsetX: Float, offsetY: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onMenuDismiss: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val sectionCount = course.endSection - course.startSection + 1
    val cardHeight = (customCardHeightDp ?: (sectionCount * cardHeightPerSection)).dp
    val hasBlur = wallpaperBackdrop != null
    val effectiveCornerRadius = if (isTablet) (cardCornerRadius * 1.3f) else cardCornerRadius
    val isDark = isAppDarkTheme()
    val scope = rememberCoroutineScope()
    val localDensity = LocalDensity.current

    val effectiveAlpha = if (hasBlur) cardAlpha * 1.6f else cardAlpha
    // 假期课程与调休课程沿用非本周课程的灰色，不再使用课程自身颜色
    val cardColor = remember(course.colorRes, isCurrentWeek, isHoliday, effectiveAlpha) {
        if (isCurrentWeek && !isHoliday) {
            Color(course.colorRes).copy(alpha = effectiveAlpha)
        } else {
            Color(0xFF9E9E9E).copy(alpha = effectiveAlpha * 0.7f)
        }
    }
    val textColor = remember(course.colorRes, isCurrentWeek, isHoliday, hasBlur, isDark, cardTextColor) {
        // 纯色模式仅作用于本周课程：文字统一为黑/白 0.74f；非本周/假期沿用原有灰色
        if (isCurrentWeek && !isHoliday &&
            cardTextColor == com.haooz.chedule.data.CardTextColor.SOLID
        ) {
            if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.74f)
        } else if (isCurrentWeek && !isHoliday) {
            if (hasBlur) Color(course.colorRes).let { c ->
                val hsv = FloatArray(3)
                AndroidColor.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
                if (isDark) {
                    hsv[1] = (hsv[1] * 0.5f).coerceIn(0f, 1f)
                    hsv[2] = (hsv[2] + 0.4f).coerceIn(0f, 1f)
                } else {
                    hsv[1] = (hsv[1] * 0.84f).coerceIn(0f, 1f)
                    hsv[2] = (hsv[2] + 0.5f).coerceIn(0f, 1f)
                }
                val boosted = AndroidColor.HSVToColor(hsv)
                Color(AndroidColor.red(boosted), AndroidColor.green(boosted), AndroidColor.blue(boosted))
            }
            else Color(course.colorRes)
        } else {
            if (hasBlur) {
                if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)
            } else {
                Color(0xFF9E9E9E).copy(alpha = if (isDark) 0.28f else 0.45f)
            }
        }
    }

    if (hasBlur) {
        key(effectiveCornerRadius) {
            var isPressed by remember { mutableStateOf(false) }
            val scale = remember { Animatable(1f) }
            var cardPosition by remember { mutableStateOf(Offset.Zero) }
            var cardSize by remember { mutableStateOf(Offset.Zero) }
            var layoutCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            val backdropShape = remember(effectiveCornerRadius) { ContinuousRoundedRectangle(effectiveCornerRadius.dp) }
            val blurPx = with(localDensity) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
            val lensRadiusPx = with(localDensity) { remember(cardRefraction) { cardRefraction.lensRadiusDp.dp.toPx() } }
            val lensStrengthPx = with(localDensity) { remember(cardRefraction) { cardRefraction.lensStrengthDp.dp.toPx() } }
            val overlayColor = remember(isDark) { if (isDark) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.17f) }
            val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
            val backdropEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(isSharedBlur, blurPx, lensRadiusPx, lensStrengthPx, cardRefraction) {
                {
                    if (!isSharedBlur) {
                        blur(blurPx)
                    }
                    if (cardRefraction != com.haooz.chedule.data.CardRefractionLevel.OFF) {
                        lens(lensRadiusPx, lensStrengthPx)
                    }
                }
            }
            LaunchedEffect(isPressed) {
                if (isPressed) {
                    scale.animateTo(
                        targetValue = 0.94f,
                        animationSpec = tween(durationMillis = 100)
                    )
                } else {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 180)
                    )
                }
            }

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .then(if (disablePadding) Modifier else Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = if (isDragging) 0f else 1f
                    }
                    .onGloballyPositioned { coordinates ->
                        layoutCoordinates = coordinates
                        val center = coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
                        cardPosition = center
                        cardSize = Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                    }
                    .drawBackdrop(
                        backdrop = wallpaperBackdrop,
                        shape = { backdropShape },
                        effects = backdropEffects,
                        highlight = null,
                        shadow = null,
                        downsampleScale = 0.48f,
                        onDrawSurface = {
                            // 底色 + 反光覆盖层一并在此绘制，省去独立的 drawBehind 绘制节点
                            drawRect(cardColor)
                            drawRect(overlayColor)
                        }
                    )
                    .drawWithContent {
                        drawContent()
                        // 同色描边替代 edgeLight，使用和课程卡片一样的 ContinuousRoundedRectangle
                        val strokePx = 2.dp.toPx()
                        val outline = ContinuousRoundedRectangle(effectiveCornerRadius.dp)
                            .createOutline(size, layoutDirection, this)
                        drawOutline(
                            outline = outline,
                            color = cardColor.copy(alpha = 0.05f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx)
                        )
                    }
                    .pointerInput(course) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            isPressed = true
                            val downPosition = down.position
                            var isLongPress = false
                            var isDraggingCard = false
                            var menuShown = false
                            val longPressJob = scope.launch {
                                delay(320.milliseconds)
                                isLongPress = true
                                isPressed = false
                                menuShown = true
                                onLongPressStart(
                                    cardPosition.x,
                                    cardPosition.y,
                                    cardSize.x,
                                    cardSize.y
                                )
                            }
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.any { it.pressed }
                                    if (!pressed) {
                                        isPressed = false
                                        if (isDraggingCard) {
                                            if (menuShown) {
                                                onDrag(0f, 0f)
                                            } else {
                                                onDragEnd()
                                            }
                                        } else if (menuShown) {
                                        } else {
                                            val upChange = event.changes.firstOrNull()
                                            if (upChange != null) {
                                                upChange.consume()
                                                val dist = (upChange.position - downPosition).getDistance()
                                                if (!isLongPress && dist < 8f * density) {
                                                    onClick()
                                                }
                                            }
                                        }
                                        break
                                    }
                                    val currentPos = event.changes.firstOrNull()?.position ?: continue
                                    val dx = currentPos.x - downPosition.x
                                    val dy = currentPos.y - downPosition.y
                                    val dragDist = currentPos.minus(downPosition).getDistance()

                                    // 水平滑动 → 释放手势给 HorizontalPager
                                    if (!isLongPress && !isDraggingCard && !menuShown
                                        && kotlin.math.abs(dx) > 5f * density
                                        && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f
                                    ) {
                                        isPressed = false
                                        break
                                    }

                                    if (!isLongPress && dragDist > 8f * density) {
                                        isPressed = false
                                        event.changes.forEach { it.consume() }
                                        break
                                    }
                                    if (menuShown && !isDraggingCard) {
                                        isDraggingCard = true
                                        onDragStart()
                                    }
                                    if (menuShown) {
                                        val menuDragDist = (currentPos - downPosition).getDistance()
                                        if (menuDragDist > 8f * density) {
                                            menuShown = false
                                            onMenuDismiss()
                                        }
                                    }
                                    if (isDraggingCard) {
                                        onDrag(currentPos.x - downPosition.x, currentPos.y - downPosition.y)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } finally {
                                longPressJob.cancel()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                CardContent(course, sectionCount, textColor, hasMultipleCourses,
                    isTablet, cardContentAlignment, cardHeight.value, cardHeightPerSection,
                    isHoliday, isWorkSwap, isCurrentWeek)
            }
        }
    } else {
        var cardPosition by remember { mutableStateOf(Offset.Zero) }
        var cardSize by remember { mutableStateOf(Offset.Zero) }
        var layoutCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(cardHeight)
                .then(if (disablePadding) Modifier else Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                .graphicsLayer {
                    alpha = if (isDragging) 0f else 1f
                }
                .onGloballyPositioned { coordinates ->
                    layoutCoordinates = coordinates
                    // 上报卡片正中心的绝对坐标，与 hasBlur 分支保持一致
                    val center = coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
                    cardPosition = center
                    cardSize = Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                }
                .pointerInput(course) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val downPosition = down.position
                        var isLongPress = false
                        var isDraggingCard = false
                        var menuShown = false
                        val longPressJob = scope.launch {
                            delay(320.milliseconds)
                            isLongPress = true
                            menuShown = true
                            onLongPressStart(
                                cardPosition.x,
                                cardPosition.y,
                                cardSize.x,
                                cardSize.y
                            )
                        }
                        try {
                            while (true) {
                                // 菜单/拖拽状态：Main pass 拦截事件防止穿透
                                // 否则：Final pass 让 HorizontalPager 先处理水平滑动
                                val pass = if (menuShown || isDraggingCard)
                                    PointerEventPass.Main else PointerEventPass.Final
                                val event = awaitPointerEvent(pass)
                                val pressed = event.changes.any { it.pressed }
                                if (!pressed) {
                                    if (isDraggingCard) {
                                        if (menuShown) {
                                            onDrag(0f, 0f)
                                        } else {
                                            onDragEnd()
                                        }
                                    } else if (menuShown) {
                                    } else {
                                        val upChange = event.changes.firstOrNull()
                                        if (upChange != null) {
                                            upChange.consume()
                                            val dist = (upChange.position - downPosition).getDistance()
                                            if (!isLongPress && dist < 8f * density) {
                                                onClick()
                                            }
                                        }
                                    }
                                    break
                                }
                                val currentPos = event.changes.firstOrNull()?.position ?: continue
                                val dx = currentPos.x - downPosition.x
                                val dy = currentPos.y - downPosition.y
                                val dragDist = currentPos.minus(downPosition).getDistance()

                                // 水平滑动 → 释放手势给 HorizontalPager
                                if (!isLongPress && !isDraggingCard && !menuShown
                                    && kotlin.math.abs(dx) > 5f * density
                                    && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f
                                ) {
                                    break
                                }

                                if (!isLongPress && dragDist > 8f * density) {
                                    event.changes.forEach { it.consume() }
                                    break
                                }
                                if (menuShown && !isDraggingCard) {
                                    isDraggingCard = true
                                    onDragStart()
                                }
                                if (menuShown) {
                                    val menuDragDist = (currentPos - downPosition).getDistance()
                                    if (menuDragDist > 8f * density) {
                                        menuShown = false
                                        onMenuDismiss()
                                    }
                                }
                                if (isDraggingCard) {
                                    onDrag(currentPos.x - downPosition.x, currentPos.y - downPosition.y)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } finally {
                            longPressJob.cancel()
                        }
                    }
                }
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = effectiveCornerRadius.dp,
                insideMargin = PaddingValues(0.dp),
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = true,
                colors = CardDefaults.defaultColors(
                    color = cardColor,
                    contentColor = MiuixTheme.colorScheme.onSurface
                ),
                onClick = {}
            ) {
                CardContent(course, sectionCount, textColor, hasMultipleCourses,
                    isTablet, cardContentAlignment, cardHeight.value, cardHeightPerSection,
                    isHoliday, isWorkSwap, isCurrentWeek, showClassroom, showTeacher)
            }
        }
    }
}

@Composable
private fun CardContent(course: Course, sectionCount: Int, textColor: Color, hasMultipleCourses: Boolean,
                        isTablet: Boolean = false, cardContentAlignment: com.haooz.chedule.data.CardContentAlignment = com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER,
                        cardHeightDp: Float = 0f, cardHeightPerSection: Float = 54f,
                        isHoliday: Boolean = false, isWorkSwap: Boolean = false, isCurrentWeek: Boolean = true,
                        showClassroom: Boolean = true, showTeacher: Boolean = true) {
    val infoFontSize = 11.sp
    val infoLineHeight = 12.sp
    val courseNameLineHeight = 14.2.sp

    val effectiveShowClassroom = showClassroom && course.classroom.isNotEmpty()
    val effectiveShowTeacher = showTeacher && course.teacher.isNotEmpty()

    // 课程名称最多行数：卡片高度能放下几行就几行
    val density = LocalDensity.current
    val availableHeightDp = cardHeightDp - 16f
    val courseNameLineH = with(density) { courseNameLineHeight.toDp().value }
    val infoLineH = with(density) { infoLineHeight.toDp().value }
    val reservedForInfo = ((if (effectiveShowClassroom) 1 else 0) + (if (effectiveShowTeacher) 1 else 0)) * infoLineH
    val nameMaxLines = ((availableHeightDp - reservedForInfo) / courseNameLineH).toInt().coerceAtLeast(1)

    Box(modifier = Modifier.fillMaxSize()) {
        val verticalArrangement = when (cardContentAlignment) {
            com.haooz.chedule.data.CardContentAlignment.TOP_START,
            com.haooz.chedule.data.CardContentAlignment.TOP_CENTER -> Arrangement.Top
            com.haooz.chedule.data.CardContentAlignment.CENTER_START,
            com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER -> Arrangement.Center
        }
        val horizontalAlignment = when (cardContentAlignment) {
            com.haooz.chedule.data.CardContentAlignment.TOP_START,
            com.haooz.chedule.data.CardContentAlignment.CENTER_START -> Alignment.Start
            com.haooz.chedule.data.CardContentAlignment.TOP_CENTER,
            com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER -> Alignment.CenterHorizontally
        }
        val textAlign = when (cardContentAlignment) {
            com.haooz.chedule.data.CardContentAlignment.TOP_START,
            com.haooz.chedule.data.CardContentAlignment.CENTER_START -> TextAlign.Start
            com.haooz.chedule.data.CardContentAlignment.TOP_CENTER,
            com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER -> TextAlign.Center
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement
        ) {
            // 课程名称：优先分配
            Text(
                text = course.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.7.sp,
                lineHeight = courseNameLineHeight,
                color = textColor,
                textAlign = textAlign,
                maxLines = nameMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            // 教室：按剩余空间分配
            if (effectiveShowClassroom) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "@${course.classroom}",
                    fontSize = infoFontSize,
                    lineHeight = infoLineHeight,
                    color = textColor,
                    textAlign = textAlign,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 教师：按剩余空间分配
            if (effectiveShowTeacher) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = course.teacher,
                    fontSize = infoFontSize,
                    lineHeight = infoLineHeight,
                    color = textColor,
                    textAlign = textAlign,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (hasMultipleCourses) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .size(8.dp)
                    .background(color = textColor, shape = CircleShape)
            )
        }

        // 假期“假”/调休“调”角标：固定在卡片右上角；“调”角标背景跟随课程颜色
        val badgeText = when {
            isHoliday -> "假"
            isWorkSwap -> "调"
            else -> null
        }
        if (badgeText != null) {
            // 角标背景：本周被调课程→课程颜色；假期/非本周被调课程→灰色；alpha 统一降低避免过于抢眼
            val badgeBackground = if (isWorkSwap && isCurrentWeek) {
                Color(course.colorRes).copy(alpha = 0.32f)
            } else {
                if (isAppDarkTheme()) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
            }
            Text(
                text = badgeText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = if (isWorkSwap && isCurrentWeek) {
                    if (isAppDarkTheme()) Color.White.copy(alpha = 0.8f) else Color.White
                } else {
                    if (isAppDarkTheme()) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)},
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBackground)
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                maxLines = 1
            )
        }

        if (course.hasValidCustomTime() && cardHeightDp >= 2 * cardHeightPerSection) {
            // 顶部显示开始时间
            Text(
                text = course.customStartTime ?: "",
                fontSize = 8.sp,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(textColor.copy(alpha = 0.1f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                maxLines = 1
            )
            // 底部显示结束时间
            Text(
                text = course.customEndTime ?: "",
                fontSize = 8.sp,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(textColor.copy(alpha = 0.1f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                maxLines = 1
            )
        }
    }
}
