// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.scroll.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A [NavigationRail] that is suitable for wide screens.
 *
 * When [state] is `null`, the rail renders in a classic fixed layout (icon + label)
 * and no toggle button is shown.
 * When [state] is provided via [rememberNavigationRailState], the rail becomes
 * expandable: clicking the built-in toggle animates between the compact rail
 * and the [expandedWidth] expanded rail with pill-shaped selected indicators.
 *
 * @param modifier The modifier to be applied to the [NavigationRail].
 * @param state The expand/collapse state. Pass `null` for a classic fixed rail.
 * @param header The header of the [NavigationRail], usually a [FloatingActionButton] or a logo.
 * @param color The color of the [NavigationRail].
 * @param showDivider Whether to show the divider line between the [NavigationRail] and the content.
 * @param defaultWindowInsetsPadding Whether to apply default window insets padding to the [NavigationRail].
 * @param minWidth The minimum width of the [NavigationRail] when collapsed.
 * @param expandedWidth The width of the [NavigationRail] when expanded.
 * @param expandContentDescription Accessibility description for the expand toggle button.
 * @param collapseContentDescription Accessibility description for the collapse toggle button.
 * @param scrollState The scroll state for the content column.
 * @param content The content of the [NavigationRail], usually [NavigationRailItem]s.
 */
@Composable
fun NavigationRail(
    modifier: Modifier = Modifier,
    state: NavigationRailState? = null,
    header: @Composable (ColumnScope.() -> Unit)? = null,
    color: Color = MiuixTheme.colorScheme.surface,
    showDivider: Boolean = true,
    defaultWindowInsetsPadding: Boolean = true,
    minWidth: Dp = NavigationRailDefaults.MinWidth,
    expandedWidth: Dp = NavigationRailDefaults.ExpandedWidth,
    expandContentDescription: String = NavigationRailDefaults.ExpandContentDescription,
    collapseContentDescription: String = NavigationRailDefaults.CollapseContentDescription,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isExpandable = state != null
    val targetWidth by animateDpAsState(
        targetValue = if (isExpandable && state!!.isExpanded) expandedWidth else minWidth,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "railWidth",
    )

    Row(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (defaultWindowInsetsPadding) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Vertical))
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Start))
                } else {
                    Modifier
                },
            )
            .background(color),
    ) {
        Column(
            modifier = Modifier
                .width(targetWidth)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .selectableGroup()
                .padding(vertical = NavigationRailDefaults.VerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            if (isExpandable) {
                Box(
                    modifier = Modifier
                        .size(NavigationRailDefaults.ToggleButtonSize)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { state!!.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        modifier = Modifier.size(20.dp),
                        imageVector = if (state!!.isExpanded) {
                            NavigationRailDefaults.CollapseIcon
                        } else {
                            NavigationRailDefaults.ExpandIcon
                        },
                        contentDescription = if (state!!.isExpanded) collapseContentDescription else expandContentDescription,
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceContainer),
                    )
                }
                Spacer(modifier = Modifier.height(NavigationRailDefaults.HeaderSpacing))
            }
            if (header != null) {
                header()
                Spacer(modifier = Modifier.height(NavigationRailDefaults.HeaderSpacing))
            }
            content()
        }
        if (showDivider) {
            VerticalDivider()
        }
    }
}

/**
 * A [NavigationRailItem] that is suitable for [NavigationRail].
 *
 * In the classic layout (no expandable state), renders icon + label vertically.
 * In the expanded layout, renders icon + label horizontally with a pill-shaped
 * selection indicator when selected.
 *
 * @param selected Whether the item is selected.
 * @param onClick The callback when the item is clicked.
 * @param icon The icon of the item.
 * @param label The label of the item.
 * @param modifier The modifier to be applied to the [NavigationRailItem].
 * @param enabled Whether the item is enabled.
 * @param badge Optional badge composable displayed on the icon.
 */
