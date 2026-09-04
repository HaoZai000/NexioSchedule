package com.haooz.chedule.ui.data

data class AppreciationItem(
    val nickname: String,
    val amount: String,
    val time: String,
    val remark: String = "",
)

val sampleAppreciations = listOf(
    AppreciationItem("iSkism", "¥8.88", "2026-09-04", "没有广告，作者反馈也很积极，必须支持一下"),
    AppreciationItem("QAQ", "¥6.66", "2026-09-03", "大佬nb，更新很勤 ୧⍢⃝୨"),
    AppreciationItem("Romantic", "¥6.66", "2026-09-02", ""),
    AppreciationItem("Unique", "¥6.66", "2026-08-31", ""),
    AppreciationItem("澪洛依", "¥10.00", "2026-08-31", "一千万以内最好的课程表软件"),
    AppreciationItem("一個小果冻", "¥8.88", "2026-08-31", "很好看的课程表"),
    AppreciationItem("千凪Sennagi", "¥0.88", "2026-08-31", "支持"),
    AppreciationItem("MoeLight", "¥8.88", "2026-08-31", ""),
    AppreciationItem("ᴅᴀᴡɴ", "¥6.66", "2026-08-30", "超棒的课程表，祝做大做强"),
    AppreciationItem("[匿名]", "¥14.00", "2026-08-30", "麻烦看下issue23感谢\uD83D\uDE4F把这条捐赠放在11.45后面"),
    AppreciationItem("Unique", "¥2.88", "2026-08-22", ""),
    AppreciationItem("翎.", "¥6.66", "2026-08-22", "开发者加油！希望未来可以接入国内更多的高校教务！"),
    AppreciationItem("[匿名]", "¥6.66", "2026-08-20", "做得真的很好看"),
    AppreciationItem("Hian", "¥0.10", "2026-08-17", "加油"),
    AppreciationItem("[匿名]", "¥8.88", "2026-08-06", "我喜欢你 的课程表（）"),
    AppreciationItem("", "¥2.33", "2026-08-01", "2333333"),
    AppreciationItem("[匿名]", "¥11.45", "2026-07-27", "梦中情（课程）表\uD83E\uDD79感谢开发，加油！(ง๑ •̀_•́)ง"),
    AppreciationItem("mendacious", "¥0.01", "2026-07-21", "给我留名，给我欧气"),
    AppreciationItem("QAQ", "¥2.22", "2026-07-21", "有一个人前来支持վ'ᴗ' ի"),
    AppreciationItem("停留那片海", "¥6.66", "2026-07-21", "爱播，我要做你的死侍"),
    AppreciationItem("停留那片海", "¥2.88", "2026-07-16", "博主你一定要坚持下去啊"),
    AppreciationItem("333", "¥8.88", "2026-07-08"),
)
