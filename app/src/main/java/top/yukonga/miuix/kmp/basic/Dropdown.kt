// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@NonRestartableComposable
fun RowScope.DropdownArrowEndAction(
    actionColor: Color,
    modifier: Modifier = Modifier,
) {
    val colorFilter = remember(actionColor) { ColorFilter.tint(actionColor) }
    Image(
        modifier = modifier
            .size(width = DropdownDefaults.ArrowSize.width, height = DropdownDefaults.ArrowSize.height)
            .align(Alignment.CenterVertically),
        imageVector = MiuixIcons.Basic.ArrowUpDown,
        colorFilter = colorFilter,
        contentDescription = null,
    )
}

/**
 * The implementation of the dropdown.
 *
 * 本地覆盖版本：条目使用圆角矩形裁剪 + clickable 点击反馈，外层加 8dp 内边距，
 * 首行顶部/末行底部额外 8dp。
 */
@Composable
fun DropdownImpl(
    item: DropdownItem,
    optionSize: Int,
    isSelected: Boolean,
    index: Int,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    enabled: Boolean = item.enabled,
    dialogMode: Boolean = false,
    hasSubmenu: Boolean = false,
    isFirst: Boolean = index == 0,
    isLast: Boolean = index == optionSize - 1,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val backgroundColor = if (isSelected) {
        dropdownColors.selectedContainerColor
    } else {
        dropdownColors.containerColor
    }
    val backgroundColorState = rememberUpdatedState(backgroundColor)

    val checkColor = when {
        !isSelected -> Color.Transparent
        !enabled -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
        else -> dropdownColors.selectedIndicatorColor
    }

    val titleColor = when {
        !enabled -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
        isSelected -> dropdownColors.selectedContentColor
        else -> dropdownColors.contentColor
    }

    val summaryColor = when {
        !enabled -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
        isSelected -> dropdownColors.selectedSummaryColor
        else -> dropdownColors.summaryColor
    }

    val containerModifier = remember(dialogMode) {
        if (dialogMode) {
            Modifier
                .heightIn(min = DropdownDefaults.MinHeight)
                .widthIn(min = DropdownDefaults.MinWidth)
                .fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        }
    }
    val innerRowModifier = remember(dialogMode) {
        if (dialogMode) Modifier else Modifier.widthIn(max = DropdownDefaults.MaxItemTextWidth)
    }

    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    val role = if (hasSubmenu) Role.Button else Role.RadioButton
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = if (isFirst) 8.dp else 0.dp,
                bottom = if (isLast) 8.dp else 0.dp,
            )
            .clip(RoundedRectangle(17.dp))
            .drawBehind { drawRect(backgroundColorState.value) }
            .clickable(
                enabled = enabled,
                role = role,
                onClick = { currentOnSelectedIndexChange(index) },
            )
            .then(containerModifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = innerRowModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            item.icon?.let { it(IconCellModifier) }
            Column {
                Text(
                    text = item.text,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                item.summary?.let {
                    Text(
                        text = it,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = summaryColor,
                    )
                }
            }
        }

        if (hasSubmenu) {
            val chevronColor = when {
                !enabled -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
                isSelected -> dropdownColors.selectedContentColor
                else -> dropdownColors.summaryColor
            }
            val chevronColorFilter = remember(chevronColor) {
                BlendModeColorFilter(chevronColor, BlendMode.SrcIn)
            }
            Image(
                modifier = ChevronIconBaseModifier,
                imageVector = MiuixIcons.Basic.ArrowRight,
                colorFilter = chevronColorFilter,
                contentDescription = null,
            )
        } else {
            val checkColorFilter = remember(checkColor) {
                BlendModeColorFilter(checkColor, BlendMode.SrcIn)
            }
            Image(
                modifier = CheckIconBaseModifier,
                imageVector = MiuixIcons.Basic.Check,
                colorFilter = checkColorFilter,
                contentDescription = null,
            )
        }
    }
}

