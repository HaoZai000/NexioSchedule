/** 适配器选择底部弹窗 - 显示指定学校的全部适配器（含描述与贡献者信息） */
package com.haooz.chedule.ui.activities.eduimport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.school.AdapterData
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.SchoolRepository
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.components.CardReveal
import com.haooz.chedule.ui.components.sheetContentBottomInset
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.utils.ExtractedShareTools
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.nexioui.blurbottomsheet.BlurBottomSheet
import com.haooz.nexioui.blurbottomsheet.BlurBottomSheetTablet
import com.haooz.nexioui.blurbottomsheet.LocalBlurBottomSheetContentExpanded
import com.haooz.nexioui.blurbottomsheet.LocalSheetContentBackdrop
import com.haooz.nexioui.blurbottomsheet.LocalSheetTopBarMaterial
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Close
import com.haooz.nexioui.iconbutton.IconButton
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.surface.Surface
import com.haooz.nexioui.textfield.TextField
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle

/**
 * 适配器选择底部弹窗
 *
 * @param show 是否显示
 * @param school 当前选中的学校
 * @param liquidGlassBackdrop 模糊背景
 * @param onDismissRequest 关闭回调
 * @param onAdapterSelected 选择适配器回调
 */
@Composable
fun AdapterSelectionBottomSheet(
    show: Boolean,
    school: SchoolData?,
    liquidGlassBackdrop: GlassBackdrop? = null,
    onDismissRequest: () -> Unit,
    onAdapterSelected: (SchoolData, AdapterData) -> Unit,
    /** 弹窗底部固定操作区；null = 保持原有纯列表形态 */
    footerContent: (@Composable () -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val schoolRepository = remember { SchoolRepository() }

    // 退出动画期间保留最后选中的学校，避免内容瞬间消失导致关闭看起来无动画
    var lastSchool by remember { mutableStateOf<SchoolData?>(null) }
    LaunchedEffect(school) {
        if (school != null) lastSchool = school
    }
    val currentSchool = lastSchool

    val adapters = remember(currentSchool?.id) {
        val s = currentSchool ?: return@remember emptyList()
        val raw = schoolRepository.getSchoolById(s.id)?.adapters ?: s.adapters
        // WakeUp / 星链已提取到课表导入页，教务导入通用工具不再展示
        raw.filter { it.adapterId !in ExtractedShareTools.ADAPTER_IDS }
    }

    var showUrlDialog by remember { mutableStateOf(false) }
    var pendingAdapter by remember { mutableStateOf<AdapterData?>(null) }
    var customUrl by remember { mutableStateOf("") }

    val isTablet = screenWidthDp() >= 600

    val startAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        IconButton(
            onClick = onDismissRequest,
            backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
            icon = NexioIcons.Normal.Close,
            contentDescription = CommonStr.close(),
            modifier = Modifier.padding(start = if (isTablet) 16.dp else 18.dp),
            iconSize = 24.dp,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }

    val sheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (currentSchool != null && adapters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ImportStr.noAdapters(),
                        style = NexioTheme.textStyles.body1,
                        color = NexioTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            } else if (currentSchool != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (LocalBlurBottomSheetContentExpanded.current) Modifier.fillMaxHeight()
                            else Modifier,
                        )
                        .overScrollVertical()
                        .scrollEndHaptic(
                            hapticFeedbackType = HapticFeedbackType.TextHandleMove
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp)
                        .sheetContentBottomInset(isTablet),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(modifier = Modifier.height(if (isTablet) 56.dp else 58.dp))
                    // 逐行入场由 CardReveal 的 index 错开，页面不再自己维护步进
                    adapters.forEachIndexed { index, adapter ->
                        CardReveal(index = index) {
                            AdapterRow(
                                adapter = adapter,
                                categoryName = when (adapter.category) {
                                    AdapterData.CATEGORY_BACHELOR -> ImportStr.categoryUndergraduate()
                                    AdapterData.CATEGORY_POSTGRADUATE -> ImportStr.categoryPostgraduate()
                                    AdapterData.CATEGORY_GENERAL_TOOL -> ImportStr.generalTools()
                                    else -> CommonStr.other()
                                },
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    if (adapter.category == AdapterData.CATEGORY_GENERAL_TOOL) {
                                        pendingAdapter = adapter
                                        customUrl = adapter.importUrl ?: ""
                                        showUrlDialog = true
                                    } else {
                                        onAdapterSelected(currentSchool, adapter)
                                    }
                                }
                            )
                        }
                    }
                    // 继续 AI 入口跟随列表滚动，放在所有适配器之后
                    footerContent?.invoke()
                }
            }
        }
    }

    if (isTablet) {
        BlurBottomSheetTablet(
            show = show,
            title = currentSchool?.name ?: "",
            dimBackground = true,
            onDismissRequest = onDismissRequest,
            startAction = startAction,
        ) {
            sheetContent()
        }
    } else {
        BlurBottomSheet(
            show = show,
            title = currentSchool?.name ?: "",
            dimBackground = true,
            onDismissRequest = onDismissRequest,
            startAction = startAction,
        ) {
            sheetContent()
        }
    }

    OverlayDialog(
        title = ImportStr.enterUrl(),
        summary = ImportStr.enterUrlHint(),
        show = showUrlDialog,
        onDismissRequest = { showUrlDialog = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = ImportStr.urlLabel(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlatTextButton(
                    text = CommonStr.cancel(),
                    onClick = { showUrlDialog = false },
                    modifier = Modifier.weight(1f)
                )
                FlatTextButton(
                    text = CommonStr.ok(),
                    onClick = {
                        if (customUrl.isNotBlank()) {
                            pendingAdapter?.let { adapter ->
                                currentSchool?.let { s ->
                                    onAdapterSelected(s, adapter.copy(importUrl = customUrl))
                                }
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

@Composable
private fun AdapterRow(
    adapter: AdapterData,
    categoryName: String,
    onClick: () -> Unit
) {
    val dark = isAppDarkTheme()
    // AI 仓库生成的适配器叠一层主题蓝淡底，和拾光的人工适配区分开；深浅色与动态取色都跟随主题
    val fromAiDock = adapter.source == AdapterData.SOURCE_AI_DOCK
    Surface(
        color = when {
            fromAiDock -> NexioTheme.colorScheme.primary.copy(alpha = if (NexioTheme.isDark) 0.16f else 0.12f)
            NexioTheme.isDynamicColor -> NexioTheme.colorScheme.surfaceContainerHigh
            dark -> Color(0xFF303030)
            else -> Color(0xFFFFFFFF)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = adapter.adapterName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = NexioTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = categoryName,
                    fontSize = 14.sp,
                    color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = adapter.description.ifBlank { ImportStr.noDescription() },
                fontSize = 14.sp,
                color = NexioTheme.colorScheme.onSurfaceVariantActions,
            )
            if (adapter.maintainer.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ImportStr.maintainerN(adapter.maintainer),
                    fontSize = 14.sp,
                    color = NexioTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}
