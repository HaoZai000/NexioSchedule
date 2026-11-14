package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.truncateNote
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.NoteStr
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import com.haooz.chedule.ui.components.sheetContentBottomInset
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalBlurBottomSheetContentExpanded
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 课程详情底部弹窗（今日页与课程表页共用）。
 *
 * 内容 = 这门课在 [selectedCourses] 里的各条记录卡片，按「本周是否有课」排序：
 * 点卡片进全屏详情，卡片底部是备注行（点一下就地输入，可选「仅本周 / 所有周」）。
 *
 * 今日页与课程表页的差别全部收在参数里：今日页不传 [onEdit]（没有编辑弹窗入口），
 * 也不传 endAction（右上角没有「加课」按钮）。
 *
 * @param allCourses 整张课表的全部课程：点卡片时要按课名捞同名课，跨周次展示。
 * @param selectedCourses 本次弹窗要展示的课程记录（通常 = 该槽位上的同名课）。
 * @param viewingWeek 当前查看的周次，决定排序、备注取哪条、「仅本周」写到哪一周。
 * @param onCourseClick 点卡片 → 全屏详情（参数与课程表页原有回调一致）。
 * @param onNoteSave week = null 表示「所有周」，否则只写该周；空文本等于删除备注。
 */
@Composable
fun CourseDetailSheet(
    show: Boolean,
    allCourses: List<Course>,
    selectedCourses: List<Course>,
    viewingWeek: Int,
    isTablet: Boolean,
    onDismiss: () -> Unit,
    onCourseClick: (
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        snapshot: TransitionLayerSnapshot?,
        targetWeek: Int,
    ) -> Unit,
    onNoteSave: (course: Course, week: Int?, text: String) -> Unit,
    onEdit: ((Course) -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
) {
    val startAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        LiquidTopBarButton(
            onClick = onDismiss,
            backdrop = LocalSheetContentBackdrop.current!!,
            icon = MiuixIcons.Normal.Close,
            contentDescription = CommonStr.cancel(),
            modifier = Modifier.padding(start = if (isTablet) 16.dp else 18.dp),
            iconSize = 24.dp,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }
    val content: @Composable () -> Unit = {
        CourseDetailSheetContent(
            allCourses = allCourses,
            selectedCourses = selectedCourses,
            viewingWeek = viewingWeek,
            isTablet = isTablet,
            onCourseClick = onCourseClick,
            onNoteSave = onNoteSave,
            onEdit = onEdit,
        )
    }
    if (isTablet) {
        BlurBottomSheetTablet(
            show = show,
            title = SchedStr.courseDetails(),
            dimBackground = true,
            isBottomAligned = true,
            onDismissRequest = onDismiss,
            startAction = startAction,
            endAction = endAction,
            content = content,
        )
    } else {
        BlurBottomSheet(
            show = show,
            title = SchedStr.courseDetails(),
            dimBackground = true,
            onDismissRequest = onDismiss,
            startAction = startAction,
            endAction = endAction,
            content = content,
        )
    }
}

@Composable
private fun CourseDetailSheetContent(
    allCourses: List<Course>,
    selectedCourses: List<Course>,
    viewingWeek: Int,
    isTablet: Boolean,
    onCourseClick: (
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        snapshot: TransitionLayerSnapshot?,
        targetWeek: Int,
    ) -> Unit,
    onNoteSave: (course: Course, week: Int?, text: String) -> Unit,
    onEdit: ((Course) -> Unit)?,
) {
    // 本周有课的排前面；都没课时按「离当前周多近」排，别让用户先看到很久以前那学期的那条。
    // 按 id 回到 allCourses 取最新版本：改完备注要立刻显示，传入的快照列表不会跟着更新。
    val coursesToShow = remember(selectedCourses, allCourses, viewingWeek) {
        val latestById = allCourses.associateBy { it.id }
        selectedCourses
            .map { latestById[it.id] ?: it }
            .sortedWith(
                compareByDescending<Course> { it.isActiveInWeek(viewingWeek) }
                    .thenBy {
                        if (it.isActiveInWeek(viewingWeek)) 0
                        else if (viewingWeek < it.startWeek) it.startWeek - viewingWeek
                        else viewingWeek - it.endWeek
                    }
            )
    }
    // 始终参与布局，用 graphicsLayer 做透明/位移，避免移除节点导致弹窗高度闪烁；
    // 逐张错开由 CardRevealSpec 的 index 负责
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 拉到全屏时铺满（列表自己滚），半屏时按内容高度收着
            .then(
                if (LocalBlurBottomSheetContentExpanded.current) Modifier.fillMaxHeight() else Modifier
            )
            .overScrollVertical()
            .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
            .verticalScroll(rememberScrollState())
            .sheetContentBottomInset(isTablet),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(if (isTablet) 56.dp else 58.dp))
        coursesToShow.forEachIndexed { index, course ->
            key(course.id) {
                CourseDetailCard(
                    course = course,
                    allCourses = allCourses,
                    viewingWeek = viewingWeek,
                    index = index,
                    onCourseClick = onCourseClick,
                    onNoteSave = onNoteSave,
                    onEdit = onEdit,
                )
            }
        }
    }
}

