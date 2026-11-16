package com.haooz.chedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.ai.AiByokConfig
import com.haooz.chedule.data.ai.AiByokStore
import com.haooz.chedule.data.ai.AiCapabilities
import com.haooz.chedule.data.ai.AiClient
import com.haooz.chedule.data.ai.AiProviders
import com.haooz.chedule.data.ai.AiResult
import com.haooz.chedule.data.platform.HttpTimeouts
import com.haooz.chedule.data.platform.createHttpService
import com.haooz.chedule.i18n.AiStr
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.shape.ContinuousCapsule
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NativeTextField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 「AI 功能」页：免费额度 + 自己的服务商（BYOK）+ 各功能入口。
 *
 * 额度来自服务端 `/api/ai/capabilities`（按本机 device_id 计）；BYOK 只改本机配置，
 * 开启后通知精简与教务导入都直连用户填的服务商，不再占用免费额度。
 *
 * 版式约定：卡片内部一律自己控 padding（`insideMargin = 0`），
 * 卡片之间统一用 [SECTION_GAP]；卡片内输入行用无边框的 [InlineInputRow]。
 */
@Composable
fun AiFeaturesScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: GlassBackdrop? = null,
    onOpenReminder: () -> Unit = {},
    onOpenEduImport: () -> Unit = {},
    onOpenTextImport: () -> Unit = {},
) {
    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val haptic = LocalHapticFeedback.current
    val theme = MiuixTheme.colorScheme

    val http = remember { createHttpService(HttpTimeouts(connectSeconds = 10, readSeconds = 20)) }
    var capabilities by remember { mutableStateOf<AiCapabilities?>(null) }
    var quotaError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        quotaError = null
        val result = runCatching { AiClient(http).capabilities() }
            .getOrElse { AiResult.Error(it.message ?: AiStr.quotaUnavailable()) }
        when (result) {
            is AiResult.Ok -> capabilities = result.value
            is AiResult.Error -> quotaError = result.message
        }
        loading = false
    }

    val stored = remember { AiByokStore.load() }
    var provider by remember { mutableStateOf(stored.provider) }
    val providerPreset = AiProviders.byId(provider)
    val isFreeProvider = AiProviders.isFree(provider)
    val isCustomProvider = AiProviders.isCustom(provider)
    var baseUrl by remember { mutableStateOf(stored.baseUrl) }
    var model by remember { mutableStateOf(stored.model) }
    var modelCustom by remember {
        mutableStateOf(
            stored.model.isNotBlank() &&
                (isCustomProvider || stored.model !in providerPreset.models)
        )
    }
    var apiKey by remember { mutableStateOf(stored.apiKey) }

    val dropdownColors = if (liquidGlassBackdrop != null) {
        DropdownDefaults.dropdownColors(
            containerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent,
        )
    } else {
        DropdownDefaults.dropdownColors()
    }

    /** 没有保存按钮：服务商、模型与 key 任意一项改动都立即落盘。 */
    fun persistByok(
        providerId: String = provider,
        url: String = baseUrl,
        modelName: String = model,
        key: String = apiKey,
    ) {
        AiByokStore.save(
            AiByokConfig(
                provider = providerId,
                enabled = !AiProviders.isFree(providerId),
                baseUrl = url,
                model = modelName,
                apiKey = key,
            )
        )
    }

    Scaffold(topBar = {}) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .then(
                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier,
                )
                .verticalScroll(rememberScrollState())
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = paddingValues.calculateTopPadding() +
                        CollapsibleTopAppBarDefaults.CollapsedHeight +
                        pageTopExtra(isTablet, hasSmallTitle = true),
                    bottom = 120.dp,
                ),
        ) {
            SmallTitle(text = AiStr.freeQuota())
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    val quota = capabilities?.quota
                    when {
                        loading -> Text(
                            text = AiStr.quotaLoading(),
                            style = MiuixTheme.textStyles.body2,
                            color = theme.onSurfaceVariantSummary,
                        )

                        quota != null -> {
                            QuotaLine(
                                label = AiStr.quotaShrink(),
                                used = quota.shrinkUsed,
                                limit = quota.shrinkLimit,
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                thickness = 0.5.dp,
                                color = theme.onSurfaceVariantActions.copy(alpha = 0.1f),
                            )
                            QuotaLine(
                                label = AiStr.quotaAgent(),
                                used = quota.agentUsed,
                                limit = quota.agentLimit,
                            )
                        }

                        else -> Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (quotaError != null) AiStr.quotaUnavailable() else AiStr.serverDisabled(),
                                style = MiuixTheme.textStyles.body1,
                                color = theme.onSurface,
                            )
                            quotaError?.let { message ->
                                Text(
                                    text = message,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = theme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            TextButton(
                                text = AiStr.retry(),
                                onClick = { reloadKey++ },
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .align(Alignment.Start),
                            )
                        }
                    }
                    Text(
                        text = AiStr.quotaNote(),
                        style = MiuixTheme.textStyles.footnote2,
                        fontSize = 14.sp,
                        color = theme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SECTION_GAP))
            SmallTitle(text = AiStr.byokSection())
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 服务商：默认「每日免费 AI」，其余为预设 + 自定义
                    OverlayDropdownMenu(
                        title = AiStr.providerLabel(),
                        entry = DropdownEntry(
                            items = AiProviders.presets.map { preset ->
                                DropdownItem(
                                    text = when {
                                        AiProviders.isFree(preset.id) -> AiStr.providerFree()
                                        AiProviders.isCustom(preset.id) -> AiStr.providerCustom()
                                        else -> preset.label
                                    },
                                    selected = preset.id == provider,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        provider = preset.id
                                        modelCustom = when {
                                            AiProviders.isFree(preset.id) -> false
                                            AiProviders.isCustom(preset.id) -> true
                                            else -> false
                                        }
                                        if (preset.models.isNotEmpty()) {
                                            baseUrl = preset.baseUrl
                                            model = preset.defaultModel
                                        }
                                        persistByok()
                                    },
                                )
                            }
                        ),
                        collapseOnSelection = true,
                        maxHeight = 420.dp,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        dropdownColors = dropdownColors,
                    )

                    // 免费额度与自填配置互斥：切换服务商时高度做展开/收起动画
                    CardExpand(visible = isFreeProvider) {
                        Text(
                            text = AiStr.providerFreeNote(),
                            fontSize = 13.sp,
                            color = theme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                        )
                    }
                    CardExpand(visible = !isFreeProvider) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 自定义 / 预设两段都走展开动画：从预设切到自定义时接口地址不会突然出现
                            CardExpand(visible = isCustomProvider) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    InlineInputRow(
                                        label = AiStr.byokBaseUrl(),
                                        value = baseUrl,
                                        onValueChange = {
                                            baseUrl = it
                                            persistByok(url = it)
                                        },
                                        hint = AiStr.byokBaseUrlHint(),
                                    )
                                    InlineInputRow(
                                        label = AiStr.byokModel(),
                                        value = model,
                                        onValueChange = {
                                            model = it
                                            persistByok(modelName = it)
                                        },
                                        hint = AiStr.byokModelHint(),
                                    )
                                }
                            }
                            CardExpand(visible = !isCustomProvider) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // 预设服务商：模型也给下拉，避免用户记模型名
                                    OverlayDropdownMenu(
                                        title = AiStr.byokModel(),
                                        entry = DropdownEntry(
                                            items = providerPreset.models.map { name ->
                                                DropdownItem(
                                                    text = name,
                                                    selected = !modelCustom && name == model,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                        model = name
                                                        modelCustom = false
                                                        persistByok()
                                                    },
                                                )
                                            } + DropdownItem(
                                                text = AiStr.modelCustom(),
                                                selected = modelCustom,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                    modelCustom = true
                                                },
                                            )
                                        ),
                                        collapseOnSelection = true,
                                        maxHeight = 360.dp,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        dropdownColors = dropdownColors,
                                    )
                                    CardExpand(visible = modelCustom) {
                                        InlineInputRow(
                                            label = AiStr.byokModel(),
                                            value = model,
                                            onValueChange = {
                                                model = it
                                                persistByok(modelName = it)
                                            },
                                            hint = AiStr.byokModelHint(),
                                        )
                                    }
                                }
                            }

                            InlineInputRow(
                                label = AiStr.byokApiKey(),
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                    persistByok(key = it)
                                },
                                hint = AiStr.byokApiKeyHint(),
                            )

                            Text(
                                text = AiStr.byokPrivacy(),
                                style = MiuixTheme.textStyles.footnote2,
                                fontSize = 14.sp,
                                color = theme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                            )
                        }
                    }

                }
            }

            Spacer(modifier = Modifier.height(SECTION_GAP))
            SmallTitle(text = AiStr.features())
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FeatureRow(AiStr.entryShrink(), AiStr.entryShrinkSummary(), onOpenReminder)
                    FeatureDivider()
                    FeatureRow(AiStr.entryEduImport(), AiStr.entryEduImportSummary(), onOpenEduImport)
                    FeatureDivider()
                    FeatureRow(AiStr.entryTextImport(), AiStr.entryTextImportSummary(), onOpenTextImport)
                }
            }
        }
    }
}

