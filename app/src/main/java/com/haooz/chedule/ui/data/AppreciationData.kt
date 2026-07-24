package com.haooz.chedule.ui.data

data class AppreciationItem(
    val nickname: String,
    val amount: String,
    val time: String,
    val remark: String = "",
)

val sampleAppreciations = listOf(
    AppreciationItem("mendacious", "¥0.01", "2026-07-21", "给我留名，给我欧气"),
    AppreciationItem("QAQ", "¥2.22", "2026-07-21", "有一个人前来支持վ'ᴗ' ի"),
    AppreciationItem("停留那片海", "¥6.66", "2026-07-21", "爱播，我要做你的死侍"),
    AppreciationItem("停留那片海", "¥2.88", "2026-07-16", "博主你一定要坚持下去啊"),
    AppreciationItem("333", "¥8.88", "2026-07-08"),
    AppreciationItem("mendacious", "¥0.88", "2026-07-03"),
)