/** 卡片轮廓之外的可变边界：点卡片时要把当前卡片的屏幕位置交给全屏详情做 morph。 */
private class DetailCardBounds {
    var rect: Rect? = null
}

@Composable
private fun CourseDetailCard(
    course: Course,
    allCourses: List<Course>,
    viewingWeek: Int,
    index: Int,
    onCourseClick: (
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        snapshot: TransitionLayerSnapshot?,
        targetWeek: Int,
    ) -> Unit,
    onNoteSave: (course: Course, week: Int?, text: String) -> Unit,
    onEdit: ((Course) -> Unit)?,
) {
    val isCurrentWeek = course.isActiveInWeek(viewingWeek)
    // 时间染色、地点/教师走次要色：一条信息行里区分「几点上课」和「在哪、谁上」
    val primaryColor = MiuixTheme.colorScheme.primary
    val infoText = remember(course, isCurrentWeek, primaryColor) {
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.Medium,
                )
            ) {
                append(course.timeDisplayTextLocalized())
            }
            val extras = buildList {
                if (course.classroom.isNotEmpty()) add(course.classroom)
                if (course.teacher.isNotEmpty()) add(course.teacher)
            }
            if (extras.isNotEmpty()) {
                append("  ")
                append(extras.joinToString(" · "))
            }
        }
    }
    val cardLayer = rememberGraphicsLayer()
    val boundsHolder = remember(course.id) { DetailCardBounds() }
    val cardReveal = rememberCardRevealState(index = index)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (cardReveal.isAnimating) Modifier.cardRevealLayer(reveal = cardReveal) else Modifier
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.localToRoot(Offset.Zero)
                    val size = coordinates.size
                    boundsHolder.rect = Rect(
                        left = position.x,
                        top = position.y,
                        right = position.x + size.width,
                        bottom = position.y + size.height,
                    )
                }
                .drawWithContent {
                    // The outer visibility/reveal transform is deliberately not recorded.
                    cardLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(cardLayer)
                },
            insideMargin = PaddingValues(0.dp),
            pressFeedbackType = PressFeedbackType.None,
            showIndication = true,
            colors = CardDefaults.defaultColors(
                color = if (MiuixTheme.isDynamicColor) {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                } else if (isAppDarkTheme()) {
                    Color(0xFF303030)
                } else {
                    Color(0xFFFFFFFF)
                },
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
            onClick = {
                val coursesForDetail = allCourses.filter { it.name == course.name }
                // 本周有课用当前查看周，否则回退到最近上课周
                val targetWeek = when {
                    course.isActiveInWeek(viewingWeek) -> viewingWeek
                    viewingWeek < course.startWeek -> course.startWeek
                    else -> course.endWeek
                }
                val bounds = boundsHolder.rect ?: return@Card
                onCourseClick(
                    coursesForDetail,
                    bounds.left,
                    bounds.top,
                    bounds.width,
                    bounds.height,
                    TransitionLayerSnapshot(cardLayer),
                    targetWeek,
                )
            },
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            // 周次最多占半行（够长就折行，不会被省略号截掉），
                            // 标题吃满剩下那段 —— 所以周次实际用得少时，多出来的宽度全归标题，
                            // 而周次本身始终贴在最右边。
                            val badgeMaxWidth = maxWidth * 0.5f
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = course.name,
                                    modifier = Modifier.weight(1f),
                                    style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                WeekBadge(
                                    text = course.weekTextLocalized(),
                                    highlighted = isCurrentWeek,
                                    modifier = Modifier.widthIn(max = badgeMaxWidth),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = infoText,
                                modifier = Modifier.weight(1f),
                                style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (onEdit != null) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = CommonStr.edit(),
                                    style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(ContinuousRoundedRectangle(8.dp))
                                        .clickable { onEdit(course) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                CourseNoteBlock(
                    course = course,
                    week = viewingWeek,
                    onSave = { week, text -> onNoteSave(course, week, text) },
                )
            }
        }
    }
}

