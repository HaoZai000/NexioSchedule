/** 学校选择页面 - 用于教务系统导入时选择学校 */
package com.haooz.chedule.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.school.AdapterData
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.SchoolRepository
import com.haooz.chedule.ui.components.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.components.liquidglass.ProgressiveBlurTopBar
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SchoolSelectionScreen(
    isUpdating: Boolean = false,
    isChecking: Boolean = false,
    updateProgress: Float = 0f,
    dataVersion: Int = 0,
    isInFreeformWindow: Boolean = false,
    isLiquidGlass: Boolean = false,
    liquidGlassBackdrop: LayerBackdrop? = null,
    onRefresh: () -> Unit = {},
    onSchoolSelected: (SchoolData, AdapterData) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val schoolRepository = remember { SchoolRepository(context) }
    val scrollBehavior = MiuixScrollBehavior()

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val allSchools = remember(dataVersion) { schoolRepository.getSchools() }

    // 0=学校导入(本科+研究生), 1=通用工具
    var selectedTab by remember { mutableIntStateOf(0) }

    // 通用工具网址输入弹窗状态
    var showUrlDialog by remember { mutableStateOf(false) }
    var pendingSchool by remember { mutableStateOf<SchoolData?>(null) }
    var pendingAdapter by remember { mutableStateOf<AdapterData?>(null) }
    var customUrl by remember { mutableStateOf("") }

    val filteredSchools = remember(allSchools, searchQuery, selectedTab) {
        allSchools.filter { school ->
            when (selectedTab) {
                0 -> school.adapters.any { it.category == AdapterData.CATEGORY_BACHELOR || it.category == AdapterData.CATEGORY_POSTGRADUATE }
                1 -> school.adapters.any { it.category == AdapterData.CATEGORY_GENERAL_TOOL }
                else -> false
            }
        }.filter { school ->
            searchQuery.isBlank() ||
                    school.name.contains(searchQuery, ignoreCase = true) ||
                    school.initial.contains(searchQuery, ignoreCase = true)
        }.sortedBy { it.initial.uppercase() + it.name }
    }

    val displayTabs = listOf("学校导入", "通用工具")

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            if (isLiquidGlass && liquidGlassBackdrop != null) {
                val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                ProgressiveBlurTopBar(
                    backdrop = liquidGlassBackdrop,
                ) {
                    SmallTopAppBar(
                        color = Color.Transparent,
                        title = "选择学校",
                        modifier = Modifier.zIndex(1f),
                        navigationIcon = {}
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(2f)
                            .offset(y = if (statusBarPadding > 0.dp) statusBarPadding + 5.dp else 42.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LiquidTopBarButton(
                            onClick = { onBack() },
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.Medium.ChevronBackward,
                            contentDescription = "返回",
                            modifier = Modifier.offset(x = 20.dp),
                            iconSize = 22.dp,
                            iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                            useBackdropShadow = true
                        )
                        if (!isUpdating) {
                            LiquidTopBarButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    onRefresh()
                                },
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Normal.Update,
                                contentDescription = "更新",
                                modifier = Modifier.offset(x = (-20).dp),
                                iconSize = 25.dp,
                                useBackdropShadow = true
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .offset(x = (-24).dp, y = (-4).dp)
                                    .size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        progress = updateProgress,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                SmallTopAppBar(
                    title = "选择学校",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(MiuixIcons.Back, contentDescription = "返回", modifier = Modifier.size(28.dp))
                        }
                    },
                    actions = {
                        if (isUpdating) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator()
                                } else {
                                    CircularProgressIndicator(
                                        progress = updateProgress
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onRefresh()
                            }, modifier = Modifier.padding(end = 4.dp)) {
                                Icon(MiuixIcons.Normal.Update, contentDescription = "更新", modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(top = paddingValues.calculateTopPadding() +
                        if (isLiquidGlass) {
                            if (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() > 0.dp) (-20).dp else (-32).dp
                        } else 0.dp)
                .fillMaxSize()
        ) {
            SearchBar(
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
                inputField = {
                    InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { searchExpanded = false },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = "搜索学校"
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it },
                outsideEndAction = {
                    Text(
                        modifier = Modifier
                            .padding(end = 18.dp)
                            .clickable(
                                interactionSource = null,
                                indication = null
                            ) {
                                searchExpanded = false
                                searchQuery = ""
                            },
                        text = "取消",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            ) {
                // 搜索结果内容 - 与主列表样式一致
                val groupedSearchResults = remember(filteredSchools) {
                    filteredSchools.groupBy { it.initial.uppercase() }
                }
                val searchGroupedEntries = groupedSearchResults.entries.toList()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 60.dp)
                ) {
                    searchGroupedEntries.forEachIndexed { index, (letter, schools) ->
                        // 分割线
                        if (index > 0) {
                            item(key = "search_divider_$letter") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
                                    color = MiuixTheme.colorScheme.outline,
                                    thickness = 0.5.dp
                                )
                            }
                        }
                        // 字母标题
                        item(key = "search_header_$letter") {
                            Text(
                                text = letter,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.padding(start = 26.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        // 学校列表
                        items(schools, key = { it.id }) { school ->
                            val isPostgrad = school.adapters.any { it.category == AdapterData.CATEGORY_POSTGRADUATE }
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        val adapters = schoolRepository.getAdaptersForSchool(school.id, AdapterData.CATEGORY_BACHELOR)
                                            .ifEmpty { schoolRepository.getAdaptersForSchool(school.id, AdapterData.CATEGORY_POSTGRADUATE) }
                                            .ifEmpty { schoolRepository.getAdaptersForSchool(school.id, AdapterData.CATEGORY_GENERAL_TOOL) }
                                        if (adapters.isNotEmpty()) {
                                            val adapter = adapters.first()
                                            if (adapter.category == AdapterData.CATEGORY_GENERAL_TOOL) {
                                                pendingSchool = school
                                                pendingAdapter = adapter
                                                customUrl = adapter.importUrl ?: ""
                                                showUrlDialog = true
                                            } else {
                                                onSchoolSelected(school, adapter)
                                            }
                                        }
                                        searchExpanded = false
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
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    if (isPostgrad) {
                                        Text(
                                            text = "研究生",
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayTabs.forEachIndexed { index, tabName ->
                    val isSelected = selectedTab == index
                    Surface(
                        modifier = Modifier
                            .clip(com.kyant.shapes.RoundedRectangle(20.dp))
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                selectedTab = index
                            },
                        shape = com.kyant.shapes.RoundedRectangle(20.dp),
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = tabName,
                                fontSize = 14.sp,
                                color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            when {
                filteredSchools.isEmpty() && !isUpdating -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isBlank()) "暂无学校数据" else "未找到匹配的学校",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
                isUpdating && filteredSchools.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 64.dp)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator()
                            } else {
                                CircularProgressIndicator(
                                    progress = updateProgress,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = if (isChecking) "正在检查是否有更新" else "正在更新学校数据",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    }
                }
                else -> {
                    // 按首字母分组
                    val groupedSchools = remember(filteredSchools) {
                        filteredSchools.groupBy { it.initial.uppercase() }
                    }
                    val allLetters = remember { ('A'..'Z').map { it.toString() } }
                    val availableLetters = remember(groupedSchools) { groupedSchools.keys.toSet() }

                    // 构建字母到LazyColumn item index的映射
                    val letterIndexMap = remember(groupedSchools) {
                        val map = mutableMapOf<String, Int>()
                        var itemIndex = 0
                        groupedSchools.entries.forEachIndexed { index, (letter, schools) ->
                            if (index > 0) itemIndex++ // divider
                            map[letter] = itemIndex // header
                            itemIndex++ // header
                            itemIndex += schools.size // schools
                        }
                        map
                    }

                    // 构建item index到字母的反向映射
                    val indexToLetterMap = remember(groupedSchools) {
                        val map = mutableMapOf<Int, String>()
                        var itemIndex = 0
                        groupedSchools.entries.forEachIndexed { index, (letter, schools) ->
                            if (index > 0) itemIndex++ // divider
                            map[itemIndex] = letter // header
                            itemIndex++ // header
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
                                    if (!isLiquidGlass) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
                                ),
                            contentPadding = PaddingValues(
                                bottom = 60.dp
                            )
                        ) {
                            val groupedEntries = groupedSchools.entries.toList()
                            groupedEntries.forEachIndexed { index, (letter, schools) ->
                                // 分割线（第一个分组不显示）
                                if (index > 0) {
                                    item(key = "divider_$letter") {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
                                            color = MiuixTheme.colorScheme.outline,
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                                // 字母分组标题
                                item(key = "header_$letter") {
                                    Text(
                                        text = letter,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.padding(start = 26.dp, top = 16.dp, bottom = 4.dp)
                                    )
                                }
                                // 该字母下的学校列表
                                items(schools, key = { it.id }) { school ->
                                    val isPostgrad = school.adapters.any { it.category == AdapterData.CATEGORY_POSTGRADUATE }
                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                val adapters = schoolRepository.getAdaptersForSchool(school.id, AdapterData.CATEGORY_BACHELOR)
                                                    .ifEmpty { schoolRepository.getAdaptersForSchool(school.id, AdapterData.CATEGORY_POSTGRADUATE) }
                                                    .ifEmpty { schoolRepository.getAdaptersForSchool(school.id, AdapterData.CATEGORY_GENERAL_TOOL) }
                                                if (adapters.isNotEmpty()) {
                                                    val adapter = adapters.first()
                                                    if (adapter.category == AdapterData.CATEGORY_GENERAL_TOOL) {
                                                        pendingSchool = school
                                                        pendingAdapter = adapter
                                                        customUrl = adapter.importUrl ?: ""
                                                        showUrlDialog = true
                                                    } else {
                                                        onSchoolSelected(school, adapter)
                                                    }
                                                }
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
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                            if (isPostgrad) {
                                                Text(
                                                    modifier = Modifier.padding(start = 4.dp),
                                                    text = "研究生",
                                                    fontSize = 14.sp,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 右侧字母索引条
                        var dragHighlight by remember { mutableStateOf<String?>(null) }
                        val coroutineScope = rememberCoroutineScope()

                        // 根据滚动位置计算当前可见字母
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

                        val indexBarPaddingTop = if (isInFreeformWindow) 10.dp else 60.dp
                        val indexBarPaddingBottom = if (isInFreeformWindow) 30.dp else 140.dp

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 2.dp, top = indexBarPaddingTop, bottom = indexBarPaddingBottom)
                                .width(20.dp)
                                .fillMaxHeight()
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onDragStart = { offset ->
                                                val index = ((offset.y / size.height) * allLetters.size).toInt().coerceIn(0, allLetters.lastIndex)
                                                val letter = allLetters[index]
                                                if (letter in availableLetters) {
                                                    dragHighlight = letter
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                    letterIndexMap[letter]?.let { idx ->
                                                        coroutineScope.launch { listState.animateScrollToItem(idx) }
                                                    }
                                                }
                                            },
                                            onVerticalDrag = { change, _ ->
                                                change.consume()
                                                val index = ((change.position.y / size.height) * allLetters.size).toInt().coerceIn(0, allLetters.lastIndex)
                                                val letter = allLetters[index]
                                                if (letter in availableLetters && letter != dragHighlight) {
                                                    dragHighlight = letter
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                    letterIndexMap[letter]?.let { idx ->
                                                        coroutineScope.launch { listState.animateScrollToItem(idx) }
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
                                            color = if (isSelected) MiuixTheme.colorScheme.primary
                                            else if (isAvailable) MiuixTheme.colorScheme.onSurfaceVariantActions
                                            else MiuixTheme.colorScheme.outline,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .then(
                                                    if (isAvailable) Modifier.clickable(
                                                        interactionSource = null,
                                                        indication = null
                                                    ) {
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                        letterIndexMap[letter]?.let { idx ->
                                                            coroutineScope.launch { listState.animateScrollToItem(idx) }
                                                        }
                                                    } else Modifier
                                                ),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 通用工具网址输入弹窗
        OverlayDialog(
            title = "输入网址",
            summary = "请输入要访问的教务系统网址",
            show = showUrlDialog,

            onDismissRequest = { showUrlDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    label = "网址",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showUrlDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            if (customUrl.isNotBlank()) {
                                pendingAdapter?.let { adapter ->
                                    onSchoolSelected(
                                        pendingSchool!!,
                                        adapter.copy(importUrl = customUrl)
                                    )
                                }
                                showUrlDialog = false
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
