package com.kyant.backdrop

import kotlin.concurrent.Volatile

/**
 * 玻璃链路的性能 / 画质取舍开关。启动时设一次即可，绘制期只读。
 */
object BackdropTuning {

    /**
     * 把颜色算子（`vibrancy()` / `colorControls()` / `opacity()`）合进折射着色器，省掉一整段离屏 pass
     */
    @Volatile
    var mergeColorIntoRefraction: Boolean = true
}