/** 周次徽章：本周那条用主题色底，非本周保持中性灰。宽度由调用方按权重约束，文字换行不截断。 */
@Composable
private fun WeekBadge(
    text: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return
    val accent = MiuixTheme.colorScheme.primary
    val idle = MiuixTheme.colorScheme.onSurfaceVariantActions
    Box(
        modifier = modifier
            .clip(ContinuousRoundedRectangle(7.dp))
            .background(
                if (highlighted) accent.copy(alpha = 0.12f) else idle.copy(alpha = 0.08f)
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
            color = if (highlighted) accent else idle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 卡片底部的备注块：点一下就地输入，不跳页面也不弹二级窗。
 *
 * 范围 chip 只在编辑态出现，避免常驻两个按钮把卡片撑高。
 * 默认范围跟着「本周这条备注属于谁」走：
 * - 本周已有单周备注 → 正在改的就是它，默认「仅本周」；
 * - 本周没有单周备注、但存在「所有周」备注 → 默认「所有周」；
 * - 一条备注都没有（第一次加）→ 默认**只写本周**，要全周生效得自己点 chip。
 */
@Composable
private fun CourseNoteBlock(
    course: Course,
    week: Int,
    onSave: (week: Int?, text: String) -> Unit,
) {
    var editing by remember(course.id) { mutableStateOf(false) }
    var draft by remember(course.id) { mutableStateOf("") }
    var scopeAllWeeks by remember(course.id) {
        mutableStateOf(course.weekNotes[week].isNullOrBlank() && course.note.isNotBlank())
    }
    val haptic = LocalHapticFeedback.current
    val shown = course.noteForWeek(week)

    fun commit(force: Boolean = false) {
        // 失焦与 chip 点击会先后各触发一次：已经退出编辑就不再重复落盘
        if (!editing && !force) return
        val text = draft.trim()
        if (force || text != shown) {
            onSave(if (scopeAllWeeks) null else week, text)
        }
        editing = false
    }

    fun startEdit() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        draft = shown
        scopeAllWeeks = course.weekNotes[week].isNullOrBlank() && course.note.isNotBlank()
        editing = true
    }

    // 弹窗被下拉关闭 / 返回键退出时不会走失焦回调，草稿要在这里兜一次。
    // 用 rememberUpdatedState 取值：onDispose 只跑一次，闭包里不能捕获首帧的 week/文本。
    val latestEditing by rememberUpdatedState(editing)
    val latestDraft by rememberUpdatedState(draft)
    val latestScopeAllWeeks by rememberUpdatedState(scopeAllWeeks)
    val latestShown by rememberUpdatedState(shown)
    val latestWeek by rememberUpdatedState(week)
    val latestSave by rememberUpdatedState(onSave)
    DisposableEffect(course.id) {
        onDispose {
            if (!latestEditing) return@onDispose
            val text = latestDraft.trim()
            if (text != latestShown) {
                latestSave(if (latestScopeAllWeeks) null else latestWeek, text)
            }
        }
    }

    // 备注做成卡片里的一块浅底便签：与主信息分开，又不用再加一条分隔线
    val accent = MiuixTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 14.dp)
            .clip(ContinuousRoundedRectangle(10.dp))
            .background(
                when {
                    editing -> accent.copy(alpha = 0.06f)
                    shown.isNotBlank() -> accent.copy(alpha = 0.06f)
                    else -> MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.05f)
                }
            )
            .pointerInput(editing) {
                detectTapGestures(
                    onTap = {
                        if (editing) commit() else startEdit()
                    }
                )
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (editing) {
            NoteDraftField(
                draft = draft,
                // 上限与导入/服务端同一口径（Course.NOTE_MAX_LENGTH），在这里卡住比事后截断好
                onDraftChange = { draft = it.truncateNote() },
                onDone = { commit() },
                onFocusLost = { commit() },
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoteScopeChip(
                    text = NoteStr.scopeThisWeek(week),
                    selected = !scopeAllWeeks,
                    onClick = {
                        scopeAllWeeks = false
                        commit(force = true)
                    },
                )
                NoteScopeChip(
                    text = NoteStr.scopeAllWeeks(),
                    selected = scopeAllWeeks,
                    onClick = {
                        scopeAllWeeks = true
                        commit(force = true)
                    },
                )
            }
        } else if (shown.isBlank()) {
            Text(
                text = NoteStr.addNote(),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = NoteStr.noteLabel(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = shown,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 备注输入框：进组合即聚焦，回车保存；失焦也保存，避免「点了别处白写一场」。 */
@Composable
private fun NoteDraftField(
    draft: String,
    onDraftChange: (String) -> Unit,
    onDone: () -> Unit,
    onFocusLost: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    BasicTextField(
        value = draft,
        onValueChange = onDraftChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) hadFocus = true else if (hadFocus) onFocusLost()
            },
        textStyle = MiuixTheme.textStyles.body2.copy(
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
        ),
        singleLine = true,
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner ->
            Box {
                if (draft.isEmpty()) {
                    Text(
                        text = NoteStr.notePlaceholder(),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun NoteScopeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(ContinuousRoundedRectangle(9.dp))
            .background(
                if (selected) accent.copy(alpha = 0.14f)
                else MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.06f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) accent else MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}
