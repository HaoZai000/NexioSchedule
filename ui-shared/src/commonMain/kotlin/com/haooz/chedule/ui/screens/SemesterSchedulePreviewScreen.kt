/** 学期课表预览页：标准文档页（DocumentPageScaffold），右上角可保存为图片 */
package com.haooz.chedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SwitchStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val PreviewDayLabels = listOf(
    SwitchStr.dayHeaderMon,
    SwitchStr.dayHeaderTue,
    SwitchStr.dayHeaderWed,
    SwitchStr.dayHeaderThu,
    SwitchStr.dayHeaderFri,
    SwitchStr.dayHeaderSat,
    SwitchStr.dayHeaderSun,
)

/**
 * 学期课表预览页：与站内其它文档页完全一致的顶栏与滚动结构。
 *
 * 网格与图片导出共用同一套布局，导出图按固定密度渲染，与设备屏幕比例无关。
 */
@Composable
fun SemesterSchedulePreviewScreen(
    scheduleName: String,
    courses: List<Course>,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    onBack: () -> Unit,
    onSaveImage: ((ImageBitmap) -> Unit)? = null,
) {
    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) 32.dp else 28.dp
    val hasCourses = courses.isNotEmpty()
    val subtitleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val emptyColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    DocumentPageScaffold(
        title = scheduleName,
        onBack = onBack,
        // 页面内容只随课表变化：给它一个稳定键，静止时跳过整页采样层重录
        recordKey = scheduleName,
        endAction = if (onSaveImage != null) { backdropAlpha, shadowAlpha, backdrop ->
            SaveScheduleImageButton(
                scheduleName = scheduleName,
                courses = courses,
                morningSections = morningSections,
                afternoonSections = afternoonSections,
                eveningSections = eveningSections,
                onSaveImage = onSaveImage,
                backdrop = backdrop,
                backdropAlpha = backdropAlpha,
                shadowAlpha = shadowAlpha,
            )
        } else null,
    ) { scrollBehavior, _ ->
        Scaffold(topBar = {}) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                    .collapsibleTopInset(scrollBehavior)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = paddingValues.calculateTopPadding() +
                            CollapsibleTopAppBarDefaults.CollapsedHeight +
                            pageTopExtra(isTablet, hasSmallTitle = false),
                        bottom = 60.dp,
                    ),
            ) {
                Text(
                    text = if (hasCourses) SwitchStr.semesterPreview() else SwitchStr.noCourses(),
                    fontSize = 14.sp,
                    color = if (hasCourses) subtitleColor else emptyColor,
                )
                Spacer(Modifier.height(16.dp))
                if (!hasCourses) {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        text = SwitchStr.scheduleEmptyHint(),
                        fontSize = 15.sp,
                        color = emptyColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SemesterScheduleGrid(
                        courses = courses,
                        morningSections = morningSections,
                        afternoonSections = afternoonSections,
                        eveningSections = eveningSections,
                    )
                }
            }
        }
    }
}

/**
 * 学期课表网格：只包含表头与课程格，不带标题/滚动，调用方自行包裹。
 *
 * 尺寸全部用 dp/sp，配合固定密度即可得到与设备无关的导出图。
 */