/** 卡片之间的间距 */
private val SECTION_GAP = 12.dp

/**
 * 行内输入：左边标签、右边无边框输入区（与「添加课程」弹窗同一种写法）。
 * 不用带底框的 NativeMiuixTextField，避免卡片里再套一层卡片。
 */
@Composable
private fun InlineInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
) {
    val theme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = theme.onSurface,
        )
        NativeTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            hint = hint,
            singleLine = true,
            textAlign = TextAlign.End,
            textStyle = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun QuotaLine(label: String, used: Int, limit: Int) {
    val theme = MiuixTheme.colorScheme
    val fraction = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = theme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = AiStr.quotaRemaining((limit - used).coerceAtLeast(0)),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = theme.primary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(4.dp)
                .background(theme.onSurfaceVariantActions.copy(alpha = 0.12f), ContinuousCapsule()),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .background(theme.primary, ContinuousCapsule()),
                )
            }
        }
        Text(
            text = AiStr.quotaUsed(used, limit),
            style = MiuixTheme.textStyles.footnote2,
            color = theme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun FeatureDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.1f),
    )
}

@Composable
private fun FeatureRow(title: String, summary: String, onClick: () -> Unit) {
    val theme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 13.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = theme.onSurface,
            )
            Text(
                text = summary,
                fontSize = 13.sp,
                color = theme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = theme.onSurfaceVariantActions,
        )
    }
}
