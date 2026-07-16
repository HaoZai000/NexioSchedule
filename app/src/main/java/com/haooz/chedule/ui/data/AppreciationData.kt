package com.haooz.chedule.ui.data

data class AppreciationItem(
    val nickname: String,
    val amount: String,
    val time: String,
)

val sampleAppreciations = listOf(
    AppreciationItem("停留那片海", "¥2.88", "2026-07-16"),
    AppreciationItem("333", "¥8.88", "2026-07-08"),
    AppreciationItem("mendacious", "¥0.88", "2026-07-03"),
)
