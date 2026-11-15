/*
 * 缓动与时序常量。
 *
 * 类名沿用「Oobe」前缀只是历史遗留 —— OOBE 开屏引导早已下线，原本配套的一整簇
 * OOBE 动效（*Transform() 与OOBE_*_MILLIS）已随之删除。剩下的 Oobe*OutEasing
 * 是各页面 morph / 转场动画真正在用的，别按名字当成残留删掉。
 *
 * Animation timings are derived from HyperCeiler provision at commit
 * 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.haooz.chedule.ui.effects.motion

// ⚠ 全部 internal 已开放为 public：跨模块（:androidApp 的 MainActivity/AppNavHost 等）读不到
// internal 会编译报错（"Cannot access … it is internal in file"），勿改回 internal。

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

val OobeQuadraticOutEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}

val OobeCubicOutEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction) * (1f - fraction)
}

val OobeQuartOutEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - inverse.pow(4.5f)
}

/** [OobeQuartOutEasing] 的温和起步变体：Hermite 把起点速度压为 0，中后段保持原强减速 */
val OobeQuartOutSoftStartEasing = Easing { fraction ->
    val t = fraction.coerceIn(0f, 1f)
    val k = 0.48f
    val shaped = if (t <= k) {
        // shaped(0)=0, shaped'(0)=0, shaped(k)=k, shaped'(k)=1
        -t * t * t / (k * k) + 2f * t * t / k
    } else {
        t
    }
    val inverse = 1f - shaped
    1f - inverse.pow(4.5f)
}

/** 二级页「出场」缓动：起步更缓、后段正常滑出。 */
val SecondaryPageExitEasing = CubicBezierEasing(0.36f, 0.18f, 0.3f, 0.85f)

/**
 * 课表外观页（开洞）全套动效共用的缓动：入场/退场、缩放、盖层、弹窗让位上移全用它。
 */
val CustomizePageEasing = CubicBezierEasing(0.3f, 0.5f, 0.2f, 1.0f)

// ──────────────────────────────────────────────────────────────────────────
// 课表外观页「封面快照」时序：整屏截图当封面盖上去后，随主内容一起缩进洞里。
// 外观页元素入场动画必须整体收尾在 [CUSTOMIZE_COVER_WINDOW_MILLIS] 之内，
// 否则出现「封面都没了、按钮还在飞」的脱节感。
// ──────────────────────────────────────────────────────────────────────────

/**
 * 封面快照开始入洞缩放前的静置：只需盖住「状态变更 → 下一帧封面以完整 1f 上屏」。
 * 原值 280ms 是纯空等，实测（iPhone 18 Pro Max / iOS 27.0）把「首个可见变化」
 * 推迟到 1033ms，截图后的「顿」即来源于此。
 */
const val CUSTOMIZE_COVER_HOLD_MILLIS = 32

/** 封面快照入洞缩放时长（与主内容 `cutoutMainScale` 同长，保证两者同步）。 */
const val CUSTOMIZE_COVER_SCALE_MILLIS = 400

/** 封面快照淡出时长。 */
const val CUSTOMIZE_COVER_FADE_MILLIS = 120

/** 封面快照从「外观页开始组合」到「完全撤除」的总时长。见上方说明。 */
const val CUSTOMIZE_COVER_WINDOW_MILLIS =
    CUSTOMIZE_COVER_HOLD_MILLIS + CUSTOMIZE_COVER_SCALE_MILLIS + CUSTOMIZE_COVER_FADE_MILLIS

val OobeFifthpowerOutEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - inverse * inverse * inverse * inverse * inverse
}

/** 引导完成进入主界面的入场时长（毫秒）：参考为 619，这里略放长一点 */
const val OOBE_HOME_ENTER_MILLIS = 700

/** 引导完成时主界面淡入的时长与延迟（毫秒）。 */
const val OOBE_HOME_FADE_MILLIS = 230
const val OOBE_HOME_FADE_DELAY_MILLIS = 60

val OobeSinOutEasing = Easing { fraction -> sin(fraction * PI.toFloat() / 2f) }

/**
 * 进入主界面的弹簧缓动（照抄参考 `OobeHomeSpringEasing`）：阻尼 0.65、角频率 10.5 的
 * 欠阻尼阶跃响应，1.3 → 1 时过冲一次再收敛；曲线在 1f 处强制收敛。
 */
val OobeHomeSpringEasing = Easing { fraction ->
    if (fraction <= 0f) return@Easing 0f
    if (fraction >= 1f) return@Easing 1f
    val dampingRatio = 0.65f
    val angularFrequency = 10.5f
    val dampedFrequency = angularFrequency * sqrt(1f - dampingRatio * dampingRatio)
    val envelope = exp((-dampingRatio * angularFrequency * fraction).toDouble()).toFloat()
    1f - envelope * (
        cos(dampedFrequency * fraction) +
            dampingRatio / sqrt(1f - dampingRatio * dampingRatio) * sin(dampedFrequency * fraction)
        )
}
