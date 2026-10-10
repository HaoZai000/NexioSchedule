/** [NativeTextField] 的 skiko actual（JVM 桌面 / iOS / wasmJs）：BasicTextField 兜底。 */
package top.yukonga.miuix.kmp.basic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 与 Android actual 的视觉对齐点：主题色光标与选中高亮、hint 颜色、
 * 字重默认 Medium、`textStyle.color` 未指定时回退 `onSurface`、IME Done。
 * 差异（平台固有，无法对齐）：没有系统长按菜单与原生选择手柄。
 */
@Composable
actual fun NativeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    hint: String,
    textStyle: TextStyle,
    singleLine: Boolean,
    maxLines: Int,
    maxLength: Int,
    textAlign: TextAlign,
    enabled: Boolean,
) {
    val themeColor = MiuixTheme.colorScheme.primary
    val textColor = textStyle.color.takeIf { it.isSpecified }
        ?: MiuixTheme.colorScheme.onSurface
    val hintColor = MiuixTheme.colorScheme.onSurfaceVariantActions

    BasicTextField(
        value = value,
        onValueChange = { input ->
            // Android 侧靠 EditText 的 InputFilter.LengthFilter 截断，这里等价：输入侧截断
            onValueChange(if (input.length > maxLength) input.substring(0, maxLength) else input)
        },
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle.copy(
            color = textColor,
            // Android 侧按 textStyle.fontWeight（缺省 Medium）设 EditText typeface
            fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
            textAlign = textAlign,
        ),
        cursorBrush = SolidColor(themeColor),
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { innerTextField ->
            // hint 与输入区同位叠放，等价 EditText 的内部 hint（有内容即隐藏）
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && hint.isNotEmpty()) {
                    Text(
                        text = hint,
                        style = textStyle.copy(
                            color = hintColor,
                            fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
                            textAlign = textAlign,
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}
