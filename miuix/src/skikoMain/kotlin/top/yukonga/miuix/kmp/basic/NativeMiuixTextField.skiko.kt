/** [NativeMiuixTextField] 的 skiko actual（JVM 桌面 / iOS / wasmJs）：BasicTextField 实现。 */
package top.yukonga.miuix.kmp.basic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LabelAnimState { Hidden, Placeholder, Normal, Floating }

@Composable
actual fun NativeMiuixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    insideMargin: DpSize,
    cornerRadius: Dp,
    label: String,
    useLabelAsPlaceholder: Boolean,
    enabled: Boolean,
    readOnly: Boolean,
    textStyle: TextStyle,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    singleLine: Boolean,
    maxLines: Int,
    minLines: Int,
    requestFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val isFocused = remember { mutableStateOf(false) }
    val lastRequestFocus = remember { mutableStateOf(requestFocus) }

    // 与 Android actual 同语义：true → 聚焦（非 Android 平台由输入法自行随焦点弹出）；
    // 由 true 变 false 视为弹窗正在关闭 → 清焦点。
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
        } else if (lastRequestFocus.value) {
            focusManager.clearFocus()
        }
        lastRequestFocus.value = requestFocus
    }
    // 组件被移除时（弹窗关闭）清除焦点
    DisposableEffect(Unit) {
        onDispose { focusManager.clearFocus() }
    }

    val themeColor = MiuixTheme.colorScheme.primary
    val textColor = textStyle.color.takeIf { it.isSpecified }
        ?: MiuixTheme.colorScheme.onSurface
    val hintColor = MiuixTheme.colorScheme.onSurfaceVariantActions

    MiuixTextFieldShell(
        text = value,
        modifier = modifier,
        insideMargin = insideMargin,
        cornerRadius = cornerRadius,
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        isFocused = isFocused.value,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused.value = it.isFocused },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(
                color = textColor,
                fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
            ),
            cursorBrush = SolidColor(themeColor),
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            decorationBox = { innerTextField ->
                // useLabelAsPlaceholder 时 label 兼作 hint（有内容即隐藏）——
                // 与 Android actual 的 EditText.hint 条件一致
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && useLabelAsPlaceholder && label.isNotEmpty()) {
                        Text(
                            text = label,
                            style = textStyle.copy(
                                color = hintColor,
                                fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
actual fun NativeMiuixTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    insideMargin: DpSize,
    cornerRadius: Dp,
    label: String,
    useLabelAsPlaceholder: Boolean,
    enabled: Boolean,
    readOnly: Boolean,
    textStyle: TextStyle,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    singleLine: Boolean,
    maxLines: Int,
    minLines: Int,
    requestFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val isFocused = remember { mutableStateOf(false) }
    val lastRequestFocus = remember { mutableStateOf(requestFocus) }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
        } else if (lastRequestFocus.value) {
            focusManager.clearFocus()
        }
        lastRequestFocus.value = requestFocus
    }
    DisposableEffect(Unit) {
        onDispose { focusManager.clearFocus() }
    }

    val themeColor = MiuixTheme.colorScheme.primary
    val textColor = textStyle.color.takeIf { it.isSpecified }
        ?: MiuixTheme.colorScheme.onSurface
    val hintColor = MiuixTheme.colorScheme.onSurfaceVariantActions

    MiuixTextFieldShell(
        text = value.text,
        modifier = modifier,
        insideMargin = insideMargin,
        cornerRadius = cornerRadius,
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        isFocused = isFocused.value,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused.value = it.isFocused },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(
                color = textColor,
                fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
            ),
            cursorBrush = SolidColor(themeColor),
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty() && useLabelAsPlaceholder && label.isNotEmpty()) {
                        Text(
                            text = label,
                            style = textStyle.copy(
                                color = hintColor,
                                fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * 共享外壳：超椭圆背景 + 聚焦主色描边（0→2dp 动画）+ 浮动 label + 前后图标。
 * 逻辑逐段对照 Android actual 的 shell（label 状态机、动画值、布局结构完全一致），
 * 只有输入区（[content]）由各重载提供。
 */
@Composable
private fun MiuixTextFieldShell(
    text: String,
    modifier: Modifier,
    insideMargin: DpSize,
    cornerRadius: Dp,
    label: String,
    useLabelAsPlaceholder: Boolean,
    isFocused: Boolean,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val themeColor = MiuixTheme.colorScheme.primary
    val backgroundColor = MiuixTheme.colorScheme.secondaryContainer
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantActions

    val labelState = remember(text, label, useLabelAsPlaceholder) {
        when {
            label.isEmpty() -> LabelAnimState.Hidden
            useLabelAsPlaceholder && text.isNotEmpty() -> LabelAnimState.Placeholder
            text.isNotEmpty() -> LabelAnimState.Floating
            else -> LabelAnimState.Normal
        }
    }

    val borderColor by animateColorAsState(if (isFocused) themeColor else Color.Transparent)
    val borderWidth by animateDpAsState(if (isFocused) 2.dp else 0.dp)

    val labelAnim by animateDpAsState(
        when (labelState) {
            LabelAnimState.Floating -> -insideMargin.height / 2
            LabelAnimState.Placeholder, LabelAnimState.Normal -> 0.dp
            LabelAnimState.Hidden -> 0.dp
        },
    )
    val labelFontSize by animateDpAsState(
        when (labelState) {
            LabelAnimState.Floating -> 10.dp
            else -> 17.dp
        },
    )

    Box(
        modifier = modifier
            .squircleBackground(color = backgroundColor, cornerRadius = cornerRadius)
            .squircleBorder(width = borderWidth, color = borderColor, cornerRadius = cornerRadius),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = insideMargin.width,
                        end = insideMargin.width,
                        top = insideMargin.height,
                        bottom = insideMargin.height,
                    ),
                contentAlignment = Alignment.TopStart,
            ) {
                if (labelState == LabelAnimState.Floating ||
                    (labelState == LabelAnimState.Normal && !useLabelAsPlaceholder)
                ) {
                    Text(
                        text = label,
                        fontSize = labelFontSize.value.sp,
                        color = labelColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.offset { IntOffset(0, labelAnim.roundToPx()) },
                        textAlign = TextAlign.Start,
                    )
                }
                Box(
                    modifier = Modifier.offset(
                        y = if (labelState == LabelAnimState.Floating) insideMargin.height / 2 else 0.dp,
                    ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    content()
                }
            }
            trailingIcon?.invoke()
        }
    }
}
