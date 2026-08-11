/** 学校选择页面 - 用于教务系统导入时选择学校 */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.school.AdapterData
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.SchoolRepository
import com.haooz.chedule.ui.components.NativeMiuixTextField
import com.haooz.chedule.ui.components.SharedScrollBehavior
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun SchoolSelectionScreen(
    modifier: Modifier = Modifier,
    isUpdating: Boolean = false,
    isChecking: Boolean = false,
    updateProgress: Float = 0f,
    dataVersion: Int = 0,
    isInFreeformWindow: Boolean = false,
    scrollBehavior: SharedScrollBehavior? = null,
    searchQuery: String = "",
    selectedTab: Int = 0,
    topContentPadding: Dp = 0.dp,
    onSchoolSelected: (SchoolData, AdapterData) -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val schoolRepository = remember { SchoolRepository(context) }

    val allSchools = remember(dataVersion) { schoolRepository.getSchools() }

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

    val listState = rememberLazyListState()

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 0.dp

    Column(
        modifier = modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)
    ) {
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
                val groupedSchools = remember(filteredSchools) {
                    filteredSchools.groupBy { it.initial.uppercase() }
                }
                val allLetters = remember { ('A'..'Z').map { it.toString() } }
                val availableLetters = remember(groupedSchools) { groupedSchools.keys.toSet() }

                val letterIndexMap = remember(groupedSchools) {
                    val map = mutableMapOf<String, Int>()
                    var itemIndex = 0
                    groupedSchools.entries.forEachIndexed { index, (letter, schools) ->
                        if (index > 0) itemIndex++
                        map[letter] = itemIndex
                        itemIndex++
                        itemIndex += schools.size
                    }
                    map
                }

                val indexToLetterMap = remember(groupedSchools) {
                    val map = mutableMapOf<Int, String>()
                    var itemIndex = 0
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
                        groupedEntries.forEachIndexed { index, (letter, schools) ->
                            if (index > 0) {
                                item(key = "divider_$letter") {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            horizontal = 26.dp,
                                            vertical = 12.dp
                                        ),
                                        color = MiuixTheme.colorScheme.outline,
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                            item(key = "header_$letter") {
                                Text(
                                    text = letter,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
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
                                        .then(if (isTablet) Modifier.clip(RoundedRectangle(20.dp)) else Modifier)
                                        .clickable {
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.VirtualKey
                                            )
                                            val adapters =
                                                schoolRepository.getAdaptersForSchool(
                                                    school.id,
                                                    AdapterData.CATEGORY_BACHELOR
                                                )
                                                    .ifEmpty {
                                                        schoolRepository.getAdaptersForSchool(
                                                            school.id,
                                                            AdapterData.CATEGORY_POSTGRADUATE
                                                        )
                                                    }
                                                    .ifEmpty {
                                                        schoolRepository.getAdaptersForSchool(
                                                            school.id,
                                                            AdapterData.CATEGORY_GENERAL_TOOL
                                                        )
                                                    }
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

                    val indexBarPaddingTop = if (isInFreeformWindow) 10.dp else 60.dp
                    val indexBarPaddingBottom = if (isInFreeformWindow) 30.dp else 140.dp

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
                                                    listState.animateScrollToItem(
                                                        idx
                                                    )
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
                                                    listState.animateScrollToItem(
                                                        idx
                                                    )
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
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.VirtualKey
                                                )
                                                letterIndexMap[letter]?.let { idx ->
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(
                                                            idx
                                                        )
                                                    }
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
            NativeMiuixTextField(
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
