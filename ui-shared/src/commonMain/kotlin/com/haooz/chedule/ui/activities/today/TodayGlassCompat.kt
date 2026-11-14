package com.haooz.chedule.ui.activities.today

import com.haooz.chedule.data.appearance.CardRefractionLevel

/**
 * 上游 1.7.0 把 [CardRefractionLevel] 改成了 softglass 的「折射厚度倍率」(scale)，
 * 用户首页时间轴仍走 :backdrop 的透镜效果，需要毫米级参数；这里按用户 fork 的原值补回。
 * EXTREME 是上游新增档位，取比 STRONG 略强的一档。
 */
internal val CardRefractionLevel.lensRadiusDp: Float
    get() = when (this) {
        CardRefractionLevel.OFF -> 0f
        CardRefractionLevel.WEAK -> 4f
        CardRefractionLevel.DEFAULT -> 5f
        CardRefractionLevel.STRONG -> 6f
        CardRefractionLevel.EXTREME -> 7f
    }

internal val CardRefractionLevel.lensStrengthDp: Float
    get() = when (this) {
        CardRefractionLevel.OFF -> 0f
        CardRefractionLevel.WEAK -> 16f
        CardRefractionLevel.DEFAULT -> 25f
        CardRefractionLevel.STRONG -> 36f
        CardRefractionLevel.EXTREME -> 50f
    }
