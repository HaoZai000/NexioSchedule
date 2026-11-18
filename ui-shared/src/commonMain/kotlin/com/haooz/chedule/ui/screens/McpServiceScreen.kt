package com.haooz.chedule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.mcp.McpServiceBridge
import com.haooz.chedule.data.mcp.McpServicePrefs
import com.haooz.chedule.data.mcp.McpToolCatalog
import com.haooz.chedule.i18n.McpStr
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import kotlinx.coroutines.delay
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.nativetextfield.NativeTextField
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.switch.Switch
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.nexioui.window.isTabletWidth
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic

/**
 * 「MCP 服务」页：把课表以只读 MCP 工具的形式暴露给手机上的 AI 助手。
 *
 * 服务只监听回环地址，由前台服务保活；关闭开关即同时停掉服务与常驻通知。
 */
@Composable
fun McpServiceScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    onCopyToClipboard: (String) -> Unit = {},
) {
    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val haptic = LocalHapticFeedback.current
    val theme = NexioTheme.colorScheme

    var enabled by remember { mutableStateOf(McpServicePrefs.isEnabled()) }
    var token by remember { mutableStateOf(McpServicePrefs.token()) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    fun applyEnabled(value: Boolean) {
        enabled = value
        McpServicePrefs.setEnabled(value)
        if (value) McpServiceBridge.start(token) else McpServiceBridge.stop()
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
                        pageTopExtra(isTablet, hasSmallTitle = false),
                    bottom = 60.dp,
                ),
        ) {
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = McpStr.enable(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.onSurface,
                        )
                        Text(
                            text = when {
                                !McpServiceBridge.supported -> McpStr.unsupported()
                                enabled -> McpStr.enableOn()
                                else -> McpStr.enableOff()
                            },
                            fontSize = 14.sp,
                            color = theme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            if (!McpServiceBridge.supported) return@Switch
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            applyEnabled(value)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(SECTION_GAP))
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = McpStr.serverAddress(),
                            fontSize = 14.sp,
                            color = theme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = McpToolCatalog.SERVER_URL,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    FlatTextButton(
                        text = if (copied) McpStr.copied() else McpStr.copy(),
                        onClick = {
                            onCopyToClipboard(McpToolCatalog.SERVER_URL)
                            copied = true
                        },
                        minWidth = 64.dp,
                        minHeight = 36.dp,
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        cornerRadius = 100.dp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SECTION_GAP))
            SmallTitle(text = McpStr.authSection())
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = McpStr.tokenLabel(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = theme.onSurface,
                        )
                        NativeTextField(
                            value = token,
                            onValueChange = {
                                token = it
                                McpServicePrefs.setToken(it)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            hint = McpStr.tokenHint(),
                            singleLine = true,
                            textAlign = TextAlign.End,
                            textStyle = TextStyle(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(SECTION_GAP))
            SmallTitle(text = McpStr.howToSection())
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    McpStr.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = "${index + 1}.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = theme.primary,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                text = step(),
                                fontSize = 14.sp,
                                color = theme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SECTION_GAP))
            SmallTitle(text = McpStr.toolsSection())
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = McpStr.toolsNote(),
                        style = NexioTheme.textStyles.footnote2,
                        fontSize = 14.sp,
                        color = theme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                    )
                    McpToolCatalog.tools.forEachIndexed { index, tool ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = theme.onSurfaceVariantActions.copy(alpha = 0.1f),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = tool.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = theme.onSurface,
                            )
                            Text(
                                text = if (com.haooz.chedule.i18n.AppLanguage.isZh) {
                                    tool.descriptionZh
                                } else {
                                    tool.descriptionEn
                                },
                                fontSize = 14.sp,
                                color = theme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 卡片之间的间距 */
private val SECTION_GAP = 12.dp
