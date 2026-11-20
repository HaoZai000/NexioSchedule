package com.haooz.chedule.ui.ai

/**
 * 网址入口页 → AI 教务导入网页 的地址传递（路由只有稳定 id，没有参数通道）。
 *
 * 入口页确定地址后 set，网页页 remember 时读并立即 clear；
 * 下次从入口进来不会拿到上一轮的地址。
 */
internal object AiEduUrlHolder {

    var url: String? = null
        private set

    fun set(url: String) {
        this.url = url
    }

    fun take(): String? {
        val value = url
        url = null
        return value
    }

    fun clear() {
        url = null
    }
}
