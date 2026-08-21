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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberCourseCardEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
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
    val cardColor = remember(course.colorRes, isCurrentWeek, effectiveAlpha) {
        if (isCurrentWeek) {
            Color(course.colorRes).copy(alpha = effectiveAlpha)
        } else {
            Color(0xFF9E9E9E).copy(alpha = effectiveAlpha * 0.7f)
        }
    }
    val textColor = remember(course.colorRes, isCurrentWeek, hasBlur, isDark) {
        if (isCurrentWeek) {
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
            val edgeLightShape = remember(cardCornerRadius) { ContinuousRoundedRectangle(cardCornerRadius.dp) }
            val blurPx = with(localDensity) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
            val lensRadiusPx = with(localDensity) { remember { 6f.dp.toPx() } }
            val lensStrengthPx = with(localDensity) { remember { 14f.dp.toPx() } }
            val overlayColor = remember(isDark) { if (isDark) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.17f) }
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
                        effects = {
                            blur(blurPx)
                            lens(lensRadiusPx, lensStrengthPx)
                        },
                        highlight = null,
                        onDrawSurface = {
                            // 底色 + 反光覆盖层一并在此绘制，省去独立的 drawBehind 绘制节点
                            drawRect(cardColor)
                            drawRect(overlayColor)
                        }
                    )
                    .edgeLight(shape = edgeLightShape, edgeLight = rememberCourseCardEdgeLight())
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
                    isTablet, cardContentAlignment, cardHeight.value, cardHeightPerSection)
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
                    isTablet, cardContentAlignment, cardHeight.value, cardHeightPerSection)
            }
        }
    }
}

// 溢出状态缓存，避免页面切换后重建导致闪烁
private val overflowCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

@Composable
private fun CardContent(course: Course, sectionCount: Int, textColor: Color, hasMultipleCourses: Boolean,
                        isTablet: Boolean = false, cardContentAlignment: com.haooz.chedule.data.CardContentAlignment = com.haooz.chedule.data.CardContentAlignment.CENTER_CENTER,
                        cardHeightDp: Float = 0f, cardHeightPerSection: Float = 54f) {
    val footnote2Size = 10.5.sp
    val smallSize = (footnote2Size.value - 1.7).sp

    // 用全局缓存，页面切换后不会丢失状态
    val classroomKey = "classroom_${course.id}"
    val teacherKey = "teacher_${course.id}"
    val classroomOverflow = remember { mutableStateOf(overflowCache[classroomKey] ?: false) }
    val teacherOverflow = remember { mutableStateOf(overflowCache[teacherKey] ?: false) }

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
            Text(
                text = course.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                lineHeight = 14.2.sp,
                color = textColor,
                textAlign = textAlign,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (sectionCount >= 2 && course.classroom.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "@${course.classroom}",
                    fontSize = if (classroomOverflow.value) smallSize else footnote2Size,
                    lineHeight = if (classroomOverflow.value) 11.sp else 12.sp,
                    color = textColor,
                    textAlign = textAlign,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        // 只缩不放：检测到溢出就锁定缩小，不再恢复
                        if (!classroomOverflow.value && textLayoutResult.lineCount >= 2 && textLayoutResult.isLineEllipsized(1)) {
                            classroomOverflow.value = true
                            overflowCache[classroomKey] = true
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            if (sectionCount >= 2 && course.teacher.isNotEmpty()) {
                Text(
                    text = course.teacher,
                    fontSize = if (teacherOverflow.value) smallSize else footnote2Size,
                    color = textColor,
                    textAlign = textAlign,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        // 只缩不放：检测到溢出就锁定缩小，不再恢复
                        if (!teacherOverflow.value && textLayoutResult.isLineEllipsized(0)) {
                            teacherOverflow.value = true
                            overflowCache[teacherKey] = true
                        }
                    }
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
