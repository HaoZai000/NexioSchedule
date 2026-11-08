// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import com.haooz.softglass.shape.continuousSurface
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A Miuix-styled [Button]. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
    content: @Composable RowScope.() -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val rowModifier = remember(minWidth, minHeight, insideMargin) {
        Modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .padding(insideMargin)
    }
    val containerColor = if (enabled) colors.color else colors.disabledColor
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .semantics { role = Role.Button }
                .continuousSurface(color = containerColor, cornerRadius = cornerRadius)
                .clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    enabled = enabled,
                    onClick = onClick,
                ),
            propagateMinConstraints = true,
        ) {
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** A Miuix-styled [TextButton] with a text label. */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    textStyle: TextStyle? = null,
    textColor: Color? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val bgColor = colors.color.copy(alpha = colors.alpha)
    val disabledBgColor = colors.disabledColor
    val containerColor = if (enabled) bgColor else disabledBgColor
    val contentColor = if (enabled) textColor ?: colors.textColor else colors.disabledTextColor
    val rowModifier = remember(minWidth, minHeight, insideMargin) {
        Modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .padding(insideMargin)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .semantics { role = Role.Button }
                .background(color = containerColor, shape = ContinuousRoundedRectangle(cornerRadius))
                .clip(ContinuousRoundedRectangle(cornerRadius))
                .clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    enabled = enabled,
                    onClick = onClick,
                ),
            propagateMinConstraints = true,
        ) {
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    style = (textStyle ?: MiuixTheme.textStyles.button).copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

object ButtonDefaults {

    /** Default min width for buttons (override via Modifier.widthIn). */
    val MinWidth = 58.dp

    /** Default min height for buttons (override via Modifier.heightIn). */
    val MinHeight = 40.dp

    /** Default corner radius for buttons. */
    val CornerRadius = 16.dp

    /** Default inside margin for buttons. */
    val InsideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp)

    /** Default [ButtonColors] for all buttons. */
    @Composable
    fun buttonColors(
        color: Color = MiuixTheme.colorScheme.secondaryVariant,
        disabledColor: Color = MiuixTheme.colorScheme.disabledSecondaryVariant,
        contentColor: Color = MiuixTheme.colorScheme.onSecondaryVariant,
        disabledContentColor: Color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
    ): ButtonColors = remember(color, disabledColor, contentColor, disabledContentColor) {
        ButtonColors(
            color = color,
            disabledColor = disabledColor,
            contentColor = contentColor,
            disabledContentColor = disabledContentColor,
        )
    }

    /** [ButtonColors] for primary buttons. */
    @Composable
    fun buttonColorsPrimary(
        color: Color = MiuixTheme.colorScheme.primary,
        disabledColor: Color = MiuixTheme.colorScheme.disabledPrimaryButton,
        contentColor: Color = MiuixTheme.colorScheme.onPrimary,
        disabledContentColor: Color = MiuixTheme.colorScheme.disabledOnPrimaryButton,
    ): ButtonColors = remember(color, disabledColor, contentColor, disabledContentColor) {
        ButtonColors(
            color = color,
            disabledColor = disabledColor,
            contentColor = contentColor,
            disabledContentColor = disabledContentColor,
        )
    }

    /** Default [TextButtonColors] for all text buttons. */
    @Composable
    fun textButtonColors(
        // 用 MiuixTheme.colorScheme 自身的亮暗（而非 App 的 isAppDarkTheme），
        // 否则 App 用 LocalForcedDarkTheme 覆盖主题时，按钮文字色会与背景对不上。
        color: Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black,
        disabledColor: Color = MiuixTheme.colorScheme.disabledSecondaryVariant,
        textColor: Color = MiuixTheme.colorScheme.onSecondaryVariant,
        disabledTextColor: Color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
        alpha: Float = 0.06f,
    ): TextButtonColors = remember(color, disabledColor, textColor, disabledTextColor, alpha) {
        TextButtonColors(
            color = color,
            disabledColor = disabledColor,
            textColor = textColor,
            disabledTextColor = disabledTextColor,
            alpha = alpha,
        )
    }

    /** [TextButtonColors] for primary text buttons. */
    @Composable
    fun textButtonColorsPrimary(
        color: Color = MiuixTheme.colorScheme.primary,
        disabledColor: Color = MiuixTheme.colorScheme.disabledPrimaryButton,
        textColor: Color = MiuixTheme.colorScheme.onPrimary,
        disabledTextColor: Color = MiuixTheme.colorScheme.disabledOnPrimaryButton,
        alpha: Float = 0.84f,
    ): TextButtonColors = remember(color, disabledColor, textColor, disabledTextColor, alpha) {
        TextButtonColors(
            color = color,
            disabledColor = disabledColor,
            textColor = textColor,
            disabledTextColor = disabledTextColor,
            alpha = alpha,
        )
    }
}

@Immutable
data class ButtonColors(
    val color: Color,
    val disabledColor: Color,
    val contentColor: Color,
    val disabledContentColor: Color,
)

@Immutable
data class TextButtonColors(
    val color: Color,
    val disabledColor: Color,
    val textColor: Color,
    val disabledTextColor: Color,
    val alpha: Float = 1f,
)
