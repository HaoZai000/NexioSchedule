package com.haooz.chedule.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.haooz.chedule.data.appearance.CardTextColor
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.util.hsvToRgb
import com.haooz.chedule.data.util.rgbToHsv
import com.materialkolor.hct.Hct
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
internal data class CourseColors(val tint: Color, val onTint: Color)

/** 着色程度较高时自动选用清晰的文字颜色，避免色板的深色文字落在深色卡片上。 */
internal fun readableCourseTextColor(preferred: Color, background: Color): Color {
    val backgroundLuminance = background.luminance()
    val foregroundLuminance = preferred.luminance()
    val contrast = (maxOf(backgroundLuminance, foregroundLuminance) + 0.05f) /
        (minOf(backgroundLuminance, foregroundLuminance) + 0.05f)
    if (contrast >= 4.5f) return preferred
    val blackContrast = (backgroundLuminance + 0.05f) / 0.05f
    val whiteContrast = 1.05f / (backgroundLuminance + 0.05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

/** 同名课程稳定映射到系统的主、次、第三色系；只影响显示，不改写课程自定义颜色。 */
@Composable
internal fun rememberCourseColors(course: Course, isDark: Boolean): CourseColors {
    if (!MiuixTheme.isDynamicColor) {
        return remember(course.colorRes) {
            val color = Color(course.colorRes)
            CourseColors(color, color)
        }
    }
    val colors = MiuixTheme.colorScheme
    val (seed, foreground) = when (((course.name.hashCode() % 3) + 3) % 3) {
        0 -> colors.primary to colors.onPrimaryContainer
        1 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    return remember(seed, foreground, isDark) {
        // 课程卡使用半透明着色层，取强调色调而非很淡的 container，保证着色程度可见。
        val hct = Hct.fromInt(seed.toArgb())
        val tint = Color(Hct.from(hct.hue, hct.chroma, if (isDark) 80.0 else 40.0).toInt())
        CourseColors(tint, foreground)
    }
}

@Composable
internal fun scheduleAccentColor(): Color =
    if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else Color(0xFF3482FF)

/**
 * 课卡文字色。
 *
 * 网格卡与时间轴卡共用这一份，否则同一门课在两个视图里颜色不一致。
 *
 * @param background 卡片最终表面色（已 compositeOver 过垫底色），莫奈档的对比度兜底要用它
 */
internal fun courseCardTextColor(
    courseColors: CourseColors,
    useMonet: Boolean,
    inactiveColor: Color,
    background: Color,
    isCurrentWeek: Boolean,
    isHoliday: Boolean,
    isCourseCancelled: Boolean,
    hasBlur: Boolean,
    isDark: Boolean,
    cardTextColor: CardTextColor,
): Color {
    if (!(isCurrentWeek && !isHoliday && !isCourseCancelled)) {
        return if (hasBlur) {
            if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)
        } else {
            inactiveColor.copy(alpha = if (isDark) 0.28f else 0.45f)
        }
    }
    if (cardTextColor == CardTextColor.SOLID) {
        return if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.74f)
    }
    if (useMonet) return readableCourseTextColor(courseColors.onTint, background)
    if (!hasBlur) return courseColors.tint
    // 玻璃卡：课程色提亮、降饱和，才有对比度
    val hsv = FloatArray(3)
    val c = courseColors.tint
    rgbToHsv(
        (c.red * 255).toInt(),
        (c.green * 255).toInt(),
        (c.blue * 255).toInt(),
        hsv,
    )
    if (isDark) {
        hsv[1] = (hsv[1] * 0.5f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] + 0.4f).coerceIn(0f, 1f)
    } else {
        hsv[1] = (hsv[1] * 0.84f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] + 0.5f).coerceIn(0f, 1f)
    }
    return Color(hsvToRgb(hsv))
}
