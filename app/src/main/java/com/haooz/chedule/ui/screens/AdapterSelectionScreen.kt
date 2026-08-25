/** 适配器选择底部弹窗 - 显示指定学校的全部适配器（含描述与贡献者信息） */
package com.haooz.chedule.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.school.AdapterData
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.SchoolRepository
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.overlay.LocalSheetTopBarMaterial
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun AdapterSelectionBottomSheet(
    show: Boolean,
    school: SchoolData?,
    liquidGlassBackdrop: Backdrop? = null,
    onDismissRequest: () -> Unit,
    onAdapterSelected: (SchoolData, AdapterData) -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val schoolRepository = remember { SchoolRepository(context) }

    // 退出动画期间保留最后选中的学校，避免内容瞬间消失导致关闭看起来无动画
    var lastSchool by remember { mutableStateOf<SchoolData?>(null) }
    LaunchedEffect(school) {
        if (school != null) lastSchool = school
    }
    val currentSchool = lastSchool

    val adapters = remember(currentSchool?.id) {
        val s = currentSchool ?: return@remember emptyList()
        schoolRepository.getSchoolById(s.id)?.adapters ?: s.adapters
    }

    var sheetContentBackdrop by remember { mutableStateOf<Backdrop?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var pendingAdapter by remember { mutableStateOf<AdapterData?>(null) }
    var customUrl by remember { mutableStateOf("") }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    val startAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        LiquidTopBarButton(
            onClick = onDismissRequest,
            backdrop = sheetContentBackdrop ?: liquidGlassBackdrop!!,
            icon = MiuixIcons.Normal.Close,
            contentDescription = "关闭",
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
                        text = "该学校暂无适配器",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            } else if (currentSchool != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .overScrollVertical()
                        .scrollEndHaptic(
                            hapticFeedbackType = HapticFeedbackType.TextHandleMove
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(modifier = Modifier.height(if (isTablet) 56.dp else 58.dp))
                    adapters.forEach { adapter ->
                        AdapterRow(
                            adapter = adapter,
                            categoryName = when (adapter.category) {
                                AdapterData.CATEGORY_BACHELOR -> "本科"
                                AdapterData.CATEGORY_POSTGRADUATE -> "研究生"
                                AdapterData.CATEGORY_GENERAL_TOOL -> "通用工具"
                                else -> "其他"
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
                    Spacer(modifier = Modifier.height(if (isTablet) 4.dp else 160.dp))
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
            onSheetContentBackdropCreated = { sheetContentBackdrop = it },
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
            sheetOffsetDp = 100.dp,
            onSheetContentBackdropCreated = { sheetContentBackdrop = it },
            startAction = startAction,
        ) {
            sheetContent()
        }
    }

    OverlayDialog(
        title = "输入网址",
        summary = "请输入要访问的教务系统网址",
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
        color = if (isAppDarkTheme()) Color(0xFF363636).copy(alpha = 0.62f) else Color(0xFFFFFFFF).copy(alpha = 0.7f),
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
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = adapter.description.ifBlank { "暂无详细描述" },
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            if (adapter.maintainer.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "贡献者：${adapter.maintainer}",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.7f)
                )
            }
        }
    }
}
