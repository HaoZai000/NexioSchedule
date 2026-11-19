/** 学校选择页面 - 用于教务系统导入时选择学校 */
package com.haooz.chedule.ui.activities.eduimport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.school.AdapterData
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.SchoolRepository
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.ExtractedShareTools
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import com.haooz.nexioui.progressindicator.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.basic.ArrowRight
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import com.haooz.nexioui.topbarmaterial.TopBarMaterialThreshold
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.softglass.backdrop.GlassBackdrop
import kotlin.collections.get

@Composable
fun SchoolSelectionScreen(
    modifier: Modifier = Modifier,
    isUpdating: Boolean = false,
    dataVersion: Int = 0,
    isInFreeformWindow: Boolean = false,
    scrollBehavior: SharedScrollBehavior? = null,
    searchQuery: String = "",
    selectedTab: Int = 0,
    topContentPadding: Dp = 0.dp,
    liquidGlassBackdrop: GlassBackdrop? = null,
    onAdapterSelected: (SchoolData, AdapterData) -> Unit,
    /** 顶部「AI 教务导入」入口：模型现场读页面 / 写脚本，不走仓库里的适配 */
    onOpenAiImport: () -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val schoolRepository = remember { SchoolRepository() }

    var selectedSchool by remember { mutableStateOf<SchoolData?>(null) }

    val allSchools = remember(dataVersion) { schoolRepository.getSchools() }

    val filteredSchools = remember(allSchools, selectedTab, searchQuery) {
        val query = searchQuery.trim()
        allSchools.filter { school ->
            val tabMatch = when (selectedTab) {
                0 -> school.adapters.any { it.category == AdapterData.CATEGORY_BACHELOR || it.category == AdapterData.CATEGORY_POSTGRADUATE }
                // WakeUp / 星链已提取到课表导入页，通用工具页不再展示
                1 -> school.adapters.any {
                    it.category == AdapterData.CATEGORY_GENERAL_TOOL &&
                        it.adapterId !in ExtractedShareTools.ADAPTER_IDS
                }
                else -> false
            }
            val searchMatch = query.isEmpty() ||
                school.name.contains(query, ignoreCase = true) ||
                school.initial.contains(query, ignoreCase = true) ||
                school.adapters.any { it.adapterName.contains(query, ignoreCase = true) }
            tabMatch && searchMatch
        }.sortedBy { it.initial.uppercase() + it.name }
    }

    val listState = rememberLazyListState()
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // 程序化滚动（字母条/搜索）不经过 nestedScroll，需监听 listState 同步 contentOffset 和标题栏收起
    val scrollThresholdPx = with(LocalDensity.current) { TopBarMaterialThreshold.toPx() }
    var lastCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val state = scrollBehavior?.state ?: return@collect
            val shouldShow = index > 0 || offset > scrollThresholdPx
            // 同步 contentOffset：父级（教务导入）顶栏材质读的就是这个累计量。
            // 判据是「严格小于阈值」，所以这里多给 1px，保证下一次求值必然翻成 true。
            if (shouldShow && state.contentOffset >= -scrollThresholdPx) {
                state.contentOffset = -scrollThresholdPx - 1f
            } else if (!shouldShow && state.contentOffset < 0f) {
                state.contentOffset = 0f
            }
            // 仅程序化滚动时收起/展开标题栏，手动 fling 由 nestedScroll 处理避免冲突
            if (isProgrammaticScroll && shouldShow != lastCollapsed) {
                lastCollapsed = shouldShow
                if (shouldShow) scrollBehavior.collapse() else scrollBehavior.expand()
            } else if (!isProgrammaticScroll) {
                lastCollapsed = shouldShow
            }
        }
    }

    val isTablet = screenWidthDp() >= 600
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Column(
        modifier = modifier.fillMaxSize().background(NexioTheme.colorScheme.surface)
    ) {
        when {
            filteredSchools.isEmpty() && !isUpdating -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) ImportStr.noSchoolData() else ImportStr.noSchoolMatch(),
                        style = NexioTheme.textStyles.body1,
                        color = NexioTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            isUpdating && filteredSchools.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 64.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = ImportStr.checkingUpdates(),
                            style = NexioTheme.textStyles.body2,
                            color = NexioTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }

            else -> {
                val groupedSchools = remember(filteredSchools) {
                    filteredSchools.groupBy { it.initial.uppercase() }
                }
                val allLetters = remember { ('A'..'Z').map { it.toString() } }
                val availableLetters = remember(groupedSchools) { groupedSchools.keys.toSet() }

                val letterIndexMap = remember(groupedSchools) {
                    val map = mutableMapOf<String, Int>()
                    // 从 1 起：列表第 0 项是「AI 教务导入」入口，字母条跳转要跳过它
                    var itemIndex = AI_ENTRY_ITEM_COUNT
                    groupedSchools.entries.forEachIndexed { index, (letter, schools) ->
                        if (index > 0) itemIndex++
                        map[letter] = itemIndex
                        itemIndex++
                        itemIndex += schools.size
                    }
                    map
                }

                LaunchedEffect(searchQuery) {
                    if (searchQuery.isNotBlank()) {
                        val matchIndex = filteredSchools.indexOfFirst {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                it.initial.contains(searchQuery, ignoreCase = true)
                        }
                        if (matchIndex >= 0) {
                            isProgrammaticScroll = true
                            listState.animateScrollToItem(matchIndex + AI_ENTRY_ITEM_COUNT)
                            isProgrammaticScroll = false
                        }
                    }
                }

                val indexToLetterMap = remember(groupedSchools) {
                    val map = mutableMapOf<Int, String>()
                    // 同上：索引整体后移一项
                    var itemIndex = AI_ENTRY_ITEM_COUNT
                    groupedSchools.entries.forEachIndexed { index, (letter, schools) ->
                        if (index > 0) itemIndex++
                        map[itemIndex] = letter
                        itemIndex++
                        for (i in 0 until schools.size) {
                            map[itemIndex] = letter
                            itemIndex++
                        }
                    }
                    map
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .overScrollVertical()
                            .scrollEndHaptic(
                                hapticFeedbackType = HapticFeedbackType.TextHandleMove
                            )
                            .then(
                                scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                                    ?: Modifier
                            ),
                        contentPadding = PaddingValues(
                            start = tabletHorizontalPadding,
                            end = tabletHorizontalPadding,
                            top = topContentPadding,
                            bottom = 60.dp
                        )
                    ) {
                        val groupedEntries = groupedSchools.entries.toList()
                        item(key = "ai_edu_entry") {
                            if (selectedTab == 0) {
                                AiEduImportEntryCard(onClick = onOpenAiImport)
                            }
                        }
                        groupedEntries.forEachIndexed { index, (letter, schools) ->
                            if (index > 0) {
                                item(key = "divider_$letter") {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            horizontal = 26.dp,
                                            vertical = 12.dp
                                        ),
                                        color = NexioTheme.colorScheme.outline,
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                            item(key = "header_$letter") {
                                Text(
                                    text = letter,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = NexioTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.padding(
                                        start = 26.dp,
                                        top = 16.dp,
                                        bottom = 4.dp
                                    )
                                )
                            }
                            items(schools, key = { it.id }) { school ->
                                val isPostgrad =
                                    school.adapters.any { it.category == AdapterData.CATEGORY_POSTGRADUATE }
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        // 统一按圆角裁切，点击涟漪才不会溢出成直角
                                        .clip(ContinuousRoundedRectangle(20.dp))
                                        .clickable {
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.VirtualKey
                                            )
                                            selectedSchool = school
                                        }
                                        .padding(vertical = 24.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 26.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = school.name,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = NexioTheme.colorScheme.onSurface
                                        )
                                        if (isPostgrad) {
                                            Text(
                                                modifier = Modifier.padding(start = 4.dp),
                                                text = ImportStr.categoryPostgraduate(),
                                                fontSize = 14.sp,
                                                color = NexioTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    var dragHighlight by remember { mutableStateOf<String?>(null) }
                    val coroutineScope = rememberCoroutineScope()

                    val scrollHighlight by remember {
                        derivedStateOf {
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            var firstHeader: String? = null
                            var lastDivider: String? = null
                            for (item in visibleItems) {
                                val key = item.key as? String ?: continue
                                if (key.startsWith("header_")) {
                                    val letter = key.removePrefix("header_")
                                    if (letter in availableLetters && firstHeader == null) {
                                        firstHeader = letter
                                    }
                                } else if (key.startsWith("divider_")) {
                                    val letter = key.removePrefix("divider_")
                                    if (letter in availableLetters) {
                                        lastDivider = letter
                                    }
                                }
                            }
                            firstHeader ?: lastDivider
                                ?: indexToLetterMap[visibleItems.firstOrNull()?.index]
                        }
                    }

                    val activeHighlight = dragHighlight ?: scrollHighlight

                    val indexBarPaddingTop = if (isInFreeformWindow) 210.dp else 240.dp
                    val indexBarPaddingBottom = if (isInFreeformWindow) 40.dp else 120.dp

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(
                                end = 2.dp + if (isTablet) 12.dp else 0.dp,
                                top = indexBarPaddingTop,
                                bottom = indexBarPaddingBottom
                            )
                            .width(20.dp)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        val index =
                                            ((offset.y / size.height) * allLetters.size).toInt()
                                                .coerceIn(0, allLetters.lastIndex)
                                        val letter = allLetters[index]
                                        if (letter in availableLetters) {
                                            dragHighlight = letter
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.VirtualKey
                                            )
                                            letterIndexMap[letter]?.let { idx ->
                                                coroutineScope.launch {
                                                    isProgrammaticScroll = true
                                                    listState.animateScrollToItem(
                                                        idx
                                                    )
                                                    isProgrammaticScroll = false
                                                }
                                            }
                                        }
                                    },
                                    onVerticalDrag = { change, _ ->
                                        change.consume()
                                        val index =
                                            ((change.position.y / size.height) * allLetters.size).toInt()
                                                .coerceIn(0, allLetters.lastIndex)
                                        val letter = allLetters[index]
                                        if (letter in availableLetters && letter != dragHighlight) {
                                            dragHighlight = letter
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.VirtualKey
                                            )
                                            letterIndexMap[letter]?.let { idx ->
                                                coroutineScope.launch {
                                                    isProgrammaticScroll = true
                                                    listState.animateScrollToItem(
                                                        idx
                                                    )
                                                    isProgrammaticScroll = false
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = { dragHighlight = null },
                                    onDragCancel = { dragHighlight = null }
                                )
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            allLetters.forEach { letter ->
                                val isAvailable = letter in availableLetters
                                val isSelected = letter == activeHighlight && isAvailable
                                Text(
                                    text = letter,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) NexioTheme.colorScheme.primary
                                    else if (isAvailable) NexioTheme.colorScheme.onSurfaceVariantActions
                                    else NexioTheme.colorScheme.outline,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .then(
                                            if (isAvailable) Modifier.clickable(
                                                interactionSource = null,
                                                indication = null
                                            ) {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.VirtualKey
                                                )
                                                letterIndexMap[letter]?.let { idx ->
                                                    coroutineScope.launch {
                                                        isProgrammaticScroll = true
                                                        listState.animateScrollToItem(
                                                            idx
                                                        )
                                                        isProgrammaticScroll = false
                                                    }
                                                }
                                            } else Modifier
                                        ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 适配器选择底部弹窗（常驻组合，show 控制开合以播放动画）
    AdapterSelectionBottomSheet(
        show = selectedSchool != null,
        school = selectedSchool,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { selectedSchool = null },
        onAdapterSelected = { school, adapter ->
            selectedSchool = null
            onAdapterSelected(school, adapter)
        }
    )
}

/** 列表顶部「AI 教务导入」入口占掉的自选项数（字母条与搜索跳转的索引都要跳过它）。 */
private const val AI_ENTRY_ITEM_COUNT = 1

/**
 * 滚动区最上方的 AI 教务导入入口。
 *
 * 刻意做成整块可点的大卡而不是列表里的一行：它带来的是「换一种导入方式」，跟下面
 * 「按学校找适配」不是同一类操作，混进字母分组里会被当成又一个学校。
 */
@Composable
private fun AiEduImportEntryCard(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    // 主题蓝淡底 + 主题蓝文字：与下面「按学校找适配」的行区分开，深浅色与动态取色都跟随主题
    val primary = NexioTheme.colorScheme.primary
    val cardBg = primary.copy(alpha = if (NexioTheme.isDark) 0.16f else 0.12f)
    val titleColor = primary
    val subColor = primary.copy(alpha = 0.78f)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(20.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                }
                .background(cardBg)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ImportStr.aiEduImportTitle(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                Text(
                    text = ImportStr.aiEduEntrySubtitle(),
                    fontSize = 13.sp,
                    color = subColor,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Icon(
                imageVector = NexioIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = subColor
            )
        }
    }
}
