// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.softglass.shape.ContinuousCapsule
import kotlin.math.ceil
import kotlin.math.min
import top.yukonga.miuix.kmp.color.api.toHsv
import top.yukonga.miuix.kmp.color.api.toOkLab
import top.yukonga.miuix.kmp.color.api.toOkLch
import top.yukonga.miuix.kmp.color.core.Transforms
import top.yukonga.miuix.kmp.color.space.Hsv
import top.yukonga.miuix.kmp.color.space.OkHsv
import top.yukonga.miuix.kmp.color.space.OkLab
import top.yukonga.miuix.kmp.color.space.OkLch

/** A [ColorPicker] supporting multiple color spaces. */
@Composable
fun ColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
    colorSpace: ColorSpace = ColorSpace.HSV,
) {
    val currentOnColorChanged by rememberUpdatedState(onColorChanged)
    when (colorSpace) {
        ColorSpace.OKHSV -> {
            OkHsvColorPicker(
                color = color,
                onColorChanged = currentOnColorChanged,
                showPreview = showPreview,
                hapticEffect = hapticEffect,
                modifier = modifier,
            )
        }

        ColorSpace.OKLAB -> {
            OkLabColorPicker(
                color = color,
                onColorChanged = currentOnColorChanged,
                showPreview = showPreview,
                hapticEffect = hapticEffect,
                modifier = modifier,
            )
        }

        ColorSpace.OKLCH -> {
            OkLchColorPicker(
                color = color,
                onColorChanged = currentOnColorChanged,
                showPreview = showPreview,
                hapticEffect = hapticEffect,
                modifier = modifier,
            )
        }

        else -> {
            HsvColorPicker(
                color = color,
                onColorChanged = currentOnColorChanged,
                showPreview = showPreview,
                hapticEffect = hapticEffect,
                modifier = modifier,
            )
        }
    }
}

