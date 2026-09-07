package com.haooz.chedule.ui.data

/**
 * 捐赠明细项。
 * 数据由云端后端（/api/appreciations）动态下发，App 端不再本地硬编码捐赠样本，
 * 见 com.haooz.chedule.data.AppreciationFetcher。
 */
data class AppreciationItem(
    val nickname: String,
    val amount: String,
    val time: String,
    val remark: String = "",
)
