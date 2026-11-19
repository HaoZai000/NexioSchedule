package com.haooz.chedule.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.appearance.CardEdgeThickness
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.i18n.SchedStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.nexioui.nativetextfield.NativeTextField
import com.haooz.nexioui.slider.Slider
import com.haooz.nexioui.slider.SliderHapticEffect
import com.haooz.nexioui.theme.NexioTheme
import kotlin.math.roundToInt

/**
 * 数值滑杆条目：标题 + 右侧可编辑数值 + 滑杆。
 *
 * [quantize] 只作用于对外提交的值，滑块位置由内部原始浮点值连续驱动，
 * 否则拖动会在整档之间跳动。
 */
@Composable
internal fun SliderItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    keyPoints: List<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    quantize: (Float) -> Float = { it },
    suffix: String = "",
    displayValue: (Float) -> String = { it.roundToInt().toString() },
    parseInput: (String) -> Float? = { it.toFloatOrNull() },
    disabledText: String = SchedStr.wallpaperRequired(),
) {
    var textInput by remember { mutableStateOf(displayValue(value)) }
    var isInputFocused by remember { mutableStateOf(false) }
    var rawValue by remember { mutableFloatStateOf(value) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isInputFocused) textInput = displayValue(value)
        // 拖动中禁止回写，避免 thumb 被拉回整档
        if (!isDragging) rawValue = value
    }

    val textColor = if (enabled) NexioTheme.colorScheme.onSurface
    else NexioTheme.colorScheme.onSurface.copy(alpha = 0.4f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp, start = 14.dp, end = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            if (enabled) {
                NativeTextField(
                    value = textInput,
                    onValueChange = { input ->
                        textInput = input
                        parseInput(input)?.let { onValueChange(quantize(it)) }
                    },
                    modifier = Modifier
                        .width(56.dp)
                        .onFocusChanged { focus ->
                            isInputFocused = focus.isFocused
                            // 失焦回到规范化显示，清掉未完成/非法的输入
                            if (!focus.isFocused) textInput = displayValue(value)
                        },
                    singleLine = true,
                    textAlign = TextAlign.End,
                    textStyle = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                if (suffix.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = suffix,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        color = textColor,
                        textAlign = TextAlign.End,
                    )
                }
            } else {
                Text(text = disabledText, fontSize = 14.sp, color = textColor)
            }
        }
        Slider(
            value = rawValue,
            onValueChange = { raw ->
                isDragging = true
                rawValue = raw
                val quantized = quantize(raw)
                // 数值框同步跟手，不等外部 value 的异步回写
                textInput = displayValue(quantized)
                onValueChange(quantized)
            },
            onValueChangeFinished = {
                isDragging = false
                rawValue = quantize(rawValue)
            },
            valueRange = valueRange,
            showKeyPoints = true,
            keyPoints = keyPoints,
            magnetThreshold = 0.05f,
            modifier = Modifier.fillMaxWidth(),
            hapticEffect = SliderHapticEffect.Step,
            enabled = enabled
        )
    }
}

/** 枚举档位滑杆：折射 / 边缘厚度共用，档位文本由 [levelLabel] 提供。 */
@Composable
private fun <T> LevelSliderItem(
    label: String,
    levels: List<T>,
    selected: T,
    enabled: Boolean,
    levelLabel: (T) -> String,
    disabledText: String,
    onValueChange: (T) -> Unit,
) {
    val lastIndex = levels.lastIndex
    val keyPoints = remember(levels) { levels.indices.map { it.toFloat() } }
    val selectedIndex = levels.indexOf(selected).coerceIn(0, lastIndex)
    var rawValue by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (!isDragging) rawValue = selectedIndex.toFloat()
    }

    val textColor = if (enabled) NexioTheme.colorScheme.onSurface
    else NexioTheme.colorScheme.onSurface.copy(alpha = 0.4f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            if (enabled) {
                Text(
                    text = levelLabel(selected),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.End,
                )
            } else {
                Text(text = disabledText, fontSize = 14.sp, color = textColor)
            }
        }
        Slider(
            value = rawValue,
            onValueChange = { raw ->
                isDragging = true
                rawValue = raw
                onValueChange(levels[raw.roundToInt().coerceIn(0, lastIndex)])
            },
            onValueChangeFinished = {
                isDragging = false
                rawValue = rawValue.roundToInt().coerceIn(0, lastIndex).toFloat()
            },
            valueRange = 0f..lastIndex.toFloat(),
            showKeyPoints = true,
            keyPoints = keyPoints,
            magnetThreshold = 0.01f,
            // 档位即全部可选值：甩动直接落到最近档位，不等惯性结束
            settleToKeyPoints = true,
            modifier = Modifier.fillMaxWidth(),
            hapticEffect = SliderHapticEffect.Step,
            enabled = enabled
        )
    }
}

/** 卡片折射档位。 */
@Composable
internal fun RefractionItem(
    value: CardRefractionLevel,
    enabled: Boolean,
    onValueChange: (CardRefractionLevel) -> Unit,
    disabledText: String = SchedStr.wallpaperRequired(),
) {
    LevelSliderItem(
        label = SchedStr.cardRefraction(),
        levels = CardRefractionLevel.entries,
        selected = value,
        enabled = enabled,
        levelLabel = { it.label },
        disabledText = disabledText,
        onValueChange = onValueChange,
    )
}

/** 玻璃边缘厚度档位（乘在默认 edgeWidth 上，不改默认值）。 */
@Composable
internal fun EdgeThicknessItem(
    value: CardEdgeThickness,
    enabled: Boolean,
    onValueChange: (CardEdgeThickness) -> Unit,
    disabledText: String = SchedStr.wallpaperRequired(),
) {
    LevelSliderItem(
        label = SchedStr.cardEdgeThickness(),
        levels = CardEdgeThickness.entries,
        selected = value,
        enabled = enabled,
        levelLabel = { it.label },
        disabledText = disabledText,
        onValueChange = onValueChange,
    )
}