@Composable
internal fun SemesterScheduleGrid(
    courses: List<Course>,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    modifier: Modifier = Modifier,
) {
    // 实际节数：配置为基准，并覆盖课程用到的最大节次（避免课在配置之外被吃掉）
    val maxCourseSection = remember(courses) {
        courses.maxOfOrNull { it.endSection.coerceAtLeast(it.startSection) } ?: 0
    }
    val configTotal = morningSections + afternoonSections + eveningSections
    val totalSections = maxOf(configTotal, maxCourseSection).coerceAtLeast(1)

    val dayLabelHeight = 28.dp
    val breakHeight = 24.dp
    val sideWidth = 20.dp
    val corner = 10.dp
    val emptyCellMinHeight = 48.dp
    val cellHPadding = 2.dp
    val cellVPadding = 2.dp

    val isDark = isAppDarkTheme()
    val gridLine = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val headerText = MiuixTheme.colorScheme.onSurfaceVariantSummary

    // (day, startSection) -> 该格课程列表
    val slotMap = remember(courses) {
        val map = HashMap<Pair<Int, Int>, MutableList<Course>>()
        courses.forEach { c ->
            if (c.dayOfWeek in 1..7 && c.startSection >= 1) {
                map.getOrPut(c.dayOfWeek to c.startSection) { mutableListOf() }.add(c)
            }
        }
        map.values.forEach { list -> list.sortBy { it.name } }
        map
    }

    // 严格按上午/下午/晚上三段渲染；配置外多出的节次追加在末尾
    val extraSections = (totalSections - configTotal).coerceAtLeast(0)
    val periodBands = buildList {
        if (morningSections > 0) add(morningSections)
        if (afternoonSections > 0) add(afternoonSections)
        if (eveningSections > 0) add(eveningSections)
        if (extraSections > 0) add(extraSections)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(sideWidth)
                    .height(dayLabelHeight),
            )
            PreviewDayLabels.forEach { label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dayLabelHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = headerText,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // 每段独立绘制，段间留空；节次号全局连续
        var sectionNo = 1
        periodBands.forEachIndexed { bandIndex, bandCount ->
            if (bandIndex > 0) {
                Spacer(Modifier.height(breakHeight))
            }
            repeat(bandCount) {
                val section = sectionNo++
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .width(sideWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = section.toString(),
                            fontSize = 12.sp,
                            color = headerText,
                            textAlign = TextAlign.Center,
                        )
                    }

                    PreviewDayLabels.forEachIndexed { dayIndex, _ ->
                        val day = dayIndex + 1
                        val stack = slotMap[day to section]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = cellHPadding, vertical = cellVPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (stack != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .defaultMinSize(minHeight = emptyCellMinHeight),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (stack.size == 1) {
                                        PreviewCourseChip(
                                            course = stack[0],
                                            corner = corner,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(),
                                        )
                                    } else {
                                        stack.forEach { course ->
                                            PreviewCourseChip(
                                                course = course,
                                                corner = corner,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = emptyCellMinHeight)
                                        .fillMaxHeight()
                                        .clip(ContinuousRoundedRectangle(corner))
                                        .background(gridLine.copy(alpha = gridLine.alpha * 0.35f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 课卡：高度随文字变化，同一格多门课上下堆叠 */
@Composable
private fun PreviewCourseChip(
    course: Course,
    corner: Dp,
    modifier: Modifier = Modifier,
) {
    val base = Color(course.colorRes)
    Column(
        modifier = modifier
            .clip(ContinuousRoundedRectangle(corner))
            .background(base.copy(alpha = 0.88f))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = course.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val weekText = course.weekTextLocalized()
        if (weekText.isNotEmpty()) {
            Text(
                text = weekText,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 导出图宽度：固定 dp，配合固定 3x 密度得到 2160px；与内容高度大致相当，避免细长 */
private val PosterWidth = 720.dp
private const val PosterDensity = 3f

/**
 * 顶栏下载按钮：确认弹窗 → 离屏出图 → 交给宿主保存。
 *
 * 平板右栏学期课表预览与手机预览页共用。
 */
@Composable
internal fun SaveScheduleImageButton(
    scheduleName: String,
    courses: List<Course>,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    onSaveImage: (ImageBitmap) -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    backdropAlpha: Float = 1f,
    shadowAlpha: Float = 1f,
) {
    var showConfirm by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    LiquidTopBarButton(
        onClick = { showConfirm = true },
        backdrop = backdrop,
        icon = MiuixIcons.Download,
        contentDescription = SwitchStr.saveAsImage(),
        iconSize = 26.dp,
        backdropAlpha = backdropAlpha,
        shadowAlpha = shadowAlpha,
        modifier = modifier,
    )

    OverlayDialog(
        title = SwitchStr.saveAsImage(),
        summary = SwitchStr.saveImageConfirm(scheduleName),
        show = showConfirm,
        liquidGlassBackdrop = backdrop,
        onDismissRequest = { showConfirm = false },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = CommonStr.cancel(),
                onClick = { showConfirm = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = CommonStr.ok(),
                onClick = {
                    showConfirm = false
                    exporting = true
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (exporting) {
        // 0 尺寸容器：海报节点即使比父级大，也不参与（也不能撑开）按钮所在的行
        Box(Modifier.size(0.dp)) {
            SchedulePosterExporter(
                scheduleName = scheduleName,
                courses = courses,
                morningSections = morningSections,
                afternoonSections = afternoonSections,
                eveningSections = eveningSections,
                onCaptured = {
                    exporting = false
                    onSaveImage(it)
                },
                onFailed = {
                    exporting = false
                    showToast(SwitchStr.saveImageFailed())
                },
            )
        }
    }
}

/**
 * 离屏渲染「保存为图片」海报：固定密度 + fontScale 1，保证同一张课表
 * 在任何设备上导出的像素尺寸与排版一致；高度由内容决定（自适应）。
 */
@Composable
private fun SchedulePosterExporter(
    scheduleName: String,
    courses: List<Course>,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    onCaptured: (ImageBitmap) -> Unit,
    onFailed: () -> Unit,
) {
    val captureLayer = rememberGraphicsLayer()
    val recordedToken = remember { intArrayOf(-1) }
    var requestToken by remember { mutableIntStateOf(0) }
    var laidOut by remember { mutableStateOf(false) }
    val latestOnCaptured by rememberUpdatedState(onCaptured)
    val latestOnFailed by rememberUpdatedState(onFailed)

    CompositionLocalProvider(LocalDensity provides Density(PosterDensity, 1f)) {
        Layout(
            content = {
                Column(
                    modifier = Modifier
                        .requiredWidth(PosterWidth)
                        .background(MiuixTheme.colorScheme.surface)
                        .padding(20.dp),
                ) {
                    Text(
                        text = scheduleName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = SwitchStr.semesterPreview(),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    SemesterScheduleGrid(
                        courses = courses,
                        morningSections = morningSections,
                        afternoonSections = afternoonSections,
                        eveningSections = eveningSections,
                    )
                }
            },
            modifier = Modifier
                // iOS 上 alpha=0 的图层会跳过绘制，留 0.001 让它照常录制但看不见
                .graphicsLayer { alpha = 0.001f }
                .onSizeChanged { if (it.width > 0 && it.height > 0) laidOut = true }
                .drawWithContent {
                    val token = requestToken
                    if (token > 0) {
                        captureLayer.record { this@drawWithContent.drawContent() }
                        recordedToken[0] = token
                        drawLayer(captureLayer)
                    }
                },
        ) { measurables, _ ->
            // 忽略父级约束：海报高度由内容决定，不能被屏幕高度截断
            val placeable = measurables.first().measure(Constraints())
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }
    }

    LaunchedEffect(laidOut) {
        if (!laidOut) return@LaunchedEffect
        withFrameNanos { }
        requestToken++
        val recorded = withTimeoutOrNull(2_000L) {
            while (recordedToken[0] < requestToken) {
                withFrameNanos { }
            }
            true
        } ?: false
        if (!recorded) {
            latestOnFailed()
            return@LaunchedEffect
        }
        val bitmap = runCatching { captureLayer.toImageBitmap() }.getOrNull()
        if (bitmap != null) latestOnCaptured(bitmap) else latestOnFailed()
    }
}
