package com.haooz.chedule.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.McpStr
import com.haooz.chedule.ui.components.text.material.Text
import kotlinx.coroutines.delay
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.theme.NexioTheme

/**
 * 带隐私掩码的设置字段：布局与 AI 页的行内输入行、服务器地址卡同一家族——
 * 标签行 17sp/Medium、胶囊操作按钮靠右，输入行无边框、右对齐。
 *
 * 显示状态只存在当前页面内；复制是用户主动操作，隐藏时仍使用真实密码输入语义。
 */
@Composable
fun PrivateSettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    enabled: Boolean = true,
    isAddress: Boolean = false,
    onCopy: ((String) -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val theme = NexioTheme.colorScheme
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused, enabled) {
        if (!windowFocused || !enabled) {
            visible = false
            copied = false
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            delay(30_000)
            visible = false
        }
    }
    LaunchedEffect(copied) {
        if (copied) { delay(1800); copied = false }
    }
    // 空值禁用时也用与可用态相同的 6% 透明底：默认 disabledColor 是实心灰，
    // 深色模式下会和同行可用的「复制」按钮形成明显色块差异。
    val dimBackground = if (theme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = theme.onSurface,
            )
            FlatTextButton(
                text = if (visible) McpStr.hide() else McpStr.show(),
                onClick = { visible = !visible }, enabled = enabled && value.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(disabledColor = dimBackground),
                minWidth = 64.dp, minHeight = 36.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                cornerRadius = 100.dp,
            )
            if (onCopy != null) {
                Spacer(modifier = Modifier.width(8.dp))
                FlatTextButton(
                    text = if (copied) McpStr.copied() else McpStr.copy(),
                    onClick = { onCopy(value); copied = true },
                    enabled = enabled && value.isNotEmpty(),
                    colors = ButtonDefaults.textButtonColors(disabledColor = dimBackground),
                    minWidth = 64.dp, minHeight = 36.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    cornerRadius = 100.dp,
                )
            }
        }
        // NativeTextField 没有掩码参数，改用 BasicTextField + PasswordVisualTransformation
        // 保住默认掩码；其余样式（无边框、17sp/Medium、右对齐）对齐行内输入规范。
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= 4096 && '\n' !in it && '\r' !in it) onValueChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)
                .onFocusChanged {
                    if (hadFocus && !it.isFocused) visible = false
                    hadFocus = it.isFocused
                },
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = theme.onSurface,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(theme.primary),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isAddress) KeyboardType.Uri else KeyboardType.Password,
                autoCorrectEnabled = false,
            ),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text(
                        hint,
                        fontSize = 14.sp,
                        color = theme.onSurfaceVariantSummary,
                        textAlign = TextAlign.End,
                    )
                    inner()
                }
            },
        )
        if (copied) Text(
            McpStr.clipboardWarning(), fontSize = 12.sp,
            color = theme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}
