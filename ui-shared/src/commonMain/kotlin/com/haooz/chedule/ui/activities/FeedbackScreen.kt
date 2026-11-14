/** 应用内反馈（表单页）- Screen */
package com.haooz.chedule.ui.activities

import top.yukonga.miuix.kmp.basic.ButtonDefaults
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.platform.AppInfo
import com.haooz.chedule.data.platform.FeedbackClient
import com.haooz.chedule.data.platform.FeedbackKind
import com.haooz.chedule.data.platform.currentDeviceInfo
import com.haooz.chedule.i18n.FeedbackStr
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 表单状态。
 *
 * 提到路由层持有，是因为「提交」按钮画在 [com.haooz.chedule.ui.components.DocumentPageScaffold]
 * 的 `overlay` 里（玻璃节点不能采样自己所在的那层 backdrop），而表单内容在 content 里 ——
 * 两边必须是同一份状态。
 */
@Stable
class FeedbackFormState {

    var kind by mutableStateOf(FeedbackKind.Bug)
    var title by mutableStateOf("")
    var content by mutableStateOf("")
    var email by mutableStateOf("")
    var submitting by mutableStateOf(false)
    var emailError by mutableStateOf(false)

    /** 提交成功后的单号；非空即代表当前处于「成功态」 */
    var submittedId by mutableStateOf<String?>(null)

    val canSubmit: Boolean get() = content.trim().length >= 5 && !submitting

    /** 邮箱留空视为「不填」，只有填了才校验格式 */
    fun validateEmail(): Boolean {
        val value = email.trim()
        emailError = value.isNotEmpty() && !EMAIL_PATTERN.matches(value)
        return !emailError
    }

    suspend fun submit(): Result<String> {
        if (!canSubmit) return Result.failure(IllegalArgumentException(FeedbackStr.submitTooShort()))
        submitting = true
        val result = FeedbackClient.submit(kind = kind, title = title, content = content, email = email)
        submitting = false
        result.onSuccess { id -> submittedId = id.ifBlank { "—" } }
        return result
    }

    /** 清空表单回到可填写状态（成功态点「再写一条」） */
    fun resetForNext() {
        title = ""
        content = ""
        email = ""
        emailError = false
        submittedId = null
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

@Composable
fun rememberFeedbackFormState(): FeedbackFormState = remember { FeedbackFormState() }

@Composable
fun FeedbackScreen(
    state: FeedbackFormState,
    scrollBehavior: SharedScrollBehavior? = null,
    onOpenHistory: () -> Unit = {},
    onOpenRoadmap: () -> Unit = {},
    /** 顶部被固定层的分段控件占掉的高度（控件在 overlay 里，内容要绕开它） */
    topExtra: Dp = 0.dp,
) {
    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val hapticFeedback = LocalHapticFeedback.current

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
                        pageTopExtra(isTablet, hasSmallTitle = false) +
                        topExtra,
                    bottom = 132.dp,
                ),
        ) {
            if (state.submittedId != null) {
                SubmittedCard(
                    requestId = state.submittedId.orEmpty(),
                    onWriteAnother = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        state.resetForNext()
                    },
                    onOpenHistory = onOpenHistory,
                    onOpenRoadmap = onOpenRoadmap,
                )
            } else {
                FeedbackForm(state = state, onOpenHistory = onOpenHistory)
            }
        }
    }
}

@Composable
private fun FeedbackForm(
    state: FeedbackFormState,
    onOpenHistory: () -> Unit,
) {
    val theme = MiuixTheme.colorScheme

    NativeMiuixTextField(
        value = state.title,
        onValueChange = { state.title = it },
        label = FeedbackStr.titleLabel(),
        useLabelAsPlaceholder = true,
        singleLine = true,
        maxLength = FeedbackClient.MAX_TITLE_LENGTH,
    )
    Spacer(modifier = Modifier.height(10.dp))
    NativeMiuixTextField(
        value = state.content,
        onValueChange = { state.content = it },
        // 占位符直接给填写结构（3 条），比「具体内容」四个字有用；开始输入即隐藏
        label = FeedbackStr.contentPlaceholder(),
        useLabelAsPlaceholder = true,
        minLines = 6,
        maxLines = 10,
        maxLength = FeedbackClient.MAX_CONTENT_LENGTH,
    )
    Text(
        text = FeedbackStr.contentCounter(state.content.length, FeedbackClient.MAX_CONTENT_LENGTH),
        style = MiuixTheme.textStyles.footnote2,
        color = theme.onSurfaceVariantSummary,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, top = 8.dp),
    )

    Spacer(modifier = Modifier.height(12.dp))
    NativeMiuixTextField(
        value = state.email,
        onValueChange = {
            state.email = it
            if (state.emailError) state.validateEmail()
        },
        label = if (state.emailError) FeedbackStr.emailInvalid() else FeedbackStr.emailLabel(),
        useLabelAsPlaceholder = true,
        singleLine = true,
        maxLength = FeedbackClient.MAX_EMAIL_LENGTH,
    )

    // 不提交也能看历史：入口常驻，不必先写一条
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = FeedbackStr.viewMine() + " ›",
            style = MiuixTheme.textStyles.footnote1,
            color = theme.primary,
            modifier = Modifier
                .clickable { onOpenHistory() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = deviceSummary(),
        style = MiuixTheme.textStyles.footnote2,
        color = theme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun deviceSummary(): String {
    val info = remember { currentDeviceInfo() }
    // 服务端只存 osVersion 原值，展示时补上平台前缀（Android 的 osVersion 是纯版本号）
    val os = if (info.sdkLevel > 0) "Android " + info.osVersion else info.osVersion
    return listOf("v" + AppInfo.version, os, info.model)
        .filter { it.isNotBlank() && it != "vunknown" }
        .joinToString(" · ")
}

@Composable
private fun SubmittedCard(
    requestId: String,
    onWriteAnother: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRoadmap: () -> Unit,
) {
    val theme = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 圆形勾：不依赖图标库，深浅色都成立
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(theme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.primary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = FeedbackStr.successTitle(),
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Medium,
                color = theme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = FeedbackStr.successHint(),
                style = MiuixTheme.textStyles.body2,
                color = theme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = FeedbackStr.successBody(requestId),
                style = MiuixTheme.textStyles.footnote2,
                color = theme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = FeedbackStr.viewMine(),
                    onClick = onOpenHistory,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = FeedbackStr.viewRoadmap(),
                    onClick = onOpenRoadmap,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                text = FeedbackStr.writeAnother(),
                onClick = onWriteAnother,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