/** HSV color picker. */
@Composable
fun HsvColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val currentOnColorChanged by rememberUpdatedState(onColorChanged)
    val capsuleShape = ContinuousCapsule()

    val hsv = remember { color.toHsv() }
    var currentHue by remember { mutableFloatStateOf(hsv.h) }
    var currentSaturation by remember { mutableFloatStateOf(hsv.s / 100f) }
    var currentValue by remember { mutableFloatStateOf(hsv.v / 100f) }
    var currentAlpha by remember { mutableFloatStateOf(color.alpha) }

    var lastAppliedExternalColorArgb by remember {
        mutableStateOf(color.toArgb())
    }
    val selectedColor by remember {
        derivedStateOf {
            Hsv(
                h = currentHue,
                s = currentSaturation * 100f,
                v = currentValue * 100f,
            ).toColor(currentAlpha)
        }
    }

    SideEffect {
        val externalArgb = color.toArgb()
        val internalArgb = selectedColor.toArgb()

        if (
            externalArgb != lastAppliedExternalColorArgb &&
            externalArgb != internalArgb
        ) {
            lastAppliedExternalColorArgb = externalArgb
            val hsv = color.toHsv()
            currentHue = hsv.h
            currentSaturation = hsv.s / 100f
            currentValue = hsv.v / 100f
            currentAlpha = color.alpha
        }
    }

    fun notifyUserColorChanged() {
        val newColor = selectedColor
        if (newColor.toArgb() != color.toArgb()) {
            currentOnColorChanged(newColor)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(capsuleShape)
                    .background(selectedColor),
            )
        }

        HsvHueSlider(
            currentHue = currentHue,
            onHueChanged = { newHue ->
                currentHue = newHue * 360f
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        HsvSaturationSlider(
            currentHue = currentHue,
            currentSaturation = currentSaturation,
            onSaturationChanged = {
                currentSaturation = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        HsvValueSlider(
            currentHue = currentHue,
            currentSaturation = currentSaturation,
            currentValue = currentValue,
            onValueChanged = {
                currentValue = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        HsvAlphaSlider(
            currentHue = currentHue,
            currentSaturation = currentSaturation,
            currentValue = currentValue,
            currentAlpha = currentAlpha,
            onAlphaChanged = {
                currentAlpha = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )
    }
}

/** Hue slider. `currentHue` is 0-360; `onHueChanged` returns normalized 0-1. */
@Composable
fun HsvHueSlider(
    currentHue: Float,
    onHueChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val hsvHueColors = remember {
        Transforms.generateHsvHueColors()
    }

    ColorSlider(
        value = currentHue / 360f,
        onValueChanged = onHueChanged,
        drawBrushColors = hsvHueColors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

/** HSV saturation slider. */
@Composable
fun HsvSaturationSlider(
    currentHue: Float,
    currentSaturation: Float,
    onSaturationChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val saturationColors = remember(currentHue) {
        listOf(
            Hsv(currentHue, 0f, 100f).toColor(1f),
            Hsv(currentHue, 100f, 100f).toColor(1f),
        )
    }
    ColorSlider(
        value = currentSaturation,
        onValueChanged = onSaturationChanged,
        drawBrushColors = saturationColors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

/** HSV value/brightness slider. */
@Composable
fun HsvValueSlider(
    currentHue: Float,
    currentSaturation: Float,
    currentValue: Float,
    onValueChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val valueColors = remember(currentHue, currentSaturation) {
        listOf(Color.Black, Hsv(currentHue, currentSaturation * 100f, 100f).toColor())
    }
    ColorSlider(
        value = currentValue,
        onValueChanged = onValueChanged,
        drawBrushColors = valueColors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

/** HSV alpha slider. */
@Composable
fun HsvAlphaSlider(
    currentHue: Float,
    currentSaturation: Float,
    currentValue: Float,
    currentAlpha: Float,
    onAlphaChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val alphaColors = remember(currentHue, currentSaturation, currentValue) {
        val baseColor = Hsv(currentHue, currentSaturation * 100f, currentValue * 100f).toColor()
        listOf(baseColor.copy(alpha = 0f), baseColor.copy(alpha = 1f))
    }

    ColorSlider(
        value = currentAlpha,
        onValueChanged = onAlphaChanged,
        drawBrushColors = alphaColors,
        modifier = Modifier
            .fillMaxWidth()
            .drawCheckerboard(),
        hapticEffect = hapticEffect,
    )
}

/**
 * OkHSV color picker (OkLab-based, better perceptual uniformity than HSV).
 */
@Composable
fun OkHsvColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val currentOnColorChanged by rememberUpdatedState(onColorChanged)
    val capsuleShape = ContinuousCapsule()

    val okhsv = remember { Transforms.colorToOkhsv(color) }
    var currentH by remember { mutableFloatStateOf(okhsv[0]) }
    var currentS by remember { mutableFloatStateOf(okhsv[1]) }
    var currentV by remember { mutableFloatStateOf(okhsv[2]) }
    var currentAlpha by remember { mutableFloatStateOf(color.alpha) }

    var lastAppliedExternalColorArgb by remember {
        mutableStateOf(color.toArgb())
    }
    val selectedColor by remember {
        derivedStateOf {
            OkHsv(
                h = currentH,
                s = currentS,
                v = currentV,
            ).toColor(currentAlpha)
        }
    }

    SideEffect {
        val externalArgb = color.toArgb()
        val internalArgb = selectedColor.toArgb()

        if (
            externalArgb != lastAppliedExternalColorArgb &&
            externalArgb != internalArgb
        ) {
            lastAppliedExternalColorArgb = externalArgb
            val okhsv = Transforms.colorToOkhsv(color)
            currentH = okhsv[0]
            currentS = okhsv[1]
            currentV = okhsv[2]
            currentAlpha = color.alpha
        }
    }

    fun notifyUserColorChanged() {
        val newColor = selectedColor
        if (newColor.toArgb() != color.toArgb()) {
            currentOnColorChanged(newColor)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(capsuleShape)
                    .background(selectedColor),
            )
        }

        OkHsvHueSlider(
            currentH = currentH,
            onHueChanged = {
                currentH = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        OkHsvSaturationSlider(
            currentH = currentH,
            currentS = currentS,
            onSaturationChanged = {
                currentS = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        OkHsvValueSlider(
            currentH = currentH,
            currentS = currentS,
            currentV = currentV,
            onValueChanged = {
                currentV = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        OkHsvAlphaSlider(
            currentH = currentH,
            currentS = currentS,
            currentV = currentV,
            currentAlpha = currentAlpha,
            onAlphaChanged = {
                currentAlpha = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )
    }
}

/** OkHSV hue slider (`currentH` is normalized 0-1). */
@Composable
fun OkHsvHueSlider(
    currentH: Float,
    onHueChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val okHsvHueColors = remember {
        Transforms.generateOkHsvHueColors()
    }

    ColorSlider(
        value = currentH,
        onValueChanged = onHueChanged,
        drawBrushColors = okHsvHueColors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

/** OkHSV saturation slider. */
@Composable
fun OkHsvSaturationSlider(
    currentH: Float,
    currentS: Float,
    onSaturationChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val saturationColors = remember(currentH) {
        listOf(
            Transforms.okhsvToColor(currentH, 0f, 1f, 1f),
            Transforms.okhsvToColor(currentH, 1f, 1f, 1f),
        )
    }

    ColorSlider(
        value = currentS,
        onValueChanged = onSaturationChanged,
        drawBrushColors = saturationColors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

/** OkHSV value slider. */
@Composable
fun OkHsvValueSlider(
    currentH: Float,
    currentS: Float,
    currentV: Float,
    onValueChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val valueColors = remember(currentH, currentS) {
        listOf(
            Transforms.okhsvToColor(currentH, currentS, 0f, 1f),
            Transforms.okhsvToColor(currentH, currentS, 1f, 1f),
        )
    }

    ColorSlider(
        value = currentV,
        onValueChanged = onValueChanged,
        drawBrushColors = valueColors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

/** OkHSV alpha slider. */
@Composable
fun OkHsvAlphaSlider(
    currentH: Float,
    currentS: Float,
    currentV: Float,
    currentAlpha: Float,
    onAlphaChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val alphaColors = remember(currentH, currentS, currentV) {
        val baseColor = Transforms.okhsvToColor(currentH, currentS, currentV)
        listOf(baseColor.copy(alpha = 0f), baseColor.copy(alpha = 1f))
    }

    ColorSlider(
        value = currentAlpha,
        onValueChanged = onAlphaChanged,
        drawBrushColors = alphaColors,
        modifier = Modifier
            .fillMaxWidth()
            .drawCheckerboard(),
        hapticEffect = hapticEffect,
    )
}

/** OkLab color picker. */
@Composable
fun OkLabColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val currentOnColorChanged by rememberUpdatedState(onColorChanged)
    val capsuleShape = ContinuousCapsule()

    val ok = remember { color.toOkLab() }
    var currentL by remember { mutableFloatStateOf(ok.l / 100f) }
    var currentA by remember { mutableFloatStateOf(((ok.a / 100f) * 0.4f)) }
    var currentB by remember { mutableFloatStateOf(((ok.b / 100f) * 0.4f)) }
    var currentAlpha by remember { mutableFloatStateOf(color.alpha) }

    var lastAppliedExternalColorArgb by remember {
        mutableStateOf(color.toArgb())
    }
    val selectedColor by remember {
        derivedStateOf {
            OkLab(
                l = currentL * 100f,
                a = (currentA / 0.4f) * 100f,
                b = (currentB / 0.4f) * 100f,
            ).toColor(currentAlpha)
        }
    }

    SideEffect {
        val externalArgb = color.toArgb()
        val internalArgb = selectedColor.toArgb()

        if (
            externalArgb != lastAppliedExternalColorArgb &&
            externalArgb != internalArgb
        ) {
            lastAppliedExternalColorArgb = externalArgb
            val ok = color.toOkLab()
            currentL = ok.l / 100f
            currentA = (ok.a / 100f) * 0.4f
            currentB = (ok.b / 100f) * 0.4f
            currentAlpha = color.alpha
        }
    }

    fun notifyUserColorChanged() {
        val newColor = selectedColor
        if (newColor.toArgb() != color.toArgb()) {
            currentOnColorChanged(newColor)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(capsuleShape)
                    .background(selectedColor),
            )
        }

        OkLabLightnessSlider(
            currentL = currentL,
            currentA = currentA,
            currentB = currentB,
            onLightnessChanged = {
                currentL = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        OkLabAChannelSlider(
            currentL = currentL,
            currentA = currentA,
            currentB = currentB,
            onAChanged = {
                currentA = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        OkLabBChannelSlider(
            currentL = currentL,
            currentA = currentA,
            currentB = currentB,
            onBChanged = {
                currentB = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        OkLabAlphaSlider(
            currentL = currentL,
            currentA = currentA,
            currentB = currentB,
            currentAlpha = currentAlpha,
            onAlphaChanged = {
                currentAlpha = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )
    }
}

/** OkLch color picker. */
@Composable
fun OkLchColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val currentOnColorChanged by rememberUpdatedState(onColorChanged)
    val capsuleShape = ContinuousCapsule()

    val oklch = remember { color.toOkLch() }
    var currentL by remember { mutableFloatStateOf(oklch.l / 100f) } // 0..1
    var currentC by remember { mutableFloatStateOf(oklch.c / 100f) } // proportion 0..1 (scaled to 0..0.4 internally)
    var currentH by remember { mutableFloatStateOf(oklch.h / 360f) } // normalized 0..1 (scaled to 360)
    var currentAlpha by remember { mutableFloatStateOf(color.alpha) }

    var lastAppliedExternalColorArgb by remember {
        mutableStateOf(color.toArgb())
    }
    val selectedColor by remember {
        derivedStateOf {
            OkLch(
                l = currentL * 100f,
                c = currentC * 100f,
                h = currentH * 360f,
            ).toColor(currentAlpha)
        }
    }

    SideEffect {
        val externalArgb = color.toArgb()
        val internalArgb = selectedColor.toArgb()

        if (
            externalArgb != lastAppliedExternalColorArgb &&
            externalArgb != internalArgb
        ) {
            lastAppliedExternalColorArgb = externalArgb
            val oklch = color.toOkLch()
            currentL = oklch.l / 100f
            currentC = oklch.c / 100f
            currentH = oklch.h / 360f
            currentAlpha = color.alpha
        }
    }

    fun notifyUserColorChanged() {
        val newColor = selectedColor
        if (newColor.toArgb() != color.toArgb()) {
            currentOnColorChanged(newColor)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(capsuleShape)
                    .background(selectedColor),
            )
        }

        OkLchHueSlider(
            currentL = currentL,
            currentC = currentC,
            currentH = currentH,
            onHueChanged = {
                currentH = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        OkLchLightnessSlider(
            currentL = currentL,
            currentC = currentC,
            currentH = currentH,
            onLightnessChanged = {
                currentL = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        OkLchChromaSlider(
            currentL = currentL,
            currentC = currentC,
            currentH = currentH,
            onChromaChanged = {
                currentC = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )

        OkLchAlphaSlider(
            currentL = currentL,
            currentC = currentC,
            currentH = currentH,
            currentAlpha = currentAlpha,
            onAlphaChanged = {
                currentAlpha = it
                notifyUserColorChanged()
            },
            hapticEffect = hapticEffect,
        )
    }
}

@Composable
fun OkLchLightnessSlider(
    currentL: Float,
    currentC: Float,
    currentH: Float,
    onLightnessChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val hDeg = currentH * 360f
    val cInternal = currentC * 0.4f
    val colors = remember(currentC, currentH) {
        listOf(
            Transforms.oklchToColor(0f, cInternal, hDeg, 1f),
            Transforms.oklchToColor(1f, cInternal, hDeg, 1f),
        )
    }

    ColorSlider(
        value = currentL,
        onValueChanged = onLightnessChanged,
        drawBrushColors = colors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

@Composable
fun OkLchChromaSlider(
    currentL: Float,
    currentC: Float,
    currentH: Float,
    onChromaChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val hDeg = currentH * 360f
    val colors = remember(currentL, currentH) {
        listOf(
            Transforms.oklchToColor(currentL, 0f, hDeg, 1f),
            Transforms.oklchToColor(currentL, 0.4f, hDeg, 1f),
        )
    }

    ColorSlider(
        value = currentC,
        onValueChanged = onChromaChanged,
        drawBrushColors = colors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

@Composable
fun OkLchHueSlider(
    currentL: Float,
    currentC: Float,
    currentH: Float,
    onHueChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val colors = remember(currentL, currentC) {
        Transforms.generateOkLchHueColors(currentL, currentC)
    }

    ColorSlider(
        value = currentH,
        onValueChanged = onHueChanged,
        drawBrushColors = colors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

@Composable
fun OkLchAlphaSlider(
    currentL: Float,
    currentC: Float,
    currentH: Float,
    currentAlpha: Float,
    onAlphaChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val hDeg = currentH * 360f
    val cInternal = currentC * 0.4f
    val colors = remember(currentL, currentC, currentH) {
        val opaque = Transforms.oklchToColor(currentL, cInternal, hDeg, 1f)
        val transparent = Color(opaque.red, opaque.green, opaque.blue, 0f)
        listOf(transparent, opaque)
    }

    ColorSlider(
        value = currentAlpha,
        onValueChanged = onAlphaChanged,
        drawBrushColors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .drawCheckerboard(),
        hapticEffect = hapticEffect,
    )
}

/** OkLab lightness (L) slider. */
@Composable
fun OkLabLightnessSlider(
    currentL: Float,
    currentA: Float,
    currentB: Float,
    onLightnessChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val lightnessColors = remember(currentA, currentB) {
        val steps = 7
        (0..steps).map { i ->
            val l = i.toFloat() / steps.toFloat()
            OkLab(
                l = l * 100f,
                a = (currentA / 0.4f) * 100f,
                b = (currentB / 0.4f) * 100f,
            ).toColor()
        }
    }
    ColorSlider(
        value = currentL,
        onValueChanged = onLightnessChanged,
        drawBrushColors = lightnessColors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

/** OkLab A-channel (green-red axis) slider. */
@Composable
fun OkLabAChannelSlider(
    currentL: Float,
    currentA: Float,
    currentB: Float,
    onAChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val aChannelColors = remember(currentL, currentB) {
        val minA = -0.3f
        val maxA = 0.3f
        val steps = 8
        (0..steps).map { i ->
            val a = minA + (maxA - minA) * i.toFloat() / steps.toFloat()
            OkLab(
                l = currentL * 100f,
                a = (a / 0.4f) * 100f,
                b = (currentB / 0.4f) * 100f,
            ).toColor()
        }
    }
    ColorSlider(
        value = (currentA + 0.3f) / 0.6f,
        onValueChanged = { normalizedValue ->
            onAChanged(normalizedValue * 0.6f - 0.3f)
        },
        drawBrushColors = aChannelColors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

/** OkLab B-channel (blue-yellow axis) slider. */
@Composable
fun OkLabBChannelSlider(
    currentL: Float,
    currentA: Float,
    currentB: Float,
    onBChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val bChannelColors = remember(currentL, currentA) {
        val minB = -0.3f
        val maxB = 0.3f
        val steps = 8
        (0..steps).map { i ->
            val b = minB + (maxB - minB) * i.toFloat() / steps.toFloat()
            OkLab(
                l = currentL * 100f,
                a = (currentA / 0.4f) * 100f,
                b = (b / 0.4f) * 100f,
            ).toColor()
        }
    }
    ColorSlider(
        value = (currentB + 0.3f) / 0.6f,
        onValueChanged = { normalizedValue ->
            onBChanged(normalizedValue * 0.6f - 0.3f)
        },
        drawBrushColors = bChannelColors,
        modifier = Modifier.fillMaxWidth(),
        hapticEffect = hapticEffect,
    )
}

/** OkLab alpha slider. */
@Composable
fun OkLabAlphaSlider(
    currentL: Float,
    currentA: Float,
    currentB: Float,
    currentAlpha: Float,
    onAlphaChanged: (Float) -> Unit,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val alphaColors = remember(currentL, currentA, currentB) {
        val baseColor = OkLab(
            l = currentL * 100f,
            a = (currentA / 0.4f) * 100f,
            b = (currentB / 0.4f) * 100f,
        ).toColor()
        listOf(baseColor.copy(alpha = 0f), baseColor.copy(alpha = 1f))
    }

    ColorSlider(
        value = currentAlpha,
        onValueChanged = onAlphaChanged,
        drawBrushColors = alphaColors,
        modifier = Modifier.fillMaxWidth()
            .drawCheckerboard(),
        hapticEffect = hapticEffect,
    )
}

fun Modifier.drawCheckerboard(
    cellSizeDp: Dp = 3.dp,
    lightColor: Color = Color(0xFFCCCCCC),
    darkColor: Color = Color(0xFFAAAAAA),
): Modifier = this.then(
    Modifier.drawWithCache {
        val cell = cellSizeDp.toPx().coerceAtLeast(1f)
        val rows = ceil(size.height / cell).toInt().coerceAtLeast(1)

        val darkPath = Path().apply {
            var y = 0f
            for (row in 0 until rows) {
                var x = if ((row and 1) == 0) cell else 0f
                while (x < size.width) {
                    addRect(
                        Rect(
                            x,
                            y,
                            min(x + cell, size.width),
                            min(y + cell, size.height),
                        ),
                    )
                    x += cell * 2f
                }
                y += cell
            }
        }

        onDrawBehind {
            drawRect(color = lightColor)
            drawPath(path = darkPath, color = darkColor)
        }
    },
)

/** Generic color-selection slider. */
@Composable
private fun ColorSlider(
    value: Float,
    onValueChanged: (Float) -> Unit,
    drawBrushColors: List<Color>,
    modifier: Modifier = Modifier,
    hapticEffect: SliderDefaults.SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
) {
    val onValueChangedState = rememberUpdatedState(onValueChanged)
    val capsuleShape = ContinuousCapsule()
    val density = LocalDensity.current
    val indicatorSizeDp = 20.dp
    val sliderHeightDp = 26.dp
    val sliderHeightPx = with(density) { sliderHeightDp.toPx() }
    val hapticFeedback = LocalHapticFeedback.current
    val hapticState = remember { SliderHapticState() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var sliderWidthPxState by remember { mutableFloatStateOf(0f) }
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    BoxWithConstraints(
        modifier = Modifier
            .clip(capsuleShape)
            .then(modifier)
            .height(sliderHeightDp)
            .drawWithCache {
                val widthPx = size.width
                val halfSliderHeightPx = sliderHeightDp.toPx() / 2f
                val gradientBrush = Brush.horizontalGradient(
                    colors = if (isRtl) drawBrushColors.reversed() else drawBrushColors,
                    startX = halfSliderHeightPx,
                    endX = widthPx - halfSliderHeightPx,
                    tileMode = TileMode.Clamp,
                )
                val borderStroke = Stroke(width = 0.5.dp.toPx())
                val borderColor = Color.Gray.copy(0.1f)

                onDrawBehind {
                    drawRect(brush = gradientBrush)
                    drawRect(
                        color = borderColor,
                        style = borderStroke,
                    )
                }
            }
            .onSizeChanged { sliderWidthPxState = it.width.toFloat() }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    val d = if (isRtl) -delta else delta
                    dragOffset += d
                    val newValue = handleSliderInteraction(
                        dragOffset,
                        sliderWidthPxState,
                        sliderHeightPx,
                    ).coerceIn(0f, 1f)
                    onValueChangedState.value(newValue)
                    hapticState.handleHapticFeedback(newValue, 0f..1f, hapticEffect, hapticFeedback)
                },
                onDragStarted = { offset ->
                    val x = if (isRtl) sliderWidthPxState - offset.x else offset.x
                    dragOffset = x
                    val newValue = handleSliderInteraction(x, sliderWidthPxState, sliderHeightPx)
                    onValueChangedState.value(newValue)
                    hapticState.reset(newValue)
                },
            ),
    ) {
        SliderIndicator(
            modifier = Modifier.align(Alignment.CenterStart),
            valueProvider = { value },
            sliderWidth = maxWidth,
            sliderSizePx = sliderHeightPx,
            indicatorSize = indicatorSizeDp,
        )
    }
}

@Composable
private fun SliderIndicator(
    valueProvider: () -> Float,
    sliderWidth: Dp,
    sliderSizePx: Float,
    indicatorSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .offset {
                val sliderWidthPx = sliderWidth.toPx()
                val effectiveWidthPx = sliderWidthPx - sliderSizePx
                val indicatorPositionPx = (valueProvider() * effectiveWidthPx) + (sliderSizePx / 2)
                val indicatorOffsetXDp = indicatorPositionPx.toDp() - (indicatorSize / 2)
                IntOffset(indicatorOffsetXDp.roundToPx(), 0)
            }
            .size(indicatorSize)
            .drawWithCache {
                val strokeWidth = 6.dp.toPx()
                val halfStroke = strokeWidth / 2f
                val glowSpread = 2.dp.toPx()
                val glowColor = Color.Black.copy(alpha = 0.25f)

                val ringCenterRadius = (size.minDimension / 2f) - halfStroke
                val gradientRadius = ringCenterRadius + halfStroke + glowSpread

                val glowBrush = Brush.radialGradient(
                    colorStops = listOf(
                        ((ringCenterRadius - halfStroke - glowSpread).coerceAtLeast(0f) / gradientRadius) to Color.Transparent,
                        ((ringCenterRadius - halfStroke) / gradientRadius) to glowColor,
                        ((ringCenterRadius + halfStroke) / gradientRadius) to glowColor,
                        ((ringCenterRadius + halfStroke + glowSpread) / gradientRadius) to Color.Transparent,
                    ).toTypedArray(),
                    radius = gradientRadius,
                )

                onDrawBehind {
                    drawCircle(
                        brush = glowBrush,
                        radius = gradientRadius,
                    )

                    drawCircle(
                        color = Color.White,
                        radius = ringCenterRadius,
                        style = Stroke(width = strokeWidth),
                    )
                }
            },
    )
}

/** Maps a drag position to a normalized 0-1 slider value. */
private fun handleSliderInteraction(
    positionX: Float,
    totalWidth: Float,
    sliderSizePx: Float,
): Float {
    val sliderHalfSizePx = sliderSizePx / 2
    val effectiveWidth = totalWidth - sliderSizePx
    val constrainedX = positionX.coerceIn(sliderHalfSizePx, totalWidth - sliderHalfSizePx)
    val newPosition = (constrainedX - sliderHalfSizePx) / effectiveWidth
    return newPosition.coerceIn(0f, 1f)
}

enum class ColorSpace {
    HSV,
    OKHSV,
    OKLAB,
    OKLCH,
}
