package com.haooz.chedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 56.dp,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isAppDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFFFFFF).copy(0.76f)
        else Color(0xFF181818).copy(0.84f)
    val selectorColor =
        if (isLightTheme) Color.Black.copy(alpha = 0.08f)
        else Color.White.copy(alpha = 0.12f)
    val defaultEdgeLight = rememberDefaultEdgeLight()

    val animationScope = rememberCoroutineScope()
    var currentIndex by remember(selectedTabIndex) {
        mutableIntStateOf(selectedTabIndex())
    }
    LaunchedEffect(selectedTabIndex) {
        snapshotFlow { selectedTabIndex() }
            .collect { index -> currentIndex = index }
    }

    val selectorOffset = remember { Animatable(0f) }
    LaunchedEffect(currentIndex) {
        animationScope.launch {
            selectorOffset.animateTo(
                targetValue = currentIndex.toFloat(),
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
            )
        }
    }

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 14f.dp.toPx()) / tabsCount
        }

        Row(
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(10f.dp.toPx(), 32f.dp.toPx())
                    },
                    highlight = null,
                    onDrawSurface = {
                        drawRect(containerColor)
                        val selectorExtra = 3f.dp.toPx()
                        val selectorW = tabWidth + selectorExtra * 2
                        val selectorX = selectorOffset.value * tabWidth + 7f.dp.toPx() - selectorExtra
                        val selectorH = size.height - 7f.dp.toPx()
                        val outline = Capsule().createOutline(Size(selectorW, selectorH), layoutDirection, this)
                        val path = Path().apply {
                            when (outline) {
                                is Outline.Generic -> addPath(outline.path)
                                is Outline.Rounded -> addRoundRect(outline.roundRect)
                                is Outline.Rectangle -> addRect(outline.rect)
                            }
                        }
                        path.transform(Matrix().apply { translate(selectorX, 3.5f.dp.toPx()) })
                        drawPath(path, selectorColor)
                        val maskColor = if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f)
                        drawRect(maskColor, alpha = interactiveHighlight.pressProgress)
                    }
                )
                .edgeLight(shape = Capsule(), edgeLight = defaultEdgeLight)
                .then(interactiveHighlight.modifier)
                .height(containerHeight)
                .fillMaxWidth()
                .padding(horizontal = 7f.dp, vertical = 3.5f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
