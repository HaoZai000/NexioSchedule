package top.yukonga.miuix.kmp.basic

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * 原生输入框（跨平台入口）。
 *
 * Android actual 用 `AndroidView(EditText)` 包装 —— 使用系统原生长按菜单、
 * 原生光标/选择手柄着色，**Android 行为零变化**（这是当初选它的理由，别改）。
 *
 * skiko actual（JVM 桌面 / iOS / wasmJs）是 `BasicTextField` 兜底实现：
 * 视觉对齐（主题色光标、hint、字重、对齐），但没有系统原生菜单 ——
 * 平台上本来就没有，兜底的目的是**让调用方代码能进 commonMain**。
 *
 * @param value 文本内容
 * @param onValueChange 内容变化回调
 * @param hint 空内容时的提示文字
 * @param textStyle 文字样式；`color` 未指定时回退到 `MiuixTheme.colorScheme.onSurface`
 * @param singleLine 单行
 * @param maxLines 最大行数
 * @param maxLength 最大长度（skiko 实现按输入截断；Android 走 EditText 的 InputFilter）
 * @param textAlign 文本对齐
 * @param enabled 是否可用
 */
@Composable
expect fun NativeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    textStyle: TextStyle = TextStyle.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Start,
    enabled: Boolean = true,
)
