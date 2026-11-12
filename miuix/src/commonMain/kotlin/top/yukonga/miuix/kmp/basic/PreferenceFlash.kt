// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 搜索定位闪烁的目标：标题 + 序号。 */
data class PreferenceFlashTarget(val title: String, val token: Int)

/**
 * 搜索跳转定位：跳转后目标页里标题匹配的 preference 连按两次（压暗 → 松开 → 压暗）。
 *
 * 设置搜索项散落在各页，逐行接线成本高；这里由 [BasicComponent] 统一消费，
 * 宿主只需在跳转时 [trigger] 一次目标标题。
 */
object PreferenceFlash {
    var target by mutableStateOf<PreferenceFlashTarget?>(null)
        private set

    private var seq = 0

    fun trigger(title: String) {
        seq += 1
        target = PreferenceFlashTarget(title, seq)
    }

    internal fun clear(token: Int) {
        if (target?.token == token) target = null
    }
}