@Composable
@NonRestartableComposable
fun DropdownImpl(
    text: String,
    optionSize: Int,
    isSelected: Boolean,
    index: Int,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    enabled: Boolean = true,
    dialogMode: Boolean = false,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val item = remember(text, enabled) { DropdownItem(text = text, enabled = enabled) }
    DropdownImpl(
        item = item,
        optionSize = optionSize,
        isSelected = isSelected,
        index = index,
        dropdownColors = dropdownColors,
        enabled = enabled,
        dialogMode = dialogMode,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

@Immutable
data class DropdownColors(
    val contentColor: Color,
    val summaryColor: Color,
    val containerColor: Color,
    val selectedContentColor: Color,
    val selectedSummaryColor: Color,
    val selectedContainerColor: Color,
    val selectedIndicatorColor: Color,
)

@Stable
data class DropdownEntry(
    val items: List<DropdownItem>,
    val enabled: Boolean = true,
)

@Stable
data class DropdownItem(
    val text: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val icon: @Composable ((Modifier) -> Unit)? = null,
    val summary: String? = null,
    val children: List<DropdownItem>? = null,
) {
    constructor(
        icon: @Composable ((Modifier) -> Unit)? = null,
        title: String? = null,
        summary: String? = null,
    ) : this(
        text = title.orEmpty(),
        icon = icon,
        summary = summary,
    )
}

object DropdownDefaults {
    val MinHeight: Dp = 56.dp
    val MinWidth: Dp = 200.dp
    val CheckIconSize: Dp = 20.dp
    val ArrowSize: DpSize = DpSize(width = 10.dp, height = 16.dp)
    val ChevronSize: DpSize = DpSize(width = 10.dp, height = 16.dp)
    val IconMinSize: Dp = 26.dp
    val MaxItemTextWidth: Dp = 216.dp
    val InsideHorizontalPadding: Dp = 20.dp
    val DialogHorizontalPadding: Dp = 28.dp
    val FirstLastVerticalPadding: Dp = 20.dp
    val MiddleVerticalPadding: Dp = 12.dp
    val IconEndPadding: Dp = 12.dp
    val CheckIconStartPadding: Dp = 12.dp

    @Composable
    fun dropdownColors(
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        selectedContentColor: Color = MiuixTheme.colorScheme.primary,
        selectedSummaryColor: Color = MiuixTheme.colorScheme.primary,
        selectedContainerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        selectedIndicatorColor: Color = MiuixTheme.colorScheme.primary,
    ): DropdownColors = rememberDropdownColorsImpl(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
    )

    @Composable
    fun dialogDropdownColors(
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        containerColor: Color = Color.Transparent,
        selectedContentColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
        selectedSummaryColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
        selectedContainerColor: Color = MiuixTheme.colorScheme.tertiaryContainer,
        selectedIndicatorColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
    ): DropdownColors = rememberDropdownColorsImpl(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
    )
}

@Composable
private fun rememberDropdownColorsImpl(
    contentColor: Color,
    summaryColor: Color,
    containerColor: Color,
    selectedContentColor: Color,
    selectedSummaryColor: Color,
    selectedContainerColor: Color,
    selectedIndicatorColor: Color,
): DropdownColors = remember(
    contentColor,
    summaryColor,
    containerColor,
    selectedContentColor,
    selectedSummaryColor,
    selectedContainerColor,
    selectedIndicatorColor,
) {
    DropdownColors(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
    )
}

private val CheckIconBaseModifier = Modifier
    .padding(start = DropdownDefaults.CheckIconStartPadding)
    .size(DropdownDefaults.CheckIconSize)

private val ChevronIconBaseModifier = Modifier
    .padding(start = DropdownDefaults.CheckIconStartPadding)
    .size(width = DropdownDefaults.ChevronSize.width, height = DropdownDefaults.ChevronSize.height)

private val IconCellModifier = Modifier
    .sizeIn(minWidth = DropdownDefaults.IconMinSize, minHeight = DropdownDefaults.IconMinSize)
    .padding(end = DropdownDefaults.IconEndPadding)

@Deprecated(
    message = "Use DropdownDefaults instead.",
    replaceWith = ReplaceWith("DropdownDefaults"),
)
object SpinnerDefaults {
    @Composable
    fun spinnerColors(
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        selectedContentColor: Color = MiuixTheme.colorScheme.primary,
        selectedSummaryColor: Color = MiuixTheme.colorScheme.primary,
        selectedContainerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        selectedIndicatorColor: Color = MiuixTheme.colorScheme.primary,
    ): DropdownColors = DropdownDefaults.dropdownColors(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
    )

    @Composable
    fun dialogSpinnerColors(
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        containerColor: Color = Color.Transparent,
        selectedContentColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
        selectedSummaryColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
        selectedContainerColor: Color = MiuixTheme.colorScheme.tertiaryContainer,
        selectedIndicatorColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
    ): DropdownColors = DropdownDefaults.dialogDropdownColors(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
    )
}

@Deprecated(
    message = "Use DropdownColors instead.",
    replaceWith = ReplaceWith("DropdownColors"),
)
typealias SpinnerColors = DropdownColors

@Deprecated(
    message = "Use DropdownItem instead.",
    replaceWith = ReplaceWith("DropdownItem"),
)
typealias SpinnerEntry = DropdownItem
