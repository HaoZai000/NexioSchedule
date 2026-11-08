/** 适配器选择底部弹窗 - 显示指定学校的全部适配器（含描述与贡献者信息） */
package com.haooz.chedule.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
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
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.CardReveal
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.ExtractedShareTools
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalBlurBottomSheetContentExpanded
import top.yukonga.miuix.kmp.overlay.LocalSheetContentBackdrop
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
    onAdapterSelected: (SchoolData, AdapterData) -> Unit
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
    val sheetBottomPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp

    val startAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        LiquidTopBarButton(
            onClick = onDismissRequest,
            backdrop = LocalSheetContentBackdrop.current ?: liquidGlassBackdrop!!,
            icon = MiuixIcons.Normal.Close,
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
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
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
                        .padding(start = 16.dp, end = 16.dp),
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
                    Spacer(modifier = Modifier.height(if (isTablet) 4.dp else sheetBottomPadding))
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
            NativeMiuixTextField(
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
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = { showUrlDialog = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
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
    Surface(
        color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainerHigh else if (isAppDarkTheme()) Color(0xFF303030) else Color(0xFFFFFFFF),
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
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = categoryName,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = adapter.description.ifBlank { ImportStr.noDescription() },
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            if (adapter.maintainer.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ImportStr.maintainerN(adapter.maintainer),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}
