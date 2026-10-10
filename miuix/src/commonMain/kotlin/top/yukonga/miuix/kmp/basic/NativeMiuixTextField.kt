package top.yukonga.miuix.kmp.basic

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 仿 Miuix 样式的输入框（跨平台入口）：浮动 label + 超椭圆圆角 + 聚焦主色描边。
 *
 * Android actual 用**原生 EditText**（系统原生选择控件、光标着色、InputMethodManager
 * 弹/收键盘），`requestFocus` 语义含「延迟 150ms 弹键盘」与「关闭时收键盘」——
 * **Android 行为零变化**（本组件是 16 个页面的输入入口，别动 Android 路径）。
 *
 * skiko actual（JVM 桌面 / iOS / wasmJs）是 `BasicTextField` 实现：
 * 外壳（label 浮动动画、squircle 背景、聚焦描边、前后图标）与 Android 完全同一套
 * 公共代码逻辑，输入区换成 CMP 文本框；键盘由平台输入法自行管理。
 *
 * @param value 文本内容
 * @param onValueChange 内容变化回调
 * @param insideMargin 内边距（同时决定 label 浮动位移基准）
 * @param cornerRadius 超椭圆圆角
 * @param label 标签；空串不显示
 * @param useLabelAsPlaceholder 空内容时 label 作为占位符展示
 * @param enabled 是否可交互
 * @param readOnly 只读（不可聚焦输入）
 * @param textStyle 文字样式
 * @param leadingIcon 前置图标
 * @param trailingIcon 后置图标
 * @param singleLine 单行
 * @param maxLines 最大行数
 * @param minLines 最小行数
 * @param requestFocus 进入即请求焦点（弹窗打开时置 true 自动聚焦并弹键盘）
 */
@Composable
expect fun NativeMiuixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    cornerRadius: Dp = 20.dp,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MiuixTheme.textStyles.main,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    requestFocus: Boolean = false,
)

/**
 * [NativeMiuixTextField] 的 `TextFieldValue` 重载 —— 用于需要访问光标位置等场景。
 */
@Composable
expect fun NativeMiuixTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    cornerRadius: Dp = 20.dp,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MiuixTheme.textStyles.main,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    requestFocus: Boolean = false,
)