@Composable
fun NavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val onSurfaceContainerColor = MiuixTheme.colorScheme.onSurfaceContainer
    val tint = when {
        isPressed -> if (selected) {
            onSurfaceContainerColor.copy(alpha = 0.7f)
        } else {
            onSurfaceContainerColor.copy(alpha = 0.8f)
        }

        selected -> onSurfaceContainerColor

        else -> onSurfaceContainerColor.copy(alpha = 0.6f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            )
            .padding(vertical = NavigationRailDefaults.ItemVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NavigationRailDefaults.ExpandedItemHorizontalMargin)
                    .clip(RoundedCornerShape(NavigationRailDefaults.ExpandedItemCornerRadius))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .padding(
                        horizontal = NavigationRailDefaults.ExpandedItemContentHorizontalPadding,
                        vertical = NavigationRailDefaults.ExpandedItemContentVerticalPadding,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NavigationRailDefaults.ExpandedItemIconTextSpacing),
                ) {
                    Image(
                        modifier = Modifier.size(NavigationRailDefaults.IconSize),
                        imageVector = icon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(tint),
                    )
                    Text(
                        text = label,
                        color = tint,
                        textAlign = TextAlign.Center,
                        fontSize = NavigationRailDefaults.ExpandedLabelFontSize,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(vertical = NavigationRailDefaults.CollapsedIndicatorVerticalPadding)
                    .size(NavigationRailDefaults.IconSize),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    modifier = Modifier.size(NavigationRailDefaults.IconSize),
                    imageVector = icon,
                    contentDescription = label,
                    colorFilter = ColorFilter.tint(tint),
                )
                if (badge != null) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        badge()
                    }
                }
            }
        }
    }
}

/** Contains default values used by [NavigationRail] and [NavigationRailItem]. */
object NavigationRailDefaults {
    /** The default minimum width of the [NavigationRail] when collapsed. */
    val MinWidth = 80.dp

    /** The default expanded width of the [NavigationRail]. */
    val ExpandedWidth = 240.dp

    /** The default vertical padding of the [NavigationRail] content. */
    val VerticalPadding = 24.dp

    /** The default spacing after the header. */
    val HeaderSpacing = 24.dp

    /** The default icon size. */
    val IconSize = 28.dp

    /** The default spacing between icon and text. */
    val IconTextSpacing = 4.dp

    /** The default vertical padding for each item. */
    val ItemVerticalPadding = 12.dp

    /** The default label font size. */
    val LabelFontSize = 12.sp

    /** The label font size when expanded. */
    val ExpandedLabelFontSize = 16.sp

    /** Horizontal margin for expanded items. */
    val ExpandedItemHorizontalMargin = 12.dp

    /** Corner radius for the selected pill indicator. */
    val ExpandedItemCornerRadius = 16.dp

    /** Vertical padding for collapsed item indicators. */
    val CollapsedIndicatorVerticalPadding = 4.dp

    /** Horizontal padding inside expanded items. */
    val ExpandedItemContentHorizontalPadding = 14.dp

    /** Vertical padding inside expanded items. */
    val ExpandedItemContentVerticalPadding = 14.dp

    /** Spacing between icon and text in expanded items. */
    val ExpandedItemIconTextSpacing = 16.dp

    /** The size of the expand/collapse toggle button. */
    val ToggleButtonSize = 36.dp

    /** Accessibility description when collapsed (for expand button). */
    val ExpandContentDescription = "Expand navigation rail"

    /** Accessibility description when expanded (for collapse button). */
    val CollapseContentDescription = "Collapse navigation rail"

    /** Icon for expanding the rail. */
    val ExpandIcon: ImageVector
        get() = MiuixIcons.Sidebar

    /** Icon for collapsing the rail. */
    val CollapseIcon: ImageVector
        get() = MiuixIcons.Close
}

/**
 * A state holder for [NavigationRail] expand/collapse behavior.
 *
 * Use [rememberNavigationRailState] to create and remember an instance.
 */
@Stable
class NavigationRailState(initialValue: NavigationRailValue = NavigationRailValue.Collapsed) {
    var currentValue by mutableStateOf(initialValue)
        internal set

    /** Whether the rail is currently expanded. */
    val isExpanded: Boolean
        get() = currentValue == NavigationRailValue.Expanded

    /** Expand the rail to show icon + label. */
    fun expand() {
        currentValue = NavigationRailValue.Expanded
    }

    /** Collapse the rail to show icons only. */
    fun collapse() {
        currentValue = NavigationRailValue.Collapsed
    }

    /** Toggle between expanded and collapsed states. */
    fun toggle() {
        currentValue = if (isExpanded) {
            NavigationRailValue.Collapsed
        } else {
            NavigationRailValue.Expanded
        }
    }
}

/**
 * The visual value of a [NavigationRailState].
 */
enum class NavigationRailValue {
    /** Collapsed: rail shows icons only at [NavigationRailDefaults.MinWidth]. */
    Collapsed,

    /** Expanded: rail shows icon + label at [NavigationRailDefaults.ExpandedWidth]. */
    Expanded,
}

/**
 * Creates and remembers a [NavigationRailState].
 *
 * @param initialValue The initial expanded/collapsed state.
 */
@Composable
fun rememberNavigationRailState(initialValue: NavigationRailValue = NavigationRailValue.Collapsed): NavigationRailState {
    return remember { NavigationRailState(initialValue) }
}
