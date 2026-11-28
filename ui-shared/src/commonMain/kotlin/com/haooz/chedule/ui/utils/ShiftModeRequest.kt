package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 设置页写入排班切换请求，主界面消费后清空，并负责重排分页和显示过渡层。 */
object ShiftModeRequest {

    /** null = 无请求；true = 进入排班；false = 退出排班 */
    var pending: Boolean? by mutableStateOf(null)
        private set

    fun enter() {
        pending = true
    }

    fun exit() {
        pending = false
    }

    /** 主界面消费：取走并清空（避免 LaunchedEffect 重复触发同一请求）。 */
    fun consume(): Boolean? {
        val value = pending
        pending = null
        return value
    }
}
